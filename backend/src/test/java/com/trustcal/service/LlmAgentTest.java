package com.trustcal.service;

import com.trustcal.config.ApiException;
import com.trustcal.config.DataSeeder;
import com.trustcal.dto.AdminDtos.*;
import com.trustcal.dto.ParticipantDtos.*;
import com.trustcal.entity.*;
import com.trustcal.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the LLM agent pipeline with a fake model (no API key or network needed):
 * output validation, the evaluation loop and its scoring, freezing, mode switching,
 * and how LLM advice is served to participants, including fallbacks.
 */
class LlmAgentTest {

    private InMemoryRepositories db;
    private FakeModel model;
    private LlmSettings settings;
    private StudySettingsService studySettings;
    private LlmAgentService agent;
    private ExperimentService experiment;

    /**
     * Fake model. By default it answers every task correctly; individual tasks can be given a
     * fixed sequence of replies (raw strings, or null to simulate a failed call).
     */
    static class FakeModel implements ChatClient {
        final Map<String, List<String>> scripted = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> callCount = new ConcurrentHashMap<>();
        final Map<String, DecisionTask> tasksByPrompt = new ConcurrentHashMap<>();
        volatile boolean failAll = false;

        @Override
        public String complete(List<Message> messages, double temperature, boolean jsonMode) throws LlmException {
            assertTrue(jsonMode, "the call must request JSON output");
            if (failAll) throw new LlmException("simulated outage");
            String userMessage = messages.get(messages.size() - 1).content();
            DecisionTask task = tasksByPrompt.values().stream()
                    .filter(t -> userMessage.contains(t.getPrompt())).findFirst()
                    .orElseThrow(() -> new AssertionError("unknown task in prompt"));
            int n = callCount.computeIfAbsent(task.getCode(), k -> new AtomicInteger()).getAndIncrement();
            List<String> replies = scripted.get(task.getCode());
            if (replies != null) {
                String reply = replies.get(n % replies.size());
                if (reply == null) throw new LlmException("simulated failure");
                return reply;
            }
            return reply(task.getCorrectIndex());
        }
    }

    static String reply(int index) {
        return "{\"answer\": \"" + PromptSpec.LETTERS.charAt(index) + "\", "
                + "\"reasoning_steps\": [\"First step.\", \"Second step.\"], \"confidence\": 80}";
    }

    @BeforeEach
    void setUp() throws Exception {
        db = new InMemoryRepositories();
        new DataSeeder(db.domains, db.tasks).run();
        model = new FakeModel();
        for (DecisionTask t : db.tasks.findAll()) model.tasksByPrompt.put(t.getCode(), t);
        settings = new LlmSettings("http://fake", "fake-model", "test-key", 0.7);
        studySettings = new StudySettingsService(db.settings);
        LlmAdviceGenerator generator = new LlmAdviceGenerator(model, settings, db.advices);
        agent = new LlmAgentService(generator, settings, db.tasks, db.domains, db.runs, db.advices, studySettings);
        AdviceProvider provider = new AdviceProvider(generator, db.advices, db.runs);
        experiment = new ExperimentService(db.participants, db.tasks, db.domains, db.trials, db.surveys,
                provider, studySettings, settings);
    }

    private DecisionTask task(String code) {
        return db.tasks.findAll().stream().filter(t -> t.getCode().equals(code)).findFirst().orElseThrow();
    }

    /** HEALTH-1: always wrong (A instead of B). SCI-3: wrong once, then right. LOGIC-2: never valid. */
    private void scriptMixedBehaviour() {
        model.scripted.put("HEALTH-1", List.of(reply(0)));
        model.scripted.put("SCI-3", List.of(reply(0), reply(2), reply(2)));
        model.scripted.put("LOGIC-2", Arrays.asList("Sure! The answer is A.", null, "{\"answer\": \"Z\"}"));
    }

    // ------------------------------------------------------------------ output validation

    @Test
    void parserAcceptsReplyThatMeetsEverySuccessCriterion() throws Exception {
        AdviceParser.ParsedAdvice p = AdviceParser.parse(reply(1), 3);
        assertEquals(1, p.answerIndex());
        assertEquals(2, p.reasoningSteps().size());
        assertEquals(80, p.confidence());

        String fenced = "```json\n" + reply(2) + "\n```";
        assertEquals(2, AdviceParser.parse(fenced, 3).answerIndex(), "code fences are tolerated");
        String lower = reply(0).replace("\"A\"", "\"a.\"");
        assertEquals(0, AdviceParser.parse(lower, 3).answerIndex(), "'a.' is read as option A");
    }

