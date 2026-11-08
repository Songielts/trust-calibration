package com.trustcal.service;

import com.trustcal.config.ApiException;
import com.trustcal.config.DataSeeder;
import com.trustcal.dto.AdminDtos.AnalyticsReport;
import com.trustcal.dto.AdminDtos.ConditionMetrics;
import com.trustcal.dto.ParticipantDtos.*;
import com.trustcal.entity.DecisionTask;
import com.trustcal.entity.ExperimentCondition;
import com.trustcal.entity.Participant;
import com.trustcal.llm.LlmAdviceGenerator;
import com.trustcal.llm.LlmException;
import com.trustcal.llm.LlmSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests of the experiment logic and the trust-calibration measures,
 * run against in-memory repositories (no database or Spring context needed).
 */
class ExperimentFlowTest {

    private static final double EPS = 1e-9;

    private InMemoryRepositories db;
    private ExperimentService experiment;
    private AnalyticsService analytics;
    private SimulationService simulation;

    /** Decides a participant's behaviour on one question: {initialAnswer, finalAnswer, trustRating}. */
    interface Strategy {
        int[] decide(DecisionTask task, int aiAnswer);
    }

    @BeforeEach
    void setUp() throws Exception {
        db = new InMemoryRepositories();
        new DataSeeder(db.domains, db.tasks).run();
        LlmSettings llm = new LlmSettings("http://unused", "test-model", "", 0.7);
        LlmAdviceGenerator generator = new LlmAdviceGenerator((m, t, j) -> {
            throw new LlmException("not used in these tests");
        }, llm, db.advices);
        AdviceProvider provider = new AdviceProvider(generator, db.advices, db.runs);
        experiment = new ExperimentService(db.participants, db.tasks, db.domains, db.trials, db.surveys,
                provider, new StudySettingsService(db.settings), llm);
        analytics = new AnalyticsService(db.participants, db.trials, db.surveys, db.tasks, db.domains);
        simulation = new SimulationService(db.participants, db.tasks, db.domains, db.trials, db.surveys, experiment);
    }

    // ------------------------------------------------------------------ helpers

    private SessionResponse register(String demoCondition) {
        return experiment.register(new DemographicsRequest("25-34", null, "Master's degree", 4, "Weekly", 5,
                true, demoCondition));
    }

    private DecisionTask task(Long id) {
        return db.tasks.findById(id).orElseThrow();
    }

    /** Runs all trials and the questionnaire for one participant. */
    private DebriefView complete(SessionResponse s, Strategy strategy, int estimatedOverall) {
        for (int i = 0; i < s.totalTrials(); i++) {
            TrialView before = experiment.getTrial(s.participantId(), i);
            DecisionTask t = task(before.taskId());
            int[] d = strategy.decide(t, t.getAiAnswerIndex());
            experiment.submitInitial(s.participantId(), i, new InitialAnswerRequest(d[0], 60, 1500L));
            experiment.submitFinal(s.participantId(), i, new FinalAnswerRequest(d[1], 70, d[2], 2500L));
        }
        Map<String, Integer> estimates = experiment.domainOptions().stream()
                .collect(Collectors.toMap(DomainOption::code, d -> 70));
        boolean capability = experiment.findParticipant(s.participantId()).getCondition().isShowCapability();
        return experiment.submitSurvey(s.participantId(), new SurveyRequest(5, 5, 5, 5, 3, estimatedOverall,
                estimates, 5, capability ? "A" : null, "test"));
    }

    // ------------------------------------------------------------------ tests

