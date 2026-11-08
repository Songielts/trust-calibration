package com.trustcal.service;

import com.trustcal.entity.*;
import com.trustcal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates clearly-flagged synthetic participants so that the researcher dashboard and the
 * analysis pipeline can be demonstrated and tested before real data is collected.
 *
 * IMPORTANT: the behaviour model below is a set of assumptions written by the developer,
 * not empirical findings. Simulated rows are flagged (participant.simulated = true), excluded
 * from the analysis by default, and can be deleted from the dashboard.
 */
@Service
public class SimulationService {

    private final ParticipantRepository participantRepository;
    private final DecisionTaskRepository taskRepository;
    private final DomainProfileRepository domainRepository;
    private final TrialRepository trialRepository;
    private final PostSurveyRepository surveyRepository;
    private final ExperimentService experimentService;

    public SimulationService(ParticipantRepository participantRepository, DecisionTaskRepository taskRepository,
                             DomainProfileRepository domainRepository, TrialRepository trialRepository,
                             PostSurveyRepository surveyRepository, ExperimentService experimentService) {
        this.participantRepository = participantRepository;
        this.taskRepository = taskRepository;
        this.domainRepository = domainRepository;
        this.trialRepository = trialRepository;
        this.surveyRepository = surveyRepository;
        this.experimentService = experimentService;
    }