    @Test
    void parserRejectsEveryViolationOfTheSpecification() {
        String longStep = String.join(" ", Collections.nCopies(51, "word"));
        List<String> bad = List.of(
                "",
                "The answer is B",
                "[1, 2]",
                "{\"answer\": \"D\", \"reasoning_steps\": [\"a\", \"b\"], \"confidence\": 50}",
                "{\"answer\": 1, \"reasoning_steps\": [\"a\", \"b\"], \"confidence\": 50}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"only one\"], \"confidence\": 50}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"], \"confidence\": 50}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"ok\", \"" + longStep + "\"], \"confidence\": 50}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"ok\", \" \"], \"confidence\": 50}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"a\", \"b\"], \"confidence\": 150}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"a\", \"b\"], \"confidence\": 80.5}",
                "{\"answer\": \"A\", \"reasoning_steps\": [\"a\", \"b\"]}");
        for (String raw : bad) {
            assertThrows(AdviceParser.InvalidOutputException.class, () -> AdviceParser.parse(raw, 3), raw);
        }
    }

    @Test
    void parserRejectsStepsThatSayAsAnAi() throws Exception {
        String bad = "{\"answer\": \"A\", \"reasoning_steps\": [\"As an AI, I cannot taste food.\", \"Option A fits best.\"], \"confidence\": 60}";
        assertThrows(AdviceParser.InvalidOutputException.class, () -> AdviceParser.parse(bad, 3));

        // 边界情况：普通句子里碰巧包含这几个字母，不应该被误拒
        String ok = "{\"answer\": \"A\", \"reasoning_steps\": [\"The room has an air conditioner.\", \"Option A fits best.\"], \"confidence\": 60}";
        assertEquals(0, AdviceParser.parse(ok, 3).answerIndex());
    }

    @Test
    void promptFollowsTheSpecificationAndNeverContainsTheParticipantsAnswer() {
        DecisionTask t = task("SHOP-1");
        List<ChatClient.Message> messages = PromptSpec.messagesFor(t, "Shopping & Pricing");
        assertEquals("system", messages.get(0).role());
        for (String part : List.of("TASK:", "AUDIENCE:", "CONSTRAINTS:", "OUTPUT FORMAT:")) {
            assertTrue(messages.get(0).content().contains(part), part);
        }
        assertTrue(messages.get(0).content().toLowerCase().contains("json"), "JSON mode requires the word json");
        assertEquals("assistant", messages.get(2).role(), "one worked example");
        String last = messages.get(messages.size() - 1).content();
        assertTrue(last.contains(t.getPrompt()));
        assertTrue(last.contains("A. Pack A") && last.contains("B. Pack B"));
        assertEquals(4, messages.size(), "instruction, example question, example answer, task - nothing else");
    }

    // ------------------------------------------------------------------ evaluation loop

    @Test
    void evaluationRunScoresEveryCallAgainstTheAnswerKey() {
        scriptMixedBehaviour();
        RunSummary run = agent.runNow(3);

        assertEquals("COMPLETED", run.status());
        assertEquals(48, run.totalCalls());
        assertEquals(45, run.validCalls(), "LOGIC-2 never produced a valid reply");
        // wrong: HEALTH-1 x3, SCI-3 x1, LOGIC-2 x3 (invalid counts as a failure)
        assertEquals(41, run.correctCalls());
        assertEquals(100, run.domainAccuracy().get("SHOP"));
        assertEquals(92, run.domainAccuracy().get("SCIENCE"));  // 11/12
        assertEquals(75, run.domainAccuracy().get("HEALTH"));   // 9/12
        assertEquals(75, run.domainAccuracy().get("LOGIC"));    // 9/12

        RunDetail detail = agent.runDetail(run.id());
        TaskResult logic2 = detail.tasks().stream().filter(t -> t.taskCode().equals("LOGIC-2")).findFirst().orElseThrow();
        assertEquals(3, logic2.answerCounts().get("invalid"));
        assertTrue(logic2.callRows().stream().allMatch(c -> c.validationError() != null));
    }

    @Test
    void evaluationNeedsAnApiKeyAndSaneRepetitions() {
        LlmSettings noKey = new LlmSettings("http://fake", "fake-model", "", 0.7);
        LlmAgentService withoutKey = new LlmAgentService(new LlmAdviceGenerator(model, noKey, db.advices), noKey,
                db.tasks, db.domains, db.runs, db.advices, studySettings);
        assertFalse(withoutKey.status().apiKeyConfigured());
        assertThrows(ApiException.class, () -> withoutKey.runNow(1));
        assertThrows(ApiException.class, () -> agent.runNow(0));
        assertThrows(ApiException.class, () -> agent.runNow(LlmAgentService.MAX_REPETITIONS + 1));
    }

    @Test
    void failedCallsAreLoggedNotThrown() {
        model.failAll = true;
        TestCallResult result = agent.testCall(task("SHOP-1").getId());
        assertFalse(result.result().valid());
        assertTrue(result.result().validationError().startsWith("Call failed"));
        assertTrue(result.messagesSent().contains("[system]"));
    }

    // ------------------------------------------------------------------ freezing & modes

    @Test
    void freezingPicksTheMajorityAnswerPerTask() {
        scriptMixedBehaviour();
        RunSummary run = agent.runNow(3);
        List<FrozenRow> bank = agent.freeze(run.id());

        FrozenRow sci3 = row(bank, "SCI-3");
        assertEquals(2, sci3.answerIndex(), "2 of 3 calls were right, so the right answer is frozen");
        assertTrue(sci3.correct());
        FrozenRow health1 = row(bank, "HEALTH-1");
        assertEquals(0, health1.answerIndex());
        assertFalse(health1.correct(), "a consistent error is kept: it is what the model really does");
        assertNull(row(bank, "LOGIC-2").adviceId(), "no valid answer: scripted fallback will be used");
        assertEquals(run.id(), studySettings.getActiveRunId());
        assertEquals(15, agent.status().frozenAnswers());
    }

    @Test
    void researcherCanSwapAFrozenAnswerOnlyForAValidAnswerToTheSameTask() {
        scriptMixedBehaviour();
        RunSummary run = agent.runNow(3);
        agent.freeze(run.id());
        RunDetail detail = agent.runDetail(run.id());
        CallRow wrongSci3 = detail.tasks().stream().filter(t -> t.taskCode().equals("SCI-3")).findFirst().orElseThrow()
                .callRows().stream().filter(c -> !c.correct()).findFirst().orElseThrow();
        CallRow shop1 = detail.tasks().stream().filter(t -> t.taskCode().equals("SHOP-1")).findFirst().orElseThrow()
                .callRows().get(0);
        Long sci3Id = task("SCI-3").getId();

        List<FrozenRow> bank = agent.chooseFrozen(sci3Id, wrongSci3.id());
        assertEquals(wrongSci3.id(), row(bank, "SCI-3").adviceId());
        assertFalse(row(bank, "SCI-3").majorityAnswer(), "the dashboard flags a non-majority choice");
        assertThrows(ApiException.class, () -> agent.chooseFrozen(sci3Id, shop1.id()), "answer to another task");
    }

    @Test
    void llmModesRequireAMeasuredRunAndLiveModeRequiresAKey() {
        assertThrows(ApiException.class, () -> agent.setMode("LLM_FROZEN"));
        assertThrows(ApiException.class, () -> agent.setMode("NOT_A_MODE"));
        agent.freeze(agent.runNow(1).id());
        assertEquals("LLM_FROZEN", agent.setMode("LLM_FROZEN").agentMode());
        assertEquals("LLM_LIVE", agent.setMode("LLM_LIVE").agentMode());
        assertEquals("SCRIPTED", agent.setMode("SCRIPTED").agentMode());
    }

    // ------------------------------------------------------------------ serving advice to participants

    private SessionResponse register() {
        return experiment.register(new DemographicsRequest("25-34", null, "Master's degree", 4, "Weekly", 5,
                true, "CAPABILITY_REASONING"));
    }

    /** Answers stage 1 for the participant's question that has the given task code. */
    private Trial answerTask(SessionResponse s, String code) {
        Participant p = experiment.findParticipant(s.participantId());
        for (int i = 0; i < p.getTaskOrderList().size(); i++) {
            TrialView v = experiment.getTrial(s.participantId(), i);
            experiment.submitInitial(s.participantId(), i, new InitialAnswerRequest(0, 50, 1L));
            Trial t = db.trials.findByParticipantAndTrialIndex(p, i).orElseThrow();
            if (task(code).getId().equals(v.taskId())) {
                return t;
            }
            experiment.submitFinal(s.participantId(), i, new FinalAnswerRequest(0, 50, 4, 1L));
        }
        throw new AssertionError("task not found");
    }

    @Test
    void frozenModeServesTheReviewedLlmAnswerWithMeasuredCapability() {
        scriptMixedBehaviour();
        RunSummary run = agent.runNow(3);
        agent.freeze(run.id());
        agent.setMode("LLM_FROZEN");
        SessionResponse s = register();
        Participant p = experiment.findParticipant(s.participantId());
        assertEquals(AgentMode.LLM_FROZEN, p.getAgentMode());
        assertEquals(run.id(), p.getEvaluationRunId());

        Trial health1 = answerTask(s, "HEALTH-1");
        assertEquals(AdviceSource.LLM_FROZEN, health1.getAdviceSource());
        assertEquals(0, health1.getAiAnswer());
        assertFalse(health1.getAiCorrect(), "correctness comes from the answer key, not the scripted design");
        assertTrue(health1.getInitialAgree());

        TrialView view = experiment.getTrial(s.participantId(), health1.getTrialIndex());
        assertEquals(List.of("First step.", "Second step."), view.advice().reasoningSteps());
        assertEquals("MEASURED", view.advice().capability().source());
        assertEquals(75.0, view.advice().capability().historicalAccuracy(), 1e-9);
        assertEquals(12, view.advice().capability().sampleSize());
        assertNotNull(view.advice().capability().measuredOn());
        assertNull(view.advice().capability().benchmarkName());
    }

    @Test
    void frozenModeFallsBackToTheScriptedAnswerWhenTheModelNeverAnsweredValidly() {
        scriptMixedBehaviour();
        agent.freeze(agent.runNow(3).id());
        agent.setMode("LLM_FROZEN");
        Trial logic2 = answerTask(register(), "LOGIC-2");
        assertEquals(AdviceSource.FALLBACK_SCRIPTED, logic2.getAdviceSource());
        assertEquals(task("LOGIC-2").getAiAnswerIndex(), logic2.getAiAnswer());
        assertNull(logic2.getLlmAdviceId());
    }

    @Test
    void liveModeCallsTheModelAndFallsBackToTheFrozenAnswerWhenTheCallFails() {
        agent.freeze(agent.runNow(1).id());
        agent.setMode("LLM_LIVE");
        SessionResponse s = register();

        Trial first = answerTask(s, "SHOP-2");
        assertEquals(AdviceSource.LLM_LIVE, first.getAdviceSource());
        assertNotNull(first.getLlmAdviceId());
        assertTrue(first.getAiCorrect());

        model.failAll = true;
        Trial second = answerTask(register(), "SHOP-3");
        assertEquals(AdviceSource.FALLBACK_FROZEN, second.getAdviceSource());
        assertNotNull(second.getLlmAdviceId(), "the frozen answer's call id is recorded");
    }

    @Test
    void agentModeIsFixedAtRegistration() {
        agent.freeze(agent.runNow(1).id());
        SessionResponse scripted = register();
        agent.setMode("LLM_FROZEN");
        SessionResponse llm = register();
        assertEquals(AgentMode.SCRIPTED, experiment.findParticipant(scripted.participantId()).getAgentMode());
        assertEquals(AgentMode.LLM_FROZEN, experiment.findParticipant(llm.participantId()).getAgentMode());

        Trial t = answerTask(scripted, "HEALTH-1");
        assertEquals(AdviceSource.SCRIPTED, t.getAdviceSource());
        TrialView v = experiment.getTrial(scripted.participantId(), t.getTrialIndex());
        assertEquals("ADVERTISED", v.advice().capability().source());
    }

    private static FrozenRow row(List<FrozenRow> bank, String code) {
        return bank.stream().filter(r -> r.taskCode().equals(code)).findFirst().orElseThrow();
    }
}
