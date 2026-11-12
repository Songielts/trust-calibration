package com.trustcal.service;

import com.trustcal.dto.AdminDtos.*;
import com.trustcal.entity.*;
import com.trustcal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Computes reliance and trust-calibration measures per experimental condition.
 *
 * Reliance measures follow the judge-advisor paradigm used in the appropriate-reliance
 * literature (e.g. Schemmer et al., 2023; Bo et al., 2025):
 * <ul>
 *   <li>Over-reliance  = P(final answer = AI | AI wrong)</li>
 *   <li>Under-reliance = P(final answer != AI | AI correct)</li>
 *   <li>RAIR (relative AI reliance)   = P(switch to correct AI | initial wrong, AI correct)</li>
 *   <li>RSR  (relative self-reliance) = P(keep own correct answer | initial correct, AI wrong)</li>
 *   <li>Appropriate reliance = P(follow AI when it is right, or reject it when it is wrong)</li>
 * </ul>
 * Trust calibration is measured as (a) trust discrimination: mean trust rating when the AI is
 * correct minus when it is wrong, and the point-biserial correlation between trust and AI
 * correctness; and (b) calibration error: |estimated AI accuracy - actual accuracy| overall and per domain.
 */
@Service
public class AnalyticsService {

    private final ParticipantRepository participantRepository;
    private final TrialRepository trialRepository;
    private final PostSurveyRepository surveyRepository;
    private final DecisionTaskRepository taskRepository;
    private final DomainProfileRepository domainRepository;

    public AnalyticsService(ParticipantRepository participantRepository, TrialRepository trialRepository,
                            PostSurveyRepository surveyRepository, DecisionTaskRepository taskRepository,
                            DomainProfileRepository domainRepository) {
        this.participantRepository = participantRepository;
        this.trialRepository = trialRepository;
        this.surveyRepository = surveyRepository;
        this.taskRepository = taskRepository;
        this.domainRepository = domainRepository;
    }

    // ------------------------------------------------------------------ report

    public AnalyticsReport report(boolean includeSimulated) {
        return report(includeSimulated, null);
    }

    /**
     * @param agentMode null or "ALL" for every participant, otherwise only participants who worked
     *                  with that agent mode (SCRIPTED, LLM_FROZEN or LLM_LIVE). Conditions should be
     *                  compared within one mode, because the agent's reliability differs between modes.
     */
    @Transactional(readOnly = true)
    public AnalyticsReport report(boolean includeSimulated, String agentMode) {
        AgentMode modeFilter = parseMode(agentMode);
        List<Participant> allParticipants = participantRepository.findAll();
        Predicate<Participant> included = p -> p.getStatus() == Participant.Status.COMPLETED
                && (includeSimulated || p.isStudyData())
                && (modeFilter == null || p.getAgentMode() == modeFilter);

        List<Trial> trials = trialRepository.findAllCompletedWithDetails().stream()
                .filter(t -> included.test(t.getParticipant())).toList();
        List<PostSurvey> surveys = surveyRepository.findAllWithParticipant().stream()
                .filter(s -> included.test(s.getParticipant())).toList();

        List<DecisionTask> tasks = taskRepository.findAll();
        // The agent's actual accuracy is taken from the advice participants really saw; with no data
        // yet it falls back to the scripted design (12/16).
        double agentAccuracy = trials.isEmpty()
                ? (tasks.isEmpty() ? 0 : tasks.stream().filter(DecisionTask::isAiCorrect).count() / (double) tasks.size())
                : trials.stream().filter(Trial::getAiCorrect).count() / (double) trials.size();
        Map<String, Double> domainActual = trials.isEmpty() ? actualDomainAccuracy(tasks)
                : trials.stream().collect(Collectors.groupingBy(t -> t.getTask().getDomainCode(), LinkedHashMap::new,
                Collectors.averagingDouble(t -> t.getAiCorrect() ? 1.0 : 0.0)));
        Experienced experienced = experienced(trials);
        int half = Math.max(1, tasks.size() / 2);

        List<ConditionMetrics> perCondition = new ArrayList<>();
        List<BlockMetrics> blocks = new ArrayList<>();
        for (ExperimentCondition c : ExperimentCondition.values()) {
            List<Trial> ct = trials.stream().filter(t -> t.getParticipant().getCondition() == c).toList();
            List<PostSurvey> cs = surveys.stream().filter(s -> s.getParticipant().getCondition() == c).toList();
            perCondition.add(metrics(c.name(), c.getLabel(), ct, cs, experienced));
            blocks.add(blockMetrics(c, ct, half));
        }
        ConditionMetrics overall = metrics("ALL", "All conditions", trials, surveys, experienced);

        int completed = (int) allParticipants.stream().filter(included).count();
        int simulated = (int) allParticipants.stream().filter(Participant::isSimulated).count();
        int demo = (int) allParticipants.stream().filter(Participant::isDemo).count();
        int total = (int) allParticipants.stream().filter(p -> includeSimulated || p.isStudyData())
                .filter(p -> modeFilter == null || p.getAgentMode() == modeFilter).count();

        return new AnalyticsReport(includeSimulated, total, completed, simulated, demo, agentAccuracy,
                overall, perCondition, domainMetrics(tasks, trials, surveys, domainActual), blocks);
    }

