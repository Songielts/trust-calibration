package com.trustcal.service;

import com.trustcal.config.ApiException;
import com.trustcal.dto.ParticipantDtos.*;
import com.trustcal.entity.*;
import com.trustcal.repository.*;
import com.trustcal.llm.LlmSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Participant-facing experiment logic: balanced condition assignment, task ordering,
 * the two-stage trial flow (answer first, then see advice), the post-task survey and the debrief.
 */
@Service
public class ExperimentService {

    /** Correct option of the capability comprehension question (see the survey page). */
    public static final String COMPREHENSION_CORRECT = "A";

    private final ParticipantRepository participantRepository;
    private final DecisionTaskRepository taskRepository;
    private final DomainProfileRepository domainRepository;
    private final TrialRepository trialRepository;
    private final PostSurveyRepository surveyRepository;
    private final AdviceProvider adviceProvider;
    private final StudySettingsService studySettings;
    private final LlmSettings llmSettings;
    private final Random random = new Random();

    /** When true, a participant may pick the condition through a demo link (?demo=CONDITION). */
    @Value("${study.demo-mode-enabled:true}")
    private boolean demoModeEnabled = true;

    public ExperimentService(ParticipantRepository participantRepository,
                             DecisionTaskRepository taskRepository,
                             DomainProfileRepository domainRepository,
                             TrialRepository trialRepository,
                             PostSurveyRepository surveyRepository,
                             AdviceProvider adviceProvider,
                             StudySettingsService studySettings,
                             LlmSettings llmSettings) {
        this.participantRepository = participantRepository;
        this.taskRepository = taskRepository;
        this.domainRepository = domainRepository;
        this.trialRepository = trialRepository;
        this.surveyRepository = surveyRepository;
        this.adviceProvider = adviceProvider;
        this.studySettings = studySettings;
        this.llmSettings = llmSettings;
    }

    // ------------------------------------------------------------------ registration

    public StudyInfo studyInfo() {
        int tasks = (int) taskRepository.count();
        return new StudyInfo(tasks, (int) domainRepository.count(), Math.max(10, Math.round(tasks * 0.9f) + 5),
                demoModeEnabled);
    }

    public List<DomainOption> domainOptions() {
        return domainRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(d -> new DomainOption(d.getCode(), d.getName()))
                .toList();
    }

    @Transactional
    public synchronized SessionResponse register(DemographicsRequest req) {
        Participant p = new Participant();
        p.setPublicId(UUID.randomUUID().toString());
        ExperimentCondition demoCondition = parseDemoCondition(req.demoCondition());
        if (demoCondition != null) {
            p.setCondition(demoCondition);
            p.setDemo(true);
        } else {
            p.setCondition(nextBalancedCondition(false));
        }
        p.setAgeGroup(req.ageGroup());
        p.setGender(req.gender());
        p.setEducation(req.education());
        p.setAiFamiliarity(req.aiFamiliarity());
        p.setAiUsageFrequency(req.aiUsageFrequency());
        p.setTrustPropensity(req.trustPropensity());
        p.setTaskOrderList(randomTaskOrder());
        // The agent mode is fixed per participant at registration, so a mode change on the dashboard
        // never switches the advice source in the middle of someone's session.
        AgentMode mode = studySettings.getAgentMode();
        p.setAgentMode(mode);
        if (mode.usesLlm()) {
            p.setEvaluationRunId(studySettings.getActiveRunId());
        }
        participantRepository.save(p);
        return toSession(p);
    }

