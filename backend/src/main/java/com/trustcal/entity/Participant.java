package com.trustcal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "participant")
public class Participant {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Random identifier given to the browser; the numeric id is never exposed to participants. */
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "experiment_condition", nullable = false, length = 32)
    private ExperimentCondition condition;

    @Column(name = "age_group", length = 20)
    private String ageGroup;

    @Column(length = 30)
    private String gender;

    @Column(length = 50)
    private String education;

    /** Self-rated familiarity with AI assistants, 1-7. */
    @Column(name = "ai_familiarity")
    private Integer aiFamiliarity;

    @Column(name = "ai_usage_frequency", length = 30)
    private String aiUsageFrequency;

    /** Self-rated general propensity to trust technology, 1-7. */
    @Column(name = "trust_propensity")
    private Integer trustPropensity;

    /** Randomised order of task ids, comma separated. */
    @Column(name = "task_order", nullable = false, length = 500)
    private String taskOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    /** True for synthetic participants created from the admin dashboard for demos. */
    @Column(nullable = false)
    private boolean simulated = false;

    /**
     * True when the participant chose the condition through a demo link (?demo=CONDITION).
     * Demo sessions are for presenting the interface and are excluded from the analysis by default.
     */
    @Column(nullable = false)
    private boolean demo = false;

    /** Where this participant's advice comes from (fixed at registration). Null in old rows = SCRIPTED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_mode", length = 20)
    private AgentMode agentMode = AgentMode.SCRIPTED;

    /** For LLM modes: the evaluation run that supplied the capability signal and frozen answers. */
    @Column(name = "evaluation_run_id")
    private Long evaluationRunId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public List<Long> getTaskOrderList() {
        if (taskOrder == null || taskOrder.isBlank()) {
            return List.of();
        }
        return Arrays.stream(taskOrder.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    public void setTaskOrderList(List<Long> ids) {
        this.taskOrder = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public ExperimentCondition getCondition() { return condition; }
    public void setCondition(ExperimentCondition condition) { this.condition = condition; }
    public String getAgeGroup() { return ageGroup; }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public Integer getAiFamiliarity() { return aiFamiliarity; }
    public void setAiFamiliarity(Integer aiFamiliarity) { this.aiFamiliarity = aiFamiliarity; }
    public String getAiUsageFrequency() { return aiUsageFrequency; }
    public void setAiUsageFrequency(String aiUsageFrequency) { this.aiUsageFrequency = aiUsageFrequency; }
    public Integer getTrustPropensity() { return trustPropensity; }
    public void setTrustPropensity(Integer trustPropensity) { this.trustPropensity = trustPropensity; }
    public String getTaskOrder() { return taskOrder; }
    public void setTaskOrder(String taskOrder) { this.taskOrder = taskOrder; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }
    public AgentMode getAgentMode() { return agentMode == null ? AgentMode.SCRIPTED : agentMode; }
    public void setAgentMode(AgentMode agentMode) { this.agentMode = agentMode; }
    public Long getEvaluationRunId() { return evaluationRunId; }
    public void setEvaluationRunId(Long evaluationRunId) { this.evaluationRunId = evaluationRunId; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }

    /** Real study data: neither simulated nor a demo session. */
    public boolean isStudyData() { return !simulated && !demo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
