package com.trustcal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One decision made by a participant, recorded in two stages (judge-advisor paradigm):
 * 1) initial answer + confidence before seeing the AI's advice,
 * 2) final answer + confidence + trust rating after seeing it.
 */
@Entity
@Table(name = "trial",
        uniqueConstraints = @UniqueConstraint(name = "uk_trial_participant_task",
                columnNames = {"participant_id", "task_id"}))
public class Trial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private DecisionTask task;

    /** Position of this task in the participant's sequence, 0-based. */
    @Column(name = "trial_index", nullable = false)
    private Integer trialIndex;

    @Column(name = "initial_answer", nullable = false)
    private Integer initialAnswer;

    /** 0-100 */
    @Column(name = "initial_confidence", nullable = false)
    private Integer initialConfidence;

    @Column(name = "initial_time_ms")
    private Long initialTimeMs;

    @Column(name = "final_answer")
    private Integer finalAnswer;

    /** 0-100 */
    @Column(name = "final_confidence")
    private Integer finalConfidence;

    /** Trust in the AI's advice on this question, 1-7. */
    @Column(name = "trust_rating")
    private Integer trustRating;

    @Column(name = "final_time_ms")
    private Long finalTimeMs;

    /** The option the agent recommended on this trial (null in rows created before LLM support). */
    @Column(name = "ai_answer")
    private Integer aiAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "advice_source", length = 20)
    private AdviceSource adviceSource;

    /** The logged LLM call that produced the advice, if any. */
    @Column(name = "llm_advice_id")
    private Long llmAdviceId;

    @Column(name = "ai_correct", nullable = false)
    private Boolean aiCorrect;

    @Column(name = "initial_correct", nullable = false)
    private Boolean initialCorrect;

    @Column(name = "final_correct")
    private Boolean finalCorrect;

    /** Initial answer equalled the AI's advice. */
    @Column(name = "initial_agree", nullable = false)
    private Boolean initialAgree;

    /** Final answer equals the AI's advice. */
    @Column(name = "final_agree")
    private Boolean finalAgree;

    /** Participant changed answer between stage 1 and stage 2. */
    @Column(name = "changed_answer")
    private Boolean changedAnswer;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Participant getParticipant() { return participant; }
    public void setParticipant(Participant participant) { this.participant = participant; }
    public DecisionTask getTask() { return task; }
    public void setTask(DecisionTask task) { this.task = task; }
    public Integer getTrialIndex() { return trialIndex; }
    public void setTrialIndex(Integer trialIndex) { this.trialIndex = trialIndex; }
    public Integer getInitialAnswer() { return initialAnswer; }
    public void setInitialAnswer(Integer initialAnswer) { this.initialAnswer = initialAnswer; }
    public Integer getInitialConfidence() { return initialConfidence; }
    public void setInitialConfidence(Integer initialConfidence) { this.initialConfidence = initialConfidence; }
    public Long getInitialTimeMs() { return initialTimeMs; }
    public void setInitialTimeMs(Long initialTimeMs) { this.initialTimeMs = initialTimeMs; }
    public Integer getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(Integer finalAnswer) { this.finalAnswer = finalAnswer; }
    public Integer getFinalConfidence() { return finalConfidence; }
    public void setFinalConfidence(Integer finalConfidence) { this.finalConfidence = finalConfidence; }
    public Integer getTrustRating() { return trustRating; }
    public void setTrustRating(Integer trustRating) { this.trustRating = trustRating; }
    public Long getFinalTimeMs() { return finalTimeMs; }
    public void setFinalTimeMs(Long finalTimeMs) { this.finalTimeMs = finalTimeMs; }
    public Integer getAiAnswer() { return aiAnswer; }
    public void setAiAnswer(Integer aiAnswer) { this.aiAnswer = aiAnswer; }
    public AdviceSource getAdviceSource() { return adviceSource == null ? AdviceSource.SCRIPTED : adviceSource; }
    public void setAdviceSource(AdviceSource adviceSource) { this.adviceSource = adviceSource; }
    public Long getLlmAdviceId() { return llmAdviceId; }
    public void setLlmAdviceId(Long llmAdviceId) { this.llmAdviceId = llmAdviceId; }

    /** The answer the agent actually recommended on this trial. */
    public int servedAiAnswer() {
        return aiAnswer != null ? aiAnswer : task.getAiAnswerIndex();
    }

    public Boolean getAiCorrect() { return aiCorrect; }
    public void setAiCorrect(Boolean aiCorrect) { this.aiCorrect = aiCorrect; }
    public Boolean getInitialCorrect() { return initialCorrect; }
    public void setInitialCorrect(Boolean initialCorrect) { this.initialCorrect = initialCorrect; }
    public Boolean getFinalCorrect() { return finalCorrect; }
    public void setFinalCorrect(Boolean finalCorrect) { this.finalCorrect = finalCorrect; }
    public Boolean getInitialAgree() { return initialAgree; }
    public void setInitialAgree(Boolean initialAgree) { this.initialAgree = initialAgree; }
    public Boolean getFinalAgree() { return finalAgree; }
    public void setFinalAgree(Boolean finalAgree) { this.finalAgree = finalAgree; }
    public Boolean getChangedAnswer() { return changedAnswer; }
    public void setChangedAnswer(Boolean changedAnswer) { this.changedAnswer = changedAnswer; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
