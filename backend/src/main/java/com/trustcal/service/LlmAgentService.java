package com.trustcal.service;

import com.trustcal.config.ApiException;
import com.trustcal.dto.AdminDtos.*;
import com.trustcal.entity.*;
import com.trustcal.llm.*;
import com.trustcal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Researcher-side management of the LLM agent:
 * <ul>
 *   <li>the fixed evaluation loop (every task x N calls, scored against the answer key),</li>
 *   <li>freezing one reviewed answer per task for LLM_FROZEN mode,</li>
 *   <li>switching the agent mode for newly registered participants,</li>
 *   <li>a single test call for checking the connection and the prompt.</li>
 * </ul>
 */
@Service
public class LlmAgentService {

    private static final Logger log = LoggerFactory.getLogger(LlmAgentService.class);
    public static final int MAX_REPETITIONS = 10;

    private final LlmAdviceGenerator generator;
    private final LlmSettings settings;
    private final DecisionTaskRepository taskRepository;
    private final DomainProfileRepository domainRepository;
    private final EvaluationRunRepository runRepository;
    private final LlmAdviceRepository adviceRepository;
    private final StudySettingsService studySettings;

    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "llm-evaluation");
        t.setDaemon(true);
        return t;
    });
    private volatile Long runningRunId;

    public LlmAgentService(LlmAdviceGenerator generator, LlmSettings settings, DecisionTaskRepository taskRepository,
                           DomainProfileRepository domainRepository, EvaluationRunRepository runRepository,
                           LlmAdviceRepository adviceRepository, StudySettingsService studySettings) {
        this.generator = generator;
        this.settings = settings;
        this.taskRepository = taskRepository;
        this.domainRepository = domainRepository;
        this.runRepository = runRepository;
        this.adviceRepository = adviceRepository;
        this.studySettings = studySettings;
    }

    /** A run cannot survive a restart: mark any run left RUNNING as FAILED. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        for (EvaluationRun r : runRepository.findByStatus(EvaluationRun.Status.RUNNING)) {
            r.setStatus(EvaluationRun.Status.FAILED);
            r.setNote("Interrupted by a server restart");
            r.setFinishedAt(LocalDateTime.now());
            runRepository.save(r);
        }
    }

    // ------------------------------------------------------------------ status & mode

    public AgentStatus status() {
        int frozen = (int) adviceRepository.findFrozenWithTask().stream().filter(LlmAdvice::isValid).count();
        return new AgentStatus(settings.isConfigured(), settings.getBaseUrl(), settings.getModel(),
                settings.getTemperature(), PromptSpec.VERSION, PromptSpec.describe(), PromptSpec.CHECKED_CRITERIA,
                PromptSpec.HUMAN_CRITERIA, studySettings.getAgentMode().name(), studySettings.getActiveRunId(),
                frozen, (int) taskRepository.count(), runningRunId);
    }

    /** Applies to participants who register from now on; existing participants keep their mode. */
    public AgentStatus setMode(String value) {
        AgentMode mode;
        try {
            mode = AgentMode.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown agent mode: " + value);
        }
        if (mode.usesLlm()) {
            Long active = studySettings.getActiveRunId();
            if (active == null || runRepository.findById(active).isEmpty()) {
                throw ApiException.badRequest("Run an evaluation and freeze its answers first: "
                        + "the LLM modes need a measured capability signal");
            }
        }
        if (mode == AgentMode.LLM_LIVE && !settings.isConfigured()) {
            throw ApiException.badRequest("Live mode needs an API key (set LLM_API_KEY or backend/local-secrets.yml, then restart the backend)");
        }
        studySettings.setAgentMode(mode);
        return status();
    }

    // ------------------------------------------------------------------ test call

    public TestCallResult testCall(Long taskId) {
        DecisionTask task = taskId == null
                ? taskRepository.findAllByOrderByDisplayOrderAsc().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("No tasks"))
                : taskRepository.findById(taskId).orElseThrow(() -> ApiException.notFound("Task not found"));
        String domainName = domainName(task);
        LlmAdvice advice = generator.generate(task, domainName, LlmAdvice.Purpose.TEST, null);
        String sent = PromptSpec.messagesFor(task, domainName).stream()
                .map(m -> "[" + m.role() + "]\n" + m.content())
                .collect(Collectors.joining("\n\n"));
        return new TestCallResult(task.getCode(), task.getPrompt(), task.getOptions(), task.getCorrectIndex(), sent,
                callRow(advice));
    }

    // ------------------------------------------------------------------ evaluation runs

    /** Starts a run in the background; poll {@link #runs()} for progress. */
    public RunSummary startRun(int repetitions) {
        EvaluationRun run = createRun(repetitions);
        coordinator.submit(() -> execute(run));
        return summary(run);
    }

    /** Runs synchronously (used by tests). */
    public RunSummary runNow(int repetitions) {
        EvaluationRun run = createRun(repetitions);
        execute(run);
        return summary(runRepository.findById(run.getId()).orElse(run));
    }

    private synchronized EvaluationRun createRun(int repetitions) {
        if (!settings.isConfigured()) {
            throw ApiException.badRequest("No API key configured. Set LLM_API_KEY (or backend/local-secrets.yml) and restart the backend.");
        }
        if (repetitions < 1 || repetitions > MAX_REPETITIONS) {
            throw ApiException.badRequest("Repetitions must be between 1 and " + MAX_REPETITIONS);
        }
        if (runningRunId != null) {
            throw ApiException.conflict("Run #" + runningRunId + " is still in progress");
        }
        EvaluationRun run = new EvaluationRun();
        run.setModel(settings.getModel());
        run.setPromptVersion(PromptSpec.VERSION);
        run.setTemperature(settings.getTemperature());
        run.setRepetitions(repetitions);
        run.setTotalCalls((int) taskRepository.count() * repetitions);
        runRepository.save(run);
        runningRunId = run.getId();
        return run;
    }

    private void execute(EvaluationRun run) {
        ExecutorService pool = Executors.newFixedThreadPool(settings.getEvaluationThreads());
        try {
            List<DecisionTask> tasks = taskRepository.findAllByOrderByDisplayOrderAsc();
            Map<String, String> names = domainNames();
            List<Future<?>> futures = new ArrayList<>();
            for (int rep = 0; rep < run.getRepetitions(); rep++) {
                for (DecisionTask task : tasks) {
                    futures.add(pool.submit(() -> generator.generate(task,
                            names.getOrDefault(task.getDomainCode(), task.getDomainCode()),
                            LlmAdvice.Purpose.EVALUATION, run.getId())));
                }
            }
            for (Future<?> f : futures) {
                f.get();
            }
            finish(run, EvaluationRun.Status.COMPLETED, null);
        } catch (Exception e) {
            log.error("Evaluation run {} failed", run.getId(), e);
            finish(run, EvaluationRun.Status.FAILED, "Run aborted: " + e.getClass().getSimpleName());
        } finally {
            pool.shutdownNow();
            runningRunId = null;
        }
    }

    /** Scores the run: correct / attempted per domain (an invalid reply counts as a failure). */
    private void finish(EvaluationRun run, EvaluationRun.Status status, String note) {
        List<LlmAdvice> calls = adviceRepository.findByRunIdWithTask(run.getId());
        run.setValidCalls((int) calls.stream().filter(LlmAdvice::isValid).count());
        run.setCorrectCalls((int) calls.stream().filter(LlmAdvice::isCorrect).count());
        Map<String, Integer> accuracy = new LinkedHashMap<>();
        Map<String, Integer> attempts = new LinkedHashMap<>();
        for (DomainProfile d : domainRepository.findAllByOrderByDisplayOrderAsc()) {
            List<LlmAdvice> inDomain = calls.stream()
                    .filter(c -> c.getTask().getDomainCode().equals(d.getCode())).toList();
            if (!inDomain.isEmpty()) {
                long correct = inDomain.stream().filter(LlmAdvice::isCorrect).count();
                accuracy.put(d.getCode(), (int) Math.round(100.0 * correct / inDomain.size()));
                attempts.put(d.getCode(), inDomain.size());
            }
        }
        run.setDomainAccuracy(accuracy);
        run.setDomainAttempts(attempts);
        run.setStatus(status);
        run.setNote(note);
        run.setFinishedAt(LocalDateTime.now());
        runRepository.save(run);
    }

    public List<RunSummary> runs() {
        return runRepository.findAllByOrderByIdDesc().stream().map(this::summary).toList();
    }

    public RunDetail runDetail(Long runId) {
        EvaluationRun run = runRepository.findById(runId).orElseThrow(() -> ApiException.notFound("Run not found"));
        Map<Long, List<LlmAdvice>> byTask = adviceRepository.findByRunIdWithTask(runId).stream()
                .collect(Collectors.groupingBy(a -> a.getTask().getId(), LinkedHashMap::new, Collectors.toList()));
        List<TaskResult> results = new ArrayList<>();
        for (DecisionTask task : taskRepository.findAllByOrderByDisplayOrderAsc()) {
            List<LlmAdvice> calls = byTask.getOrDefault(task.getId(), List.of());
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (LlmAdvice c : calls) {
                String key = c.isValid() ? String.valueOf(PromptSpec.LETTERS.charAt(c.getAnswerIndex())) : "invalid";
                counts.merge(key, 1, Integer::sum);
            }
            results.add(new TaskResult(task.getId(), task.getCode(), task.getDomainCode(), task.getPrompt(),
                    task.getOptions(), task.getCorrectIndex(), calls.size(),
                    (int) calls.stream().filter(LlmAdvice::isValid).count(),
                    (int) calls.stream().filter(LlmAdvice::isCorrect).count(),
                    counts, calls.stream().map(this::callRow).toList()));
        }
        return new RunDetail(summary(run), results);
    }

    // ------------------------------------------------------------------ frozen answer bank

    /**
     * Freezes one answer per task from a completed run: by default the most frequent valid answer
     * (majority vote over the repetitions). The run becomes the source of the capability signal.
     */
    public List<FrozenRow> freeze(Long runId) {
        EvaluationRun run = runRepository.findById(runId).orElseThrow(() -> ApiException.notFound("Run not found"));
        if (run.getStatus() != EvaluationRun.Status.COMPLETED) {
            throw ApiException.badRequest("Only a completed run can be frozen");
        }
        for (LlmAdvice a : adviceRepository.findFrozenWithTask()) {
            a.setFrozen(false);
            adviceRepository.save(a);
        }
        Map<Long, List<LlmAdvice>> byTask = adviceRepository.findByRunIdWithTask(runId).stream()
                .filter(LlmAdvice::isValid)
                .collect(Collectors.groupingBy(a -> a.getTask().getId()));
        for (List<LlmAdvice> calls : byTask.values()) {
            int majority = majorityAnswer(calls);
            calls.stream().filter(c -> c.getAnswerIndex() == majority)
                    .min(Comparator.comparing(LlmAdvice::getId))
                    .ifPresent(c -> {
                        c.setFrozen(true);
                        adviceRepository.save(c);
                    });
        }
        studySettings.setActiveRunId(runId);
        return frozenBank();
    }

    /** The researcher replaces the frozen answer of one task with another valid answer from the active run. */
    public List<FrozenRow> chooseFrozen(Long taskId, Long adviceId) {
        Long activeRun = studySettings.getActiveRunId();
        LlmAdvice chosen = adviceRepository.findById(adviceId)
                .orElseThrow(() -> ApiException.notFound("Answer not found"));
        if (!chosen.isValid() || !chosen.getTask().getId().equals(taskId) || !Objects.equals(chosen.getRunId(), activeRun)) {
            throw ApiException.badRequest("Choose a valid answer to this task from the active run");
        }
        adviceRepository.findFrozenWithTask().stream()
                .filter(a -> a.getTask().getId().equals(taskId))
                .forEach(a -> {
                    a.setFrozen(false);
                    adviceRepository.save(a);
                });
        chosen.setFrozen(true);
        adviceRepository.save(chosen);
        return frozenBank();
    }

    public List<FrozenRow> frozenBank() {
        Long activeRun = studySettings.getActiveRunId();
        Map<Long, LlmAdvice> frozen = adviceRepository.findFrozenWithTask().stream()
                .collect(Collectors.toMap(a -> a.getTask().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<LlmAdvice>> runCalls = activeRun == null ? Map.of()
                : adviceRepository.findByRunIdWithTask(activeRun).stream().filter(LlmAdvice::isValid)
                .collect(Collectors.groupingBy(a -> a.getTask().getId()));
        List<FrozenRow> rows = new ArrayList<>();
        for (DecisionTask task : taskRepository.findAllByOrderByDisplayOrderAsc()) {
            LlmAdvice a = frozen.get(task.getId());
            List<LlmAdvice> calls = runCalls.getOrDefault(task.getId(), List.of());
            boolean majority = a != null && !calls.isEmpty() && a.getAnswerIndex() == majorityAnswer(calls);
            rows.add(new FrozenRow(task.getId(), task.getCode(), task.getDomainCode(), task.getPrompt(),
                    task.getOptions(), task.getCorrectIndex(),
                    a == null ? null : a.getId(),
                    a == null ? null : a.getAnswerIndex(),
                    a == null ? null : task.getOptions().get(a.getAnswerIndex()),
                    a == null ? null : a.isCorrect(),
                    a == null ? List.of() : a.getReasoningSteps(),
                    majority));
        }
        return rows;
    }

    /** Most frequent answer index; ties go to the answer that appeared first. */
    static int majorityAnswer(List<LlmAdvice> validCalls) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        validCalls.stream().sorted(Comparator.comparing(LlmAdvice::getId))
                .forEach(c -> counts.merge(c.getAnswerIndex(), 1, Integer::sum));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);
    }

    // ------------------------------------------------------------------ export

    /** Every logged model call (evaluation, live and test), for the prompt log and analysis. */
    public String callsCsv() {
        StringBuilder sb = new StringBuilder(
                "call_id,purpose,run_id,task_code,domain,model,prompt_version,valid,validation_error,answer,correct,"
                        + "self_reported_confidence,latency_ms,frozen,created_at,reasoning_steps\n");
        for (LlmAdvice a : adviceRepository.findAllWithTask()) {
            String answer = a.getAnswerIndex() == null ? "" : String.valueOf(PromptSpec.LETTERS.charAt(a.getAnswerIndex()));
            sb.append(String.join(",", List.of(
                    String.valueOf(a.getId()), a.getPurpose().name(), a.getRunId() == null ? "" : a.getRunId().toString(),
                    a.getTask().getCode(), a.getTask().getDomainCode(), csv(a.getModel()), a.getPromptVersion(),
                    a.isValid() ? "1" : "0", csv(a.getValidationError()), answer, a.isCorrect() ? "1" : "0",
                    a.getSelfReportedConfidence() == null ? "" : a.getSelfReportedConfidence().toString(),
                    a.getLatencyMs() == null ? "" : a.getLatencyMs().toString(), a.isFrozen() ? "1" : "0",
                    String.valueOf(a.getCreatedAt()), csv(String.join(" | ", a.getReasoningSteps())))))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ------------------------------------------------------------------ helpers

    private RunSummary summary(EvaluationRun r) {
        int completed = r.getStatus() == EvaluationRun.Status.RUNNING
                ? (int) adviceRepository.countByRunId(r.getId()) : r.getTotalCalls();
        Double validRate = r.getValidCalls() == null || r.getTotalCalls() == 0 ? null : r.getValidCalls() / (double) r.getTotalCalls();
        Double accuracy = r.getCorrectCalls() == null || r.getTotalCalls() == 0 ? null : r.getCorrectCalls() / (double) r.getTotalCalls();
        return new RunSummary(r.getId(), r.getStatus().name(), r.getModel(), r.getPromptVersion(), r.getTemperature(),
                r.getRepetitions(), r.getTotalCalls(), completed, r.getValidCalls(), r.getCorrectCalls(), validRate,
                accuracy, r.getDomainAccuracy(), Objects.equals(r.getId(), studySettings.getActiveRunId()), r.getNote(),
                r.getStartedAt(), r.getFinishedAt());
    }

    private CallRow callRow(LlmAdvice a) {
        String answerText = a.getAnswerIndex() == null ? null : a.getTask().getOptions().get(a.getAnswerIndex());
        return new CallRow(a.getId(), a.isValid(), a.getValidationError(), a.getAnswerIndex(), answerText,
                a.isCorrect(), a.getSelfReportedConfidence(), a.getLatencyMs(), a.getReasoningSteps(),
                a.getRawOutput(), a.isFrozen());
    }

    private Map<String, String> domainNames() {
        return domainRepository.findAll().stream().collect(Collectors.toMap(DomainProfile::getCode, DomainProfile::getName));
    }

    private String domainName(DecisionTask task) {
        return domainNames().getOrDefault(task.getDomainCode(), task.getDomainCode());
    }
}
