package com.trustcal.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Response bodies used by the researcher (admin) API. Rates are proportions 0-1; null = no data. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record LoginRequest(String token) {
    }

    public record ConditionMetrics(
            String condition,
            String label,
            int participants,
            int trials,
            Double initialAccuracy,
            Double finalAccuracy,
            Double agreementRate,
            Double switchToAiRate,
            Double overReliance,
            Double underReliance,
            Double rair,
            Double rsr,
            Double appropriateReliance,
            Double meanTrust,
            Double meanTrustAiCorrect,
            Double meanTrustAiWrong,
            Double trustDiscrimination,
            Double trustCorrelation,
            Double trustScale,
            Double estimatedOverall,
            Double overallCalibrationError,
            Double domainCalibrationError,
            Double comprehensionRate,
            Double infoUsefulness,
            Double meanDecisionSeconds,
            Double confidenceShift) {
    }

    public record DomainCell(Double meanTrust, Double agreementRate, Double meanEstimate, int trials) {
    }

    public record DomainMetrics(
            String code,
            String name,
            double advertisedAccuracy,
            double actualAiAccuracy,
            int taskCount,
            Map<String, DomainCell> byCondition) {
    }

    public record BlockMetrics(
            String condition,
            String label,
            Double firstHalfAgreement,
            Double secondHalfAgreement,
            Double firstHalfOverReliance,
            Double secondHalfOverReliance,
            Double firstHalfTrust,
            Double secondHalfTrust) {
    }

    public record AnalyticsReport(
            boolean includeSimulated,
            int totalParticipants,
            int completedParticipants,
            int simulatedParticipants,
            int demoParticipants,
            double agentActualAccuracy,
            ConditionMetrics overall,
            List<ConditionMetrics> conditions,
            List<DomainMetrics> domains,
            List<BlockMetrics> blocks) {
    }

    public record ParticipantRow(
            Long id,
            String publicId,
            String condition,
            String status,
            boolean simulated,
            boolean demo,
            String agentMode,
            String ageGroup,
            String education,
            Integer aiFamiliarity,
            int trialsCompleted,
            Double finalAccuracy,
            Double overReliance,
            Double trustScale,
            Integer estimatedOverall,
            LocalDateTime createdAt) {
    }

    public record TaskRow(
            Long id,
            String code,
            String domainCode,
            String domainName,
            String prompt,
            List<String> options,
            int correctIndex,
            int aiAnswerIndex,
            boolean aiCorrect,
            List<String> reasoningSteps,
            String explanation) {
    }

    public record DomainRow(
            String code,
            String name,
            double historicalAccuracy,
            int sampleSize,
            String benchmarkName,
            double benchmarkScore,
            double actualAiAccuracy,
            int taskCount) {
    }

    public record SimulationResult(int created, String note) {
    }

    // ------------------------------------------------------------------ LLM agent

    public record AgentStatus(
            boolean apiKeyConfigured,
            String baseUrl,
            String model,
            double temperature,
            String promptVersion,
            String promptText,
            List<String> checkedCriteria,
            List<String> humanCriteria,
            String agentMode,
            Long activeRunId,
            int frozenAnswers,
            int taskCount,
            Long runningRunId) {
    }

    public record ModeRequest(String mode) {
    }

    public record RunSummary(
            Long id,
            String status,
            String model,
            String promptVersion,
            double temperature,
            int repetitions,
            int totalCalls,
            int completedCalls,
            Integer validCalls,
            Integer correctCalls,
            Double validRate,
            Double accuracy,
            Map<String, Integer> domainAccuracy,
            boolean active,
            String note,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {
    }

    public record CallRow(
            Long id,
            boolean valid,
            String validationError,
            Integer answerIndex,
            String answerText,
            boolean correct,
            Integer selfReportedConfidence,
            Long latencyMs,
            List<String> reasoningSteps,
            String rawOutput,
            boolean frozen) {
    }

    public record TaskResult(
            Long taskId,
            String taskCode,
            String domainCode,
            String prompt,
            List<String> options,
            int correctIndex,
            int calls,
            int validCalls,
            int correctCalls,
            Map<String, Integer> answerCounts,
            List<CallRow> callRows) {
    }

    public record RunDetail(RunSummary run, List<TaskResult> tasks) {
    }

    public record FrozenRow(
            Long taskId,
            String taskCode,
            String domainCode,
            String prompt,
            List<String> options,
            int correctIndex,
            Long adviceId,           // null = no usable LLM answer; scripted fallback will be shown
            Integer answerIndex,
            String answerText,
            Boolean correct,
            List<String> reasoningSteps,
            boolean majorityAnswer) {
    }

    public record FreezeRequest(Long adviceId) {
    }

    public record TestCallResult(
            String taskCode,
            String prompt,
            List<String> options,
            int correctIndex,
            String messagesSent,
            CallRow result) {
    }
}
