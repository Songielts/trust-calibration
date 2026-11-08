package com.trustcal.entity;

import com.trustcal.util.StringListConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One call to the LLM for one task, with its raw output, the result of validation and the
 * system's own correctness check against the answer key. Every call is logged, valid or not.
 */
@Entity
@Table(name = "llm_advice")
public class LlmAdvice {

    public enum Purpose { EVALUATION, LIVE, TEST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private DecisionTask task;

    /** Null for live and test calls. */
    @Column(name = "run_id")
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    /** Passed every checkable success criterion of the prompt specification. */
    @Column(nullable = false)
    private boolean valid;

    @Column(name = "validation_error", length = 500)
    private String validationError;

    @Column(name = "answer_index")
    private Integer answerIndex;

    @Convert(converter = StringListConverter.class)
    @Column(name = "reasoning_json", columnDefinition = "TEXT")
    private List<String> reasoningSteps = new ArrayList<>();

    /** The model's self-reported confidence. Logged for analysis only, never shown as a capability signal. */
    @Column(name = "self_reported_confidence")
    private Integer selfReportedConfidence;

    /** Set by the system by comparing answerIndex with the answer key (false when invalid). */
    @Column(nullable = false)
    private boolean correct;

    @Column(name = "raw_output", columnDefinition = "TEXT")
    private String rawOutput;

    @Column(name = "latency_ms")
    private Long latencyMs;

    /** True for the one answer per task that the researcher froze for LLM_FROZEN mode. */
    @Column(nullable = false)
    private boolean frozen = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DecisionTask getTask() { return task; }
    public void setTask(DecisionTask task) { this.task = task; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getValidationError() { return validationError; }
    public void setValidationError(String validationError) { this.validationError = validationError; }
    public Integer getAnswerIndex() { return answerIndex; }
    public void setAnswerIndex(Integer answerIndex) { this.answerIndex = answerIndex; }
    public List<String> getReasoningSteps() { return reasoningSteps; }
    public void setReasoningSteps(List<String> reasoningSteps) { this.reasoningSteps = reasoningSteps; }
    public Integer getSelfReportedConfidence() { return selfReportedConfidence; }
    public void setSelfReportedConfidence(Integer selfReportedConfidence) { this.selfReportedConfidence = selfReportedConfidence; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
