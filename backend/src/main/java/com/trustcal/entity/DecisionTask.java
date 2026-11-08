package com.trustcal.entity;

import com.trustcal.util.StringListConverter;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A multiple-choice decision task. The (simulated) AI agent's answer is fixed per task,
 * so the agent's real accuracy per domain is controlled by the experimenter and can be
 * compared with the accuracy it advertises.
 */
@Entity
@Table(name = "decision_task")
public class DecisionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "domain_code", nullable = false, length = 32)
    private String domainCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Convert(converter = StringListConverter.class)
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private List<String> options = new ArrayList<>();

    /** Index into options of the correct answer. */
    @Column(name = "correct_index", nullable = false)
    private Integer correctIndex;

    /** Index into options recommended by the AI agent. */
    @Column(name = "ai_answer_index", nullable = false)
    private Integer aiAnswerIndex;

    /** Step-by-step reasoning shown in the REASONING conditions. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "reasoning_json", nullable = false, columnDefinition = "TEXT")
    private List<String> reasoningSteps = new ArrayList<>();

    /** Explanation of the correct answer, shown on the debrief page. */
    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "display_order")
    private Integer displayOrder;

    public DecisionTask() {
    }

    public DecisionTask(String code, String domainCode, String prompt, List<String> options,
                        int correctIndex, int aiAnswerIndex, List<String> reasoningSteps,
                        String explanation, int displayOrder) {
        this.code = code;
        this.domainCode = domainCode;
        this.prompt = prompt;
        this.options = new ArrayList<>(options);
        this.correctIndex = correctIndex;
        this.aiAnswerIndex = aiAnswerIndex;
        this.reasoningSteps = new ArrayList<>(reasoningSteps);
        this.explanation = explanation;
        this.displayOrder = displayOrder;
    }

    public boolean isAiCorrect() {
        return correctIndex != null && correctIndex.equals(aiAnswerIndex);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public Integer getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(Integer correctIndex) { this.correctIndex = correctIndex; }
    public Integer getAiAnswerIndex() { return aiAnswerIndex; }
    public void setAiAnswerIndex(Integer aiAnswerIndex) { this.aiAnswerIndex = aiAnswerIndex; }
    public List<String> getReasoningSteps() { return reasoningSteps; }
    public void setReasoningSteps(List<String> reasoningSteps) { this.reasoningSteps = reasoningSteps; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
