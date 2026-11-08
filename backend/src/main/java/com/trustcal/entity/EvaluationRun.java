package com.trustcal.entity;

import com.trustcal.util.IntegerMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One run of the fixed evaluation loop: every task is sent to the LLM {@code repetitions} times
 * with the same prompt version, and the system scores each answer against the answer key.
 * The run history doubles as the prompt log (version, settings, score).
 */
@Entity
@Table(name = "evaluation_run")
public class EvaluationRun {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(nullable = false)
    private Double temperature;

    @Column(nullable = false)
    private Integer repetitions;

    @Column(name = "total_calls", nullable = false)
    private Integer totalCalls;

    @Column(name = "valid_calls")
    private Integer validCalls;

    @Column(name = "correct_calls")
    private Integer correctCalls;

    /** Domain code -> measured accuracy in percent (correct / attempted, invalid counts as wrong). */
    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "domain_accuracy", columnDefinition = "TEXT")
    private Map<String, Integer> domainAccuracy = new LinkedHashMap<>();

    /** Domain code -> number of attempted calls. */
    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "domain_attempts", columnDefinition = "TEXT")
    private Map<String, Integer> domainAttempts = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RUNNING;

    @Column(length = 500)
    private String note;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getRepetitions() { return repetitions; }
    public void setRepetitions(Integer repetitions) { this.repetitions = repetitions; }
    public Integer getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Integer totalCalls) { this.totalCalls = totalCalls; }
    public Integer getValidCalls() { return validCalls; }
    public void setValidCalls(Integer validCalls) { this.validCalls = validCalls; }
    public Integer getCorrectCalls() { return correctCalls; }
    public void setCorrectCalls(Integer correctCalls) { this.correctCalls = correctCalls; }
    public Map<String, Integer> getDomainAccuracy() { return domainAccuracy; }
    public void setDomainAccuracy(Map<String, Integer> domainAccuracy) { this.domainAccuracy = domainAccuracy; }
    public Map<String, Integer> getDomainAttempts() { return domainAttempts; }
    public void setDomainAttempts(Map<String, Integer> domainAttempts) { this.domainAttempts = domainAttempts; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