    @Test
    void seededTasksMatchTheExperimentalDesign() {
        List<DecisionTask> tasks = db.tasks.findAll();
        assertEquals(4L, db.domains.count());
        assertEquals(16, tasks.size());
        assertEquals(12L, tasks.stream().filter(DecisionTask::isAiCorrect).count(), "agent is correct on 12/16");

        Map<String, Double> perDomain = tasks.stream().collect(Collectors.groupingBy(DecisionTask::getDomainCode,
                Collectors.averagingDouble(t -> t.isAiCorrect() ? 1 : 0)));
        assertEquals(1.0, perDomain.get("SHOP"), EPS);
        assertEquals(0.75, perDomain.get("SCIENCE"), EPS);
        assertEquals(0.5, perDomain.get("HEALTH"), EPS);
        assertEquals(0.75, perDomain.get("LOGIC"), EPS);

        for (DecisionTask t : tasks) {
            assertTrue(t.getCorrectIndex() < t.getOptions().size(), t.getCode());
            assertTrue(t.getAiAnswerIndex() < t.getOptions().size(), t.getCode());
            assertFalse(t.getReasoningSteps().isEmpty(), t.getCode());
            assertNotNull(t.getExplanation(), t.getCode());
        }
    }

    @Test
    void realParticipantsAreBalancedAcrossConditionsAndDemoSessionsDoNotCount() {
        register("CAPABILITY");
        register("CAPABILITY");
        for (int i = 0; i < 8; i++) register(null);

        Map<ExperimentCondition, Long> real = db.participants.findAll().stream().map(o -> (Participant) o)
                .filter(Participant::isStudyData)
                .collect(Collectors.groupingBy(Participant::getCondition, Collectors.counting()));
        assertEquals(4, real.size());
        real.values().forEach(n -> assertEquals(2L, n));
    }

    @Test
    void adviceIsHiddenUntilTheInitialAnswerAndCuesFollowTheCondition() {
        for (ExperimentCondition c : ExperimentCondition.values()) {
            SessionResponse s = register(c.name());
            assertTrue(s.demo());
            assertEquals(c.getLabel(), s.conditionLabel());

            TrialView before = experiment.getTrial(s.participantId(), 0);
            assertEquals("INITIAL", before.stage());
            assertNull(before.advice(), "advice must not be sent before the participant commits");

            TrialView after = experiment.submitInitial(s.participantId(), 0, new InitialAnswerRequest(0, 50, 1000L));
            assertEquals("FINAL", after.stage());
            assertEquals(c.isShowCapability(), after.advice().capability() != null, c.name());
            assertEquals(c.isShowReasoning(), after.advice().reasoningSteps() != null, c.name());
        }
    }

    @Test
    void realSessionsDoNotRevealTheirCondition() {
        SessionResponse s = register(null);
        assertFalse(s.demo());
        assertNull(s.conditionLabel());
    }

    @Test
    void questionsMustBeAnsweredInOrderAndOnlyOnce() {
        SessionResponse s = register(null);
        assertThrows(ApiException.class, () -> experiment.getTrial(s.participantId(), 3));
        assertThrows(ApiException.class,
                () -> experiment.submitFinal(s.participantId(), 0, new FinalAnswerRequest(0, 50, 4, 1L)),
                "final answer before initial answer");

        experiment.submitInitial(s.participantId(), 0, new InitialAnswerRequest(1, 50, 1L));
        TrialView again = experiment.submitInitial(s.participantId(), 0, new InitialAnswerRequest(0, 90, 1L));
        assertEquals(1, again.initialAnswer(), "initial answer is locked once given");

        experiment.submitFinal(s.participantId(), 0, new FinalAnswerRequest(0, 50, 4, 1L));
        assertThrows(ApiException.class,
                () -> experiment.submitFinal(s.participantId(), 0, new FinalAnswerRequest(0, 50, 4, 1L)));
        assertThrows(ApiException.class, () -> experiment.submitInitial(s.participantId(), 1,
                new InitialAnswerRequest(9, 50, 1L)), "invalid option index");
    }

