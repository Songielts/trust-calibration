package com.trustcal.entity;

import com.trustcal.util.IntegerMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Post-task questionnaire: a 5-item trust scale (adapted from Jian, Bisantz &amp; Drury, 2000),
 * the participant's estimate of the agent's accuracy overall and per domain
 * (used to compute calibration error), and a comprehension check of the capability signal.
 */
@Entity
@Table(name = "post_survey")
public class PostSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, unique = true)
    private Participant participant;

    /** "I am confident in the AI agent." 1-7 */
    @Column(name = "trust_confident", nullable = false)
    private Integer trustConfident;

    /** "The AI agent is reliable." 1-7 */
    @Column(name = "trust_reliable", nullable = false)
    private Integer trustReliable;

    /** "I can trust the AI agent." 1-7 */
    @Column(name = "trust_trustworthy", nullable = false)
    private Integer trustTrustworthy;

    /** "The AI agent is dependable." 1-7 */
    @Column(name = "trust_dependable", nullable = false)
    private Integer trustDependable;

    /** "I am wary of the AI agent." 1-7, reverse-scored. */
    @Column(name = "trust_wary", nullable = false)
    private Integer trustWary;

    /** Estimated percentage of the agent's answers that were correct, 0-100. */
    @Column(name = "estimated_overall_accuracy", nullable = false)
    private Integer estimatedOverallAccuracy;

    /** Domain code -> estimated accuracy 0-100. */
    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "estimated_domain_accuracy", columnDefinition = "TEXT")
    private Map<String, Integer> estimatedDomainAccuracy = new LinkedHashMap<>();

    /** How useful the information shown with the advice was for deciding, 1-7. */
    @Column(name = "info_usefulness")
    private Integer infoUsefulness;

    /** Answer to the capability-signal comprehension question (capability conditions only). */
    @Column(name = "comprehension_answer", length = 10)
    private String comprehensionAnswer;

    @Column(name = "comprehension_correct")
    private Boolean comprehensionCorrect;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Mean of the five items with the wary item reversed, 1-7. */
    public double trustScaleMean() {
        return (trustConfident + trustReliable + trustTrustworthy + trustDependable + (8 - trustWary)) / 5.0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Participant getParticipant() { return participant; }
    public void setParticipant(Participant participant) { this.participant = participant; }
    public Integer getTrustConfident() { return trustConfident; }
    public void setTrustConfident(Integer trustConfident) { this.trustConfident = trustConfident; }
    public Integer getTrustReliable() { return trustReliable; }
    public void setTrustReliable(Integer trustReliable) { this.trustReliable = trustReliable; }
    public Integer getTrustTrustworthy() { return trustTrustworthy; }
    public void setTrustTrustworthy(Integer trustTrustworthy) { this.trustTrustworthy = trustTrustworthy; }
    public Integer getTrustDependable() { return trustDependable; }
    public void setTrustDependable(Integer trustDependable) { this.trustDependable = trustDependable; }
    public Integer getTrustWary() { return trustWary; }
    public void setTrustWary(Integer trustWary) { this.trustWary = trustWary; }
    public Integer getEstimatedOverallAccuracy() { return estimatedOverallAccuracy; }
    public void setEstimatedOverallAccuracy(Integer estimatedOverallAccuracy) { this.estimatedOverallAccuracy = estimatedOverallAccuracy; }
    public Map<String, Integer> getEstimatedDomainAccuracy() { return estimatedDomainAccuracy; }
    public void setEstimatedDomainAccuracy(Map<String, Integer> estimatedDomainAccuracy) { this.estimatedDomainAccuracy = estimatedDomainAccuracy; }
    public Integer getInfoUsefulness() { return infoUsefulness; }
    public void setInfoUsefulness(Integer infoUsefulness) { this.infoUsefulness = infoUsefulness; }
    public String getComprehensionAnswer() { return comprehensionAnswer; }
    public void setComprehensionAnswer(String comprehensionAnswer) { this.comprehensionAnswer = comprehensionAnswer; }
    public Boolean getComprehensionCorrect() { return comprehensionCorrect; }
    public void setComprehensionCorrect(Boolean comprehensionCorrect) { this.comprehensionCorrect = comprehensionCorrect; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
