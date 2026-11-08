package com.trustcal.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

/** Request / response bodies used by the participant-facing API. */
public final class ParticipantDtos {

    private ParticipantDtos() {
    }

    public record DemographicsRequest(
            @NotBlank String ageGroup,
            String gender,
            @NotBlank String education,
            @NotNull @Min(1) @Max(7) Integer aiFamiliarity,
            @NotBlank String aiUsageFrequency,
            @NotNull @Min(1) @Max(7) Integer trustPropensity,
            @AssertTrue(message = "Consent is required") boolean consent,
            /** Optional: condition chosen through a demo link. Only accepted when demo mode is enabled. */
            String demoCondition) {
    }

    public record SessionResponse(
            String participantId,
            int totalTrials,
            int completedTrials,
            boolean surveyCompleted,
            boolean showCapability,
            boolean showReasoning,
            String status,
            boolean demo,
            String conditionLabel) {         // only filled for demo sessions, hidden from real participants
    }

    /**
     * The capability signal. source = ADVERTISED for the scripted agent (study material),
     * MEASURED for the LLM (accuracy measured by this system against the answer key, with its date).
     */
    public record CapabilityView(
            String domainName,
            double historicalAccuracy,
            int sampleSize,
            String benchmarkName,      // null for MEASURED
            Double benchmarkScore,     // null for MEASURED
            String source,
            String measuredOn) {       // ISO date, MEASURED only
    }

    public record AdviceView(
            int aiAnswerIndex,
            String aiAnswerText,
            CapabilityView capability,       // null unless the condition shows capability
            List<String> reasoningSteps) {   // null unless the condition shows reasoning
    }

    public record TrialView(
            int index,
            int total,
            String stage,                    // INITIAL or FINAL
            Long taskId,
            String domainCode,
            String domainName,
            String prompt,
            List<String> options,
            Integer initialAnswer,
            Integer initialConfidence,
            AdviceView advice) {
    }

    public record InitialAnswerRequest(
            @NotNull @Min(0) Integer answer,
            @NotNull @Min(0) @Max(100) Integer confidence,
            Long timeMs) {
    }

    public record FinalAnswerRequest(
            @NotNull @Min(0) Integer answer,
            @NotNull @Min(0) @Max(100) Integer confidence,
            @NotNull @Min(1) @Max(7) Integer trustRating,
            Long timeMs) {
    }

    public record FinalAnswerResponse(boolean finished, Integer nextIndex) {
    }

    public record SurveyRequest(
            @NotNull @Min(1) @Max(7) Integer trustConfident,
            @NotNull @Min(1) @Max(7) Integer trustReliable,
            @NotNull @Min(1) @Max(7) Integer trustTrustworthy,
            @NotNull @Min(1) @Max(7) Integer trustDependable,
            @NotNull @Min(1) @Max(7) Integer trustWary,
            @NotNull @Min(0) @Max(100) Integer estimatedOverallAccuracy,
            @NotNull Map<String, Integer> estimatedDomainAccuracy,
            @NotNull @Min(1) @Max(7) Integer infoUsefulness,
            String comprehensionAnswer,
            @Size(max = 2000) String comments) {
    }

    public record DebriefItem(
            int index,
            String domainName,
            String prompt,
            String yourAnswer,
            String aiAnswer,
            String correctAnswer,
            boolean aiCorrect,
            boolean youCorrect,
            String explanation) {
    }

    public record DebriefView(
            int total,
            int initialCorrect,
            int finalCorrect,
            int aiCorrect,
            int followedAiWhenWrong,
            int aiWrong,
            String conditionLabel,
            String agentMode,          // SCRIPTED, LLM_FROZEN or LLM_LIVE
            String modelName,          // the LLM used, for LLM modes
            List<DebriefItem> items) {
    }

    public record DomainOption(String code, String name) {
    }

    public record StudyInfo(int taskCount, int domainCount, int estimatedMinutes, boolean demoModeEnabled) {
    }
}
