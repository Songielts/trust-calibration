package com.trustcal.service;

import com.trustcal.dto.ParticipantDtos.CapabilityView;
import com.trustcal.entity.*;
import com.trustcal.llm.LlmAdviceGenerator;
import com.trustcal.repository.EvaluationRunRepository;
import com.trustcal.repository.LlmAdviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Decides what advice a participant sees, depending on the agent mode they were assigned:
 * SCRIPTED (researcher-written), LLM_FROZEN (reviewed LLM answers) or LLM_LIVE (a new call).
 * Whatever happens, the participant always gets an answer: failed or invalid LLM output falls
 * back to the frozen answer and then to the scripted one, and the fallback is recorded.
 */
@Service
public class AdviceProvider {

    public record ServedAdvice(int answerIndex, AdviceSource source, Long llmAdviceId) {
    }

    private final LlmAdviceGenerator generator;
    private final LlmAdviceRepository adviceRepository;
    private final EvaluationRunRepository runRepository;

    public AdviceProvider(LlmAdviceGenerator generator, LlmAdviceRepository adviceRepository,
                          EvaluationRunRepository runRepository) {
        this.generator = generator;
        this.adviceRepository = adviceRepository;
        this.runRepository = runRepository;
    }

    /** May call the LLM (LLM_LIVE), so it must not run inside a database transaction. */
    public ServedAdvice resolve(Participant participant, DecisionTask task, String domainName) {
        AgentMode mode = participant.getAgentMode();
        if (mode == AgentMode.LLM_LIVE) {
            LlmAdvice live = generator.generate(task, domainName, LlmAdvice.Purpose.LIVE, null);
            if (live.isValid()) {
                return new ServedAdvice(live.getAnswerIndex(), AdviceSource.LLM_LIVE, live.getId());
            }
            return frozenOrScripted(task, AdviceSource.FALLBACK_FROZEN);
        }
        if (mode == AgentMode.LLM_FROZEN) {
            return frozenOrScripted(task, AdviceSource.LLM_FROZEN);
        }
        return new ServedAdvice(task.getAiAnswerIndex(), AdviceSource.SCRIPTED, null);
    }

    private ServedAdvice frozenOrScripted(DecisionTask task, AdviceSource frozenSource) {
        Optional<LlmAdvice> frozen = adviceRepository.findFirstByTaskAndFrozenTrue(task);
        if (frozen.isPresent() && frozen.get().isValid()) {
            return new ServedAdvice(frozen.get().getAnswerIndex(), frozenSource, frozen.get().getId());
        }
        return new ServedAdvice(task.getAiAnswerIndex(), AdviceSource.FALLBACK_SCRIPTED, null);
    }

    /** The reasoning that goes with the advice actually served on a trial. */
    public List<String> reasoningFor(Long llmAdviceId, List<String> scriptedReasoning) {
        if (llmAdviceId != null) {
            Optional<LlmAdvice> advice = adviceRepository.findById(llmAdviceId);
            if (advice.isPresent()) {
                return advice.get().getReasoningSteps();
            }
        }
        return scriptedReasoning;
    }

    /**
     * The capability signal. For the scripted agent it is the advertised figure from the study
     * materials. For the LLM it is the accuracy this system measured in the evaluation run the
     * participant was assigned to, with its date and number of attempts (provenance).
     */
    public CapabilityView capabilityFor(Participant participant, DomainProfile domain) {
        if (participant.getAgentMode().usesLlm() && participant.getEvaluationRunId() != null) {
            Optional<EvaluationRun> run = runRepository.findById(participant.getEvaluationRunId());
            if (run.isPresent() && run.get().getDomainAccuracy().get(domain.getCode()) != null) {
                EvaluationRun r = run.get();
                int attempts = r.getDomainAttempts().getOrDefault(domain.getCode(), 0);
                String date = r.getFinishedAt() == null ? null : r.getFinishedAt().toLocalDate().toString();
                return new CapabilityView(domain.getName(), r.getDomainAccuracy().get(domain.getCode()), attempts,
                        null, null, "MEASURED", date);
            }
        }
        return new CapabilityView(domain.getName(), domain.getHistoricalAccuracy(), domain.getSampleSize(),
                domain.getBenchmarkName(), domain.getBenchmarkScore(), "ADVERTISED", null);
    }
}
