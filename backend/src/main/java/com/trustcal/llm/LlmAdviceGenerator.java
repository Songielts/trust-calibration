package com.trustcal.llm;

import com.trustcal.entity.DecisionTask;
import com.trustcal.entity.LlmAdvice;
import com.trustcal.repository.LlmAdviceRepository;
import org.springframework.stereotype.Service;

/**
 * Makes one model call for one task and records everything about it.
 * The model only proposes an answer; this class validates the reply against the prompt
 * specification and the system (not the model) decides whether the answer is correct.
 */
@Service
public class LlmAdviceGenerator {

    private final ChatClient chatClient;
    private final LlmSettings settings;
    private final LlmAdviceRepository adviceRepository;

    public LlmAdviceGenerator(ChatClient chatClient, LlmSettings settings, LlmAdviceRepository adviceRepository) {
        this.chatClient = chatClient;
        this.settings = settings;
        this.adviceRepository = adviceRepository;
    }

    public LlmAdvice generate(DecisionTask task, String domainName, LlmAdvice.Purpose purpose, Long runId) {
        LlmAdvice advice = new LlmAdvice();
        advice.setTask(task);
        advice.setRunId(runId);
        advice.setPurpose(purpose);
        advice.setModel(settings.getModel());
        advice.setPromptVersion(PromptSpec.VERSION);

        long start = System.nanoTime();
        try {
            String raw = chatClient.complete(PromptSpec.messagesFor(task, domainName), settings.getTemperature(), true);
            advice.setRawOutput(raw);
            AdviceParser.ParsedAdvice parsed = AdviceParser.parse(raw, task.getOptions().size());
            advice.setValid(true);
            advice.setAnswerIndex(parsed.answerIndex());
            advice.setReasoningSteps(parsed.reasoningSteps());
            advice.setSelfReportedConfidence(parsed.confidence());
            // The system checks correctness against the answer key; the model's own confidence is not a check.
            advice.setCorrect(task.getCorrectIndex().equals(parsed.answerIndex()));
        } catch (AdviceParser.InvalidOutputException e) {
            advice.setValid(false);
            advice.setValidationError("Invalid output: " + e.getMessage());
            advice.setCorrect(false);
        } catch (LlmException e) {
            advice.setValid(false);
            advice.setValidationError("Call failed: " + e.getMessage());
            advice.setCorrect(false);
        } catch (RuntimeException e) {
            advice.setValid(false);
            advice.setValidationError("Unexpected error: " + e.getClass().getSimpleName());
            advice.setCorrect(false);
        }
        advice.setLatencyMs((System.nanoTime() - start) / 1_000_000);
        return adviceRepository.save(advice);
    }
}