    private ExperimentCondition parseDemoCondition(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!demoModeEnabled) {
            throw ApiException.badRequest("Demo mode is disabled for this study");
        }
        try {
            return ExperimentCondition.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown demo condition: " + value);
        }
    }

    /**
     * Assign to the condition with the fewest participants so far (ties broken at random).
     * Real participants are balanced against real participants only; demo sessions never count.
     */
    public ExperimentCondition nextBalancedCondition(boolean simulated) {
        long min = Long.MAX_VALUE;
        List<ExperimentCondition> candidates = new ArrayList<>();
        for (ExperimentCondition c : ExperimentCondition.values()) {
            long n = participantRepository.countByConditionAndSimulatedAndDemo(c, simulated, false);
            if (n < min) {
                min = n;
                candidates.clear();
                candidates.add(c);
            } else if (n == min) {
                candidates.add(c);
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** Every participant sees all tasks in an independently shuffled order. */
    public List<Long> randomTaskOrder() {
        List<Long> ids = taskRepository.findAll().stream().map(DecisionTask::getId).collect(Collectors.toList());
        Collections.shuffle(ids, random);
        return ids;
    }

    @Transactional(readOnly = true)
    public SessionResponse session(String publicId) {
        return toSession(findParticipant(publicId));
    }

    private SessionResponse toSession(Participant p) {
        int completed = (int) trialRepository.countByParticipantAndCompletedTrue(p);
        boolean surveyDone = surveyRepository.existsByParticipant(p);
        return new SessionResponse(p.getPublicId(), p.getTaskOrderList().size(), completed, surveyDone,
                p.getCondition().isShowCapability(), p.getCondition().isShowReasoning(), p.getStatus().name(),
                p.isDemo(), p.isDemo() ? p.getCondition().getLabel() : null);
    }

    // ------------------------------------------------------------------ trials

    @Transactional(readOnly = true)
    public TrialView getTrial(String publicId, int index) {
        Participant p = findParticipant(publicId);
        DecisionTask task = taskAt(p, index);
        requirePreviousCompleted(p, index);
        Optional<Trial> existing = trialRepository.findByParticipantAndTrialIndex(p, index);
        return buildTrialView(p, task, index, existing.orElse(null));
    }

    /**
     * Stage 1: lock in the participant's own answer, then decide the advice.
     * Deliberately not one transaction: in LLM_LIVE mode the advice is a model call that can
     * take several seconds, and a database transaction should not stay open while it runs.
     */
    public TrialView submitInitial(String publicId, int index, InitialAnswerRequest req) {
        Participant p = findParticipant(publicId);
        DecisionTask task = taskAt(p, index);
        requirePreviousCompleted(p, index);
        validateOption(task, req.answer());

        Optional<Trial> existing = trialRepository.findByParticipantAndTrialIndex(p, index);
        if (existing.isPresent()) {
            // Idempotent: the initial answer is locked once given (it may not be revised after seeing advice)
            return buildTrialView(p, task, index, existing.get());
        }
        String domainName = domainRepository.findByCode(task.getDomainCode())
                .map(DomainProfile::getName).orElse(task.getDomainCode());
        AdviceProvider.ServedAdvice advice = adviceProvider.resolve(p, task, domainName);

        Trial t = new Trial();
        t.setParticipant(p);
        t.setTask(task);
        t.setTrialIndex(index);
        t.setInitialAnswer(req.answer());
        t.setInitialConfidence(req.confidence());
        t.setInitialTimeMs(req.timeMs());
        t.setAiAnswer(advice.answerIndex());
        t.setAdviceSource(advice.source());
        t.setLlmAdviceId(advice.llmAdviceId());
        // Correctness of the advice is decided by the system from the answer key, never by the model
        t.setAiCorrect(task.getCorrectIndex().equals(advice.answerIndex()));
        t.setInitialCorrect(req.answer().equals(task.getCorrectIndex()));
        t.setInitialAgree(req.answer().equals(advice.answerIndex()));
        try {
            trialRepository.save(t);
        } catch (DataIntegrityViolationException e) {
            // A double submission raced this one; return the answer that was stored first
            Trial first = trialRepository.findByParticipantAndTrialIndex(p, index).orElseThrow(() -> e);
            return buildTrialView(p, task, index, first);
        }
        return buildTrialView(p, task, index, t);
    }

    @Transactional
    public FinalAnswerResponse submitFinal(String publicId, int index, FinalAnswerRequest req) {
        Participant p = findParticipant(publicId);
        DecisionTask task = taskAt(p, index);
        validateOption(task, req.answer());
        Trial t = trialRepository.findByParticipantAndTrialIndex(p, index)
                .orElseThrow(() -> ApiException.badRequest("Submit your own answer before the final decision"));
        if (t.isCompleted()) {
            throw ApiException.conflict("This question has already been answered");
        }
        t.setFinalAnswer(req.answer());
        t.setFinalConfidence(req.confidence());
        t.setTrustRating(req.trustRating());
        t.setFinalTimeMs(req.timeMs());
        t.setFinalCorrect(req.answer().equals(task.getCorrectIndex()));
        t.setFinalAgree(req.answer().equals(served(t, task)));
        t.setChangedAnswer(!req.answer().equals(t.getInitialAnswer()));
        t.setCompleted(true);
        t.setCompletedAt(LocalDateTime.now());
        trialRepository.save(t);

        int total = p.getTaskOrderList().size();
        boolean finished = index + 1 >= total;
        return new FinalAnswerResponse(finished, finished ? null : index + 1);
    }

    private TrialView buildTrialView(Participant p, DecisionTask task, int index, Trial trial) {
        DomainProfile domain = domainRepository.findByCode(task.getDomainCode())
                .orElseThrow(() -> ApiException.notFound("Unknown domain " + task.getDomainCode()));
        int total = p.getTaskOrderList().size();
        if (trial == null) {
            return new TrialView(index, total, "INITIAL", task.getId(), domain.getCode(), domain.getName(),
                    task.getPrompt(), task.getOptions(), null, null, null);
        }
        return new TrialView(index, total, trial.isCompleted() ? "DONE" : "FINAL", task.getId(),
                domain.getCode(), domain.getName(), task.getPrompt(), task.getOptions(),
                trial.getInitialAnswer(), trial.getInitialConfidence(), buildAdvice(p, trial, task, domain));
    }

    /** The manipulation: which cues accompany the agent's answer depends on the condition. */
    private AdviceView buildAdvice(Participant p, Trial trial, DecisionTask task, DomainProfile domain) {
        ExperimentCondition condition = p.getCondition();
        int answer = served(trial, task);
        CapabilityView capability = condition.isShowCapability() ? adviceProvider.capabilityFor(p, domain) : null;
        List<String> reasoning = condition.isShowReasoning() ? reasoningFor(trial, task) : null;
        return new AdviceView(answer, task.getOptions().get(answer), capability, reasoning);
    }

    private List<String> reasoningFor(Trial trial, DecisionTask task) {
        return adviceProvider.reasoningFor(trial.getLlmAdviceId(), task.getReasoningSteps());
    }

    /** The answer the agent recommended on this trial (older rows have no stored answer). */
    private static int served(Trial trial, DecisionTask task) {
        return trial.getAiAnswer() != null ? trial.getAiAnswer() : task.getAiAnswerIndex();
    }

    // ------------------------------------------------------------------ survey & debrief

    @Transactional
    public DebriefView submitSurvey(String publicId, SurveyRequest req) {
        Participant p = findParticipant(publicId);
        int total = p.getTaskOrderList().size();
        if (trialRepository.countByParticipantAndCompletedTrue(p) < total) {
            throw ApiException.badRequest("Please finish all decision tasks first");
        }
        if (surveyRepository.existsByParticipant(p)) {
            throw ApiException.conflict("The questionnaire has already been submitted");
        }
        PostSurvey s = new PostSurvey();
        s.setParticipant(p);
        s.setTrustConfident(req.trustConfident());
        s.setTrustReliable(req.trustReliable());
        s.setTrustTrustworthy(req.trustTrustworthy());
        s.setTrustDependable(req.trustDependable());
        s.setTrustWary(req.trustWary());
        s.setEstimatedOverallAccuracy(req.estimatedOverallAccuracy());
        s.setEstimatedDomainAccuracy(sanitiseDomainEstimates(req.estimatedDomainAccuracy()));
        s.setInfoUsefulness(req.infoUsefulness());
        if (p.getCondition().isShowCapability()) {
            if (req.comprehensionAnswer() == null || req.comprehensionAnswer().isBlank()) {
                throw ApiException.badRequest("Please answer the question about the accuracy information");
            }
            s.setComprehensionAnswer(req.comprehensionAnswer());
            s.setComprehensionCorrect(COMPREHENSION_CORRECT.equals(req.comprehensionAnswer()));
        }
        s.setComments(req.comments());
        surveyRepository.save(s);

        p.setStatus(Participant.Status.COMPLETED);
        p.setCompletedAt(LocalDateTime.now());
        participantRepository.save(p);
        return buildDebrief(p);
    }

    private Map<String, Integer> sanitiseDomainEstimates(Map<String, Integer> input) {
        Set<String> valid = domainRepository.findAll().stream().map(DomainProfile::getCode).collect(Collectors.toSet());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String code : valid) {
            Integer v = input.get(code);
            if (v == null || v < 0 || v > 100) {
                throw ApiException.badRequest("Please give an accuracy estimate (0-100) for every topic");
            }
            out.put(code, v);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public DebriefView debrief(String publicId) {
        Participant p = findParticipant(publicId);
        if (p.getStatus() != Participant.Status.COMPLETED) {
            throw ApiException.badRequest("The study is not finished yet");
        }
        return buildDebrief(p);
    }

    private DebriefView buildDebrief(Participant p) {
        Map<String, DomainProfile> domains = domainRepository.findAll().stream()
                .collect(Collectors.toMap(DomainProfile::getCode, Function.identity()));
        List<Trial> trials = trialRepository.findByParticipantOrderByTrialIndexAsc(p);
        List<DebriefItem> items = new ArrayList<>();
        int initialCorrect = 0, finalCorrect = 0, aiCorrect = 0, aiWrong = 0, followedWrong = 0;
        for (Trial t : trials) {
            if (!t.isCompleted()) continue;
            DecisionTask task = t.getTask();
            List<String> opts = task.getOptions();
            if (t.getInitialCorrect()) initialCorrect++;
            if (t.getFinalCorrect()) finalCorrect++;
            if (t.getAiCorrect()) aiCorrect++;
            else {
                aiWrong++;
                if (t.getFinalAgree()) followedWrong++;
            }
            DomainProfile d = domains.get(task.getDomainCode());
            items.add(new DebriefItem(t.getTrialIndex() + 1, d == null ? task.getDomainCode() : d.getName(),
                    task.getPrompt(), opts.get(t.getFinalAnswer()), opts.get(served(t, task)),
                    opts.get(task.getCorrectIndex()), t.getAiCorrect(), t.getFinalCorrect(), task.getExplanation()));
        }
        AgentMode mode = p.getAgentMode();
        return new DebriefView(items.size(), initialCorrect, finalCorrect, aiCorrect, followedWrong, aiWrong,
                p.getCondition().getLabel(), mode.name(), mode.usesLlm() ? llmSettings.getModel() : null, items);
    }

    // ------------------------------------------------------------------ helpers

    public void setDemoModeEnabled(boolean demoModeEnabled) {
        this.demoModeEnabled = demoModeEnabled;
    }

    public Participant findParticipant(String publicId) {
        return participantRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Participant session not found"));
    }

    private DecisionTask taskAt(Participant p, int index) {
        List<Long> order = p.getTaskOrderList();
        if (index < 0 || index >= order.size()) {
            throw ApiException.notFound("No question at position " + index);
        }
        return taskRepository.findById(order.get(index))
                .orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    private void requirePreviousCompleted(Participant p, int index) {
        long done = trialRepository.countByParticipantAndCompletedTrue(p);
        if (index > done) {
            throw ApiException.badRequest("Please answer the questions in order");
        }
    }

    private void validateOption(DecisionTask task, Integer answer) {
        if (answer == null || answer < 0 || answer >= task.getOptions().size()) {
            throw ApiException.badRequest("Invalid answer option");
        }
    }
}