    @Transactional
    public int simulate(int count) {
        Random rnd = new Random();
        Map<Long, DecisionTask> tasks = taskRepository.findAll().stream()
                .collect(Collectors.toMap(DecisionTask::getId, Function.identity()));
        Map<String, DomainProfile> domains = domainRepository.findAll().stream()
                .collect(Collectors.toMap(DomainProfile::getCode, Function.identity()));
        String[] ages = {"18-24", "25-34", "35-44", "45-54", "55+"};
        String[] edu = {"Secondary school", "Bachelor's degree", "Master's degree", "Doctorate"};
        String[] usage = {"Never", "Monthly", "Weekly", "Daily"};

        for (int i = 0; i < count; i++) {
            ExperimentCondition cond = experimentService.nextBalancedCondition(true);
            Participant p = new Participant();
            p.setPublicId(UUID.randomUUID().toString());
            p.setCondition(cond);
            p.setSimulated(true);
            p.setAgentMode(AgentMode.SCRIPTED);
            p.setAgeGroup(ages[rnd.nextInt(ages.length)]);
            p.setGender(rnd.nextBoolean() ? "Female" : "Male");
            p.setEducation(edu[rnd.nextInt(edu.length)]);
            p.setAiFamiliarity(1 + rnd.nextInt(7));
            p.setAiUsageFrequency(usage[rnd.nextInt(usage.length)]);
            p.setTrustPropensity(2 + rnd.nextInt(5));
            p.setTaskOrderList(experimentService.randomTaskOrder());
            p.setStatus(Participant.Status.COMPLETED);
            p.setCreatedAt(LocalDateTime.now().minusMinutes(20));
            p.setCompletedAt(LocalDateTime.now());
            participantRepository.save(p);

            double skill = 0.45 + rnd.nextDouble() * 0.3;          // probability of a correct initial answer
            double baseFollow = 0.25 + rnd.nextDouble() * 0.35;     // tendency to adopt the AI's answer
            List<Long> order = p.getTaskOrderList();
            for (int idx = 0; idx < order.size(); idx++) {
                DecisionTask task = tasks.get(order.get(idx));
                DomainProfile d = domains.get(task.getDomainCode());
                double signal = (d.getHistoricalAccuracy() - 75) / 100.0; // -0.21 .. +0.17

                int nOpts = task.getOptions().size();
                int initial = rnd.nextDouble() < skill ? task.getCorrectIndex() : randomOther(rnd, nOpts, task.getCorrectIndex());

                double follow = baseFollow
                        + (cond.isShowReasoning() ? 0.15 : 0)                 // assumption: reasoning raises acceptance
                        + (cond.isShowCapability() ? signal * 1.6 : 0);       // assumption: signal modulates by domain
                follow = Math.max(0.02, Math.min(0.95, follow));
                int fin = initial;
                if (initial != task.getAiAnswerIndex() && rnd.nextDouble() < follow) {
                    fin = task.getAiAnswerIndex();
                }
                double trust = 4.2 + rnd.nextGaussian() * 0.9
                        + (cond.isShowReasoning() ? 0.5 : 0)
                        + (cond.isShowCapability() ? signal * 7 : 0)
                        + (initial == task.getAiAnswerIndex() ? 0.8 : -0.4);

                Trial t = new Trial();
                t.setParticipant(p);
                t.setTask(task);
                t.setTrialIndex(idx);
                t.setInitialAnswer(initial);
                t.setInitialConfidence(clamp(55 + (int) (rnd.nextGaussian() * 18), 0, 100));
                t.setInitialTimeMs(9000L + rnd.nextInt(20000));
                t.setFinalAnswer(fin);
                t.setFinalConfidence(clamp(62 + (int) (rnd.nextGaussian() * 16), 0, 100));
                t.setTrustRating(clamp((int) Math.round(trust), 1, 7));
                t.setFinalTimeMs(4000L + rnd.nextInt(15000) + (cond.isShowReasoning() ? 6000L : 0L));
                t.setAiAnswer(task.getAiAnswerIndex());
                t.setAdviceSource(AdviceSource.SCRIPTED);
                t.setAiCorrect(task.isAiCorrect());
                t.setInitialCorrect(initial == task.getCorrectIndex());
                t.setInitialAgree(initial == task.getAiAnswerIndex());
                t.setFinalCorrect(fin == task.getCorrectIndex());
                t.setFinalAgree(fin == task.getAiAnswerIndex());
                t.setChangedAnswer(fin != initial);
                t.setCompleted(true);
                t.setCompletedAt(LocalDateTime.now());
                trialRepository.save(t);
            }

            PostSurvey s = new PostSurvey();
            s.setParticipant(p);
            int base = clamp(4 + (int) Math.round(rnd.nextGaussian() + (cond.isShowReasoning() ? 0.4 : 0)), 1, 7);
            s.setTrustConfident(clamp(base + rnd.nextInt(3) - 1, 1, 7));
            s.setTrustReliable(clamp(base + rnd.nextInt(3) - 1, 1, 7));
            s.setTrustTrustworthy(clamp(base + rnd.nextInt(3) - 1, 1, 7));
            s.setTrustDependable(clamp(base + rnd.nextInt(3) - 1, 1, 7));
            s.setTrustWary(clamp(8 - base + rnd.nextInt(3) - 1, 1, 7));
            s.setEstimatedOverallAccuracy(clamp(70 + (int) (rnd.nextGaussian() * 12), 0, 100));
            Map<String, Integer> est = new LinkedHashMap<>();
            for (DomainProfile d : domains.values()) {
                int guess = cond.isShowCapability()
                        ? (int) Math.round(d.getHistoricalAccuracy() + rnd.nextGaussian() * 8)
                        : (int) Math.round(70 + rnd.nextGaussian() * 14);
                est.put(d.getCode(), clamp(guess, 0, 100));
            }
            s.setEstimatedDomainAccuracy(est);
            s.setInfoUsefulness(clamp(4 + rnd.nextInt(3) - 1 + (cond == ExperimentCondition.CONTROL ? -1 : 0), 1, 7));
            if (cond.isShowCapability()) {
                String ans = rnd.nextDouble() < 0.78 ? "A" : (rnd.nextBoolean() ? "B" : "C");
                s.setComprehensionAnswer(ans);
                s.setComprehensionCorrect(ExperimentService.COMPREHENSION_CORRECT.equals(ans));
            }
            s.setComments("[simulated]");
            surveyRepository.save(s);
        }
        return count;
    }

    @Transactional
    public int deleteSimulated() {
        return deleteParticipants(participantRepository.findBySimulatedTrue());
    }

    /** Removes all sessions started through demo links. */
    @Transactional
    public int deleteDemo() {
        return deleteParticipants(participantRepository.findByDemoTrue());
    }

    private int deleteParticipants(List<Participant> participants) {
        if (participants.isEmpty()) return 0;
        surveyRepository.deleteByParticipantIn(participants);
        trialRepository.deleteByParticipantIn(participants);
        participantRepository.deleteAll(participants);
        return participants.size();
    }

    private static int randomOther(Random rnd, int n, int exclude) {
        int v = rnd.nextInt(n - 1);
        return v >= exclude ? v + 1 : v;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