    @Test
    void alwaysFollowingTheAgentGivesFullOverRelianceAndNoUnderReliance() {
        for (int i = 0; i < 4; i++) {
            DebriefView d = complete(register(null), (t, ai) -> new int[]{ai == 0 ? 1 : 0, ai, 5}, 75);
            assertEquals(16, d.total());
            assertEquals(12, d.finalCorrect());
            assertEquals(4, d.followedAiWhenWrong());
            assertEquals(4, d.aiWrong());
        }
        ConditionMetrics all = analytics.report(false).overall();
        assertEquals(64, all.trials());
        assertEquals(1.0, all.overReliance(), EPS);
        assertEquals(0.0, all.underReliance(), EPS);
        assertEquals(1.0, all.agreementRate(), EPS);
        assertEquals(0.75, all.appropriateReliance(), EPS);
        assertEquals(0.0, all.overallCalibrationError(), EPS, "estimate 75 equals actual 75%");
        assertEquals(0.0, all.trustDiscrimination(), EPS);
        assertNull(all.trustCorrelation(), "correlation is undefined when trust is constant");
        assertEquals(5.0, all.trustScale(), EPS, "wary item (3) is reverse-scored to 5");
    }

    @Test
    void anIdeallyCalibratedParticipantGetsPerfectRelianceScores() {
        // Knows every answer, keeps it, and trusts the agent only when it is right
        complete(register(null), (t, ai) -> new int[]{t.getCorrectIndex(), t.getCorrectIndex(), t.isAiCorrect() ? 6 : 2}, 75);
        ConditionMetrics all = analytics.report(false).overall();
        assertEquals(1.0, all.finalAccuracy(), EPS);
        assertEquals(0.0, all.overReliance(), EPS);
        assertEquals(0.0, all.underReliance(), EPS);
        assertEquals(1.0, all.rsr(), EPS);
        assertEquals(1.0, all.appropriateReliance(), EPS);
        assertEquals(4.0, all.trustDiscrimination(), EPS);
        assertEquals(1.0, all.trustCorrelation(), EPS);
        assertNull(all.rair(), "RAIR undefined: initial answer was never wrong");
    }

    @Test
    void perTopicCalibrationErrorUsesTheAgentsRealAccuracyPerTopic() {
        complete(register(null), (t, ai) -> new int[]{ai, ai, 4}, 75);
        // Estimates of 70 for every topic: |70-100| + |70-75| + |70-50| + |70-75| = 60, mean 15
        assertEquals(15.0, analytics.report(false).overall().domainCalibrationError(), EPS);
    }

    @Test
    void demoSessionsAreExcludedFromTheAnalysisByDefault() {
        complete(register("CAPABILITY"), (t, ai) -> new int[]{ai, ai, 5}, 75);
        complete(register(null), (t, ai) -> new int[]{ai, ai, 5}, 75);

        AnalyticsReport studyOnly = analytics.report(false);
        assertEquals(1, studyOnly.completedParticipants());
        assertEquals(1, studyOnly.demoParticipants());
        assertEquals(2, analytics.report(true).completedParticipants());
        assertEquals(1, analytics.participantRows(false).size());

        assertEquals(1, simulation.deleteDemo());
        assertEquals(1, analytics.report(true).completedParticipants());
    }

    @Test
    void demoLinksAreRejectedWhenDisabledOrUnknown() {
        assertThrows(ApiException.class, () -> register("NOT_A_CONDITION"));
        experiment.setDemoModeEnabled(false);
        assertThrows(ApiException.class, () -> register("CAPABILITY"));
        assertDoesNotThrow(() -> register(null));
    }

    @Test
    void simulatedDataIsFlaggedExportedConsistentlyAndDeletable() {
        complete(register(null), (t, ai) -> new int[]{ai, ai, 5}, 75);
        simulation.simulate(40);

        assertEquals(41, analytics.report(true).completedParticipants());
        assertEquals(1, analytics.report(false).completedParticipants());

        String trials = analytics.trialsCsv(true);
        assertEquals(1 + 41 * 16, trials.split("\n").length);

        String[] lines = analytics.participantsCsv(true).split("\n");
        int columns = lines[0].split(",").length;
        for (String line : Arrays.copyOfRange(lines, 1, lines.length)) {
            assertEquals(columns, line.split(",", -1).length, line);
        }

        assertEquals(40, simulation.deleteSimulated());
        assertEquals(1, analytics.report(true).completedParticipants());
    }
}