    /** Accuracy of the advice each participant actually saw, overall and per domain. */
    private record Experienced(Map<Long, Double> overall, Map<Long, Map<String, Double>> byDomain) {
    }

    private Experienced experienced(List<Trial> trials) {
        Map<Long, List<Trial>> byParticipant = trials.stream().collect(Collectors.groupingBy(t -> t.getParticipant().getId()));
        Map<Long, Double> overall = new HashMap<>();
        Map<Long, Map<String, Double>> byDomain = new HashMap<>();
        byParticipant.forEach((id, ts) -> {
            overall.put(id, ts.stream().filter(Trial::getAiCorrect).count() / (double) ts.size());
            byDomain.put(id, ts.stream().collect(Collectors.groupingBy(t -> t.getTask().getDomainCode(),
                    Collectors.averagingDouble(t -> t.getAiCorrect() ? 1.0 : 0.0))));
        });
        return new Experienced(overall, byDomain);
    }

    private static AgentMode parseMode(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) return null;
        try {
            return AgentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ConditionMetrics metrics(String code, String label, List<Trial> trials, List<PostSurvey> surveys,
                                     Experienced experienced) {
        long participants = trials.stream().map(t -> t.getParticipant().getId()).distinct().count();

        Double meanTrustCorrect = mean(trials.stream().filter(Trial::getAiCorrect).toList(), t -> t.getTrustRating());
        Double meanTrustWrong = mean(trials.stream().filter(t -> !t.getAiCorrect()).toList(), t -> t.getTrustRating());
        Double discrimination = (meanTrustCorrect == null || meanTrustWrong == null) ? null : meanTrustCorrect - meanTrustWrong;

        List<Trial> timed = trials.stream()
                .filter(t -> t.getInitialTimeMs() != null && t.getFinalTimeMs() != null).toList();

        return new ConditionMetrics(
                code, label, (int) participants, trials.size(),
                rate(trials, t -> true, Trial::getInitialCorrect),
                rate(trials, t -> true, Trial::getFinalCorrect),
                rate(trials, t -> true, Trial::getFinalAgree),
                rate(trials, t -> !t.getInitialAgree(), Trial::getFinalAgree),
                rate(trials, t -> !t.getAiCorrect(), Trial::getFinalAgree),
                rate(trials, Trial::getAiCorrect, t -> !t.getFinalAgree()),
                rate(trials, t -> t.getAiCorrect() && !t.getInitialCorrect(), Trial::getFinalCorrect),
                rate(trials, t -> !t.getAiCorrect() && t.getInitialCorrect(), Trial::getFinalCorrect),
                rate(trials, t -> true, t -> t.getFinalAgree().equals(t.getAiCorrect())),
                mean(trials, t -> t.getTrustRating()),
                meanTrustCorrect,
                meanTrustWrong,
                discrimination,
                pearson(trials, t -> t.getTrustRating(), t -> t.getAiCorrect() ? 1.0 : 0.0),
                mean(surveys, PostSurvey::trustScaleMean),
                mean(surveys, s -> s.getEstimatedOverallAccuracy()),
                // Calibration error: |estimate - accuracy of the advice this participant actually saw|
                mean(surveys.stream().filter(s -> experienced.overall().containsKey(s.getParticipant().getId())).toList(),
                        s -> Math.abs(s.getEstimatedOverallAccuracy() - experienced.overall().get(s.getParticipant().getId()) * 100)),
                mean(surveys.stream().filter(s -> domainError(s, experienced) != null).toList(),
                        s -> domainError(s, experienced)),
                rate(surveys, s -> s.getComprehensionCorrect() != null, PostSurvey::getComprehensionCorrect),
                mean(surveys.stream().filter(s -> s.getInfoUsefulness() != null).toList(), s -> s.getInfoUsefulness()),
                mean(timed, t -> (t.getInitialTimeMs() + t.getFinalTimeMs()) / 1000.0),
                mean(trials, t -> t.getFinalConfidence() - t.getInitialConfidence()));
    }

    /** Mean absolute difference between a participant's per-domain estimates and the accuracy they experienced per domain. */
    private Double domainError(PostSurvey s, Experienced experienced) {
        Map<String, Integer> est = s.getEstimatedDomainAccuracy();
        Map<String, Double> domainActual = experienced.byDomain().get(s.getParticipant().getId());
        if (est == null || est.isEmpty() || domainActual == null) return null;
        double sum = 0;
        int n = 0;
        for (Map.Entry<String, Double> e : domainActual.entrySet()) {
            Integer v = est.get(e.getKey());
            if (v != null) {
                sum += Math.abs(v - e.getValue() * 100);
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }

    private BlockMetrics blockMetrics(ExperimentCondition c, List<Trial> trials, int half) {
        List<Trial> first = trials.stream().filter(t -> t.getTrialIndex() < half).toList();
        List<Trial> second = trials.stream().filter(t -> t.getTrialIndex() >= half).toList();
        return new BlockMetrics(c.name(), c.getLabel(),
                rate(first, t -> true, Trial::getFinalAgree),
                rate(second, t -> true, Trial::getFinalAgree),
                rate(first, t -> !t.getAiCorrect(), Trial::getFinalAgree),
                rate(second, t -> !t.getAiCorrect(), Trial::getFinalAgree),
                mean(first, t -> t.getTrustRating()),
                mean(second, t -> t.getTrustRating()));
    }

    private List<DomainMetrics> domainMetrics(List<DecisionTask> tasks, List<Trial> trials,
                                              List<PostSurvey> surveys, Map<String, Double> domainActual) {
        List<DomainMetrics> out = new ArrayList<>();
        for (DomainProfile d : domainRepository.findAllByOrderByDisplayOrderAsc()) {
            int taskCount = (int) tasks.stream().filter(t -> t.getDomainCode().equals(d.getCode())).count();
            Map<String, DomainCell> cells = new LinkedHashMap<>();
            for (ExperimentCondition c : ExperimentCondition.values()) {
                List<Trial> dt = trials.stream()
                        .filter(t -> t.getParticipant().getCondition() == c)
                        .filter(t -> t.getTask().getDomainCode().equals(d.getCode())).toList();
                List<PostSurvey> ds = surveys.stream()
                        .filter(s -> s.getParticipant().getCondition() == c)
                        .filter(s -> s.getEstimatedDomainAccuracy() != null
                                && s.getEstimatedDomainAccuracy().get(d.getCode()) != null).toList();
                cells.put(c.name(), new DomainCell(
                        mean(dt, t -> t.getTrustRating()),
                        rate(dt, t -> true, Trial::getFinalAgree),
                        mean(ds, s -> s.getEstimatedDomainAccuracy().get(d.getCode())),
                        dt.size()));
            }
            out.add(new DomainMetrics(d.getCode(), d.getName(), d.getHistoricalAccuracy(),
                    domainActual.getOrDefault(d.getCode(), 0.0), taskCount, cells));
        }
        return out;
    }

    // ------------------------------------------------------------------ admin lists

    @Transactional(readOnly = true)
    public List<ParticipantRow> participantRows(boolean includeSimulated) {
        List<Participant> participants = includeSimulated
                ? participantRepository.findAllByOrderByCreatedAtDesc()
                : participantRepository.findBySimulatedFalseAndDemoFalseOrderByCreatedAtDesc();
        Map<Long, List<Trial>> trialsByParticipant = trialRepository.findAllCompletedWithDetails().stream()
                .collect(Collectors.groupingBy(t -> t.getParticipant().getId()));
        Map<Long, PostSurvey> surveyByParticipant = surveyRepository.findAllWithParticipant().stream()
                .collect(Collectors.toMap(s -> s.getParticipant().getId(), Function.identity()));

        return participants.stream().map(p -> {
            List<Trial> ts = trialsByParticipant.getOrDefault(p.getId(), List.of());
            PostSurvey s = surveyByParticipant.get(p.getId());
            return new ParticipantRow(p.getId(), p.getPublicId(), p.getCondition().name(), p.getStatus().name(),
                    p.isSimulated(), p.isDemo(), p.getAgentMode().name(), p.getAgeGroup(), p.getEducation(), p.getAiFamiliarity(), ts.size(),
                    rate(ts, t -> true, Trial::getFinalCorrect),
                    rate(ts, t -> !t.getAiCorrect(), Trial::getFinalAgree),
                    s == null ? null : s.trustScaleMean(),
                    s == null ? null : s.getEstimatedOverallAccuracy(),
                    p.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskRow> taskRows() {
        Map<String, String> names = domainRepository.findAll().stream()
                .collect(Collectors.toMap(DomainProfile::getCode, DomainProfile::getName));
        return taskRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(t -> new TaskRow(t.getId(), t.getCode(), t.getDomainCode(), names.get(t.getDomainCode()),
                        t.getPrompt(), t.getOptions(), t.getCorrectIndex(), t.getAiAnswerIndex(), t.isAiCorrect(),
                        t.getReasoningSteps(), t.getExplanation()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DomainRow> domainRows() {
        List<DecisionTask> tasks = taskRepository.findAll();
        Map<String, Double> actual = actualDomainAccuracy(tasks);
        return domainRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(d -> new DomainRow(d.getCode(), d.getName(), d.getHistoricalAccuracy(), d.getSampleSize(),
                        d.getBenchmarkName(), d.getBenchmarkScore(), actual.getOrDefault(d.getCode(), 0.0),
                        (int) tasks.stream().filter(t -> t.getDomainCode().equals(d.getCode())).count()))
                .toList();
    }

    // ------------------------------------------------------------------ CSV export

    @Transactional(readOnly = true)
    public String trialsCsv(boolean includeSimulated) {
        StringBuilder sb = new StringBuilder();
        sb.append("participant_id,condition,show_capability,show_reasoning,simulated,demo,agent_mode,trial_index,task_code,domain,")
                .append("advice_source,llm_advice_id,ai_answer,ai_correct,initial_answer,initial_correct,initial_confidence,initial_agree,")
                .append("final_answer,final_correct,final_confidence,final_agree,changed_answer,trust_rating,")
                .append("initial_time_ms,final_time_ms\n");
        for (Trial t : trialRepository.findAllCompletedWithDetails()) {
            Participant p = t.getParticipant();
            if (!includeSimulated && !p.isStudyData()) continue;
            sb.append(join(p.getId(), p.getCondition(), p.getCondition().isShowCapability(),
                    p.getCondition().isShowReasoning(), p.isSimulated(), p.isDemo(), p.getAgentMode(), t.getTrialIndex(),
                    t.getTask().getCode(), t.getTask().getDomainCode(), t.getAdviceSource(), t.getLlmAdviceId(),
                    t.servedAiAnswer(), b(t.getAiCorrect()), t.getInitialAnswer(), b(t.getInitialCorrect()),
                    t.getInitialConfidence(), b(t.getInitialAgree()), t.getFinalAnswer(), b(t.getFinalCorrect()),
                    t.getFinalConfidence(), b(t.getFinalAgree()), b(t.getChangedAnswer()), t.getTrustRating(),
                    t.getInitialTimeMs(), t.getFinalTimeMs())).append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String participantsCsv(boolean includeSimulated) {
        List<DomainProfile> domains = domainRepository.findAllByOrderByDisplayOrderAsc();
        Map<Long, PostSurvey> surveys = surveyRepository.findAllWithParticipant().stream()
                .collect(Collectors.toMap(s -> s.getParticipant().getId(), Function.identity()));
        Map<Long, List<Trial>> trials = trialRepository.findAllCompletedWithDetails().stream()
                .collect(Collectors.groupingBy(t -> t.getParticipant().getId()));

        StringBuilder sb = new StringBuilder();
        sb.append("participant_id,public_id,condition,show_capability,show_reasoning,simulated,demo,agent_mode,evaluation_run_id,status,age_group,gender,")
                .append("education,ai_familiarity,ai_usage_frequency,trust_propensity,trials_completed,")
                .append("final_accuracy,agreement_rate,over_reliance,under_reliance,mean_trust_rating,")
                .append("trust_confident,trust_reliable,trust_trustworthy,trust_dependable,trust_wary,trust_scale_mean,")
                .append("estimated_overall_accuracy");
        for (DomainProfile d : domains) sb.append(",estimated_").append(d.getCode().toLowerCase());
        sb.append(",info_usefulness,comprehension_answer,comprehension_correct,comments,created_at,completed_at\n");

        List<Participant> participants = includeSimulated
                ? participantRepository.findAllByOrderByCreatedAtDesc()
                : participantRepository.findBySimulatedFalseAndDemoFalseOrderByCreatedAtDesc();
        for (Participant p : participants) {
            List<Trial> ts = trials.getOrDefault(p.getId(), List.of());
            PostSurvey s = surveys.get(p.getId());
            List<Object> row = new ArrayList<>(List.of(
                    p.getId(), p.getPublicId(), p.getCondition(), p.getCondition().isShowCapability(),
                    p.getCondition().isShowReasoning(), p.isSimulated(), p.isDemo(), p.getAgentMode()));
            row.add(p.getEvaluationRunId());
            row.add(p.getStatus());
            row.addAll(Arrays.asList(p.getAgeGroup(), p.getGender(), p.getEducation(), p.getAiFamiliarity(),
                    p.getAiUsageFrequency(), p.getTrustPropensity(), ts.size(),
                    fmt(rate(ts, t -> true, Trial::getFinalCorrect)),
                    fmt(rate(ts, t -> true, Trial::getFinalAgree)),
                    fmt(rate(ts, t -> !t.getAiCorrect(), Trial::getFinalAgree)),
                    fmt(rate(ts, Trial::getAiCorrect, t -> !t.getFinalAgree())),
                    fmt(mean(ts, t -> t.getTrustRating()))));
            if (s != null) {
                row.addAll(Arrays.asList(s.getTrustConfident(), s.getTrustReliable(), s.getTrustTrustworthy(),
                        s.getTrustDependable(), s.getTrustWary(), fmt(s.trustScaleMean()), s.getEstimatedOverallAccuracy()));
                for (DomainProfile d : domains) row.add(s.getEstimatedDomainAccuracy().get(d.getCode()));
                row.addAll(Arrays.asList(s.getInfoUsefulness(), s.getComprehensionAnswer(),
                        b(s.getComprehensionCorrect()), s.getComments()));
            } else {
                for (int i = 0; i < 7 + domains.size() + 4; i++) row.add(null);
            }
            row.add(p.getCreatedAt());
            row.add(p.getCompletedAt());
            sb.append(join(row.toArray())).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ math helpers

    private Map<String, Double> actualDomainAccuracy(List<DecisionTask> tasks) {
        return tasks.stream().collect(Collectors.groupingBy(DecisionTask::getDomainCode, LinkedHashMap::new,
                Collectors.averagingDouble(t -> t.isAiCorrect() ? 1.0 : 0.0)));
    }

    static <T> Double rate(List<T> items, Predicate<T> filter, Predicate<T> success) {
        long n = 0, k = 0;
        for (T item : items) {
            if (filter.test(item)) {
                n++;
                if (success.test(item)) k++;
            }
        }
        return n == 0 ? null : k / (double) n;
    }

    static <T> Double mean(List<T> items, Function<T, ? extends Number> f) {
        double sum = 0;
        int n = 0;
        for (T item : items) {
            Number v = f.apply(item);
            if (v != null) {
                sum += v.doubleValue();
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }

    /** Pearson correlation; with a 0/1 variable this is the point-biserial correlation. */
    static <T> Double pearson(List<T> items, Function<T, ? extends Number> fx, ToDoubleFunction<T> fy) {
        List<double[]> pairs = new ArrayList<>();
        for (T item : items) {
            Number x = fx.apply(item);
            if (x != null) pairs.add(new double[]{x.doubleValue(), fy.applyAsDouble(item)});
        }
        int n = pairs.size();
        if (n < 3) return null;
        double mx = pairs.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double my = pairs.stream().mapToDouble(p -> p[1]).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for (double[] p : pairs) {
            sxy += (p[0] - mx) * (p[1] - my);
            sxx += (p[0] - mx) * (p[0] - mx);
            syy += (p[1] - my) * (p[1] - my);
        }
        if (sxx == 0 || syy == 0) return null;
        return sxy / Math.sqrt(sxx * syy);
    }

    private static String b(Boolean v) {
        return v == null ? "" : (v ? "1" : "0");
    }

    private static String fmt(Double v) {
        return v == null ? "" : String.format(Locale.ROOT, "%.4f", v);
    }

    private static String join(Object... values) {
        StringJoiner j = new StringJoiner(",");
        for (Object v : values) {
            j.add(csv(v));
        }
        return j.toString();
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
