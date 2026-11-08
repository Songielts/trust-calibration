package com.trustcal.service;

import com.trustcal.entity.AgentMode;
import com.trustcal.entity.StudySetting;
import com.trustcal.repository.StudySettingRepository;
import org.springframework.stereotype.Service;

/** Run-time study settings chosen by the researcher on the dashboard. */
@Service
public class StudySettingsService {

    static final String AGENT_MODE = "agent_mode";
    static final String ACTIVE_RUN = "active_evaluation_run";

    private final StudySettingRepository repository;

    public StudySettingsService(StudySettingRepository repository) {
        this.repository = repository;
    }

    public AgentMode getAgentMode() {
        return repository.findById(AGENT_MODE)
                .map(s -> {
                    try {
                        return AgentMode.valueOf(s.getValue());
                    } catch (Exception e) {
                        return AgentMode.SCRIPTED;
                    }
                })
                .orElse(AgentMode.SCRIPTED);
    }

    public void setAgentMode(AgentMode mode) {
        repository.save(new StudySetting(AGENT_MODE, mode.name()));
    }

    /** The evaluation run whose measured accuracy is the capability signal and whose answers are frozen. */
    public Long getActiveRunId() {
        return repository.findById(ACTIVE_RUN)
                .map(StudySetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .map(Long::valueOf)
                .orElse(null);
    }

    public void setActiveRunId(Long runId) {
        repository.save(new StudySetting(ACTIVE_RUN, runId == null ? "" : runId.toString()));
    }
}
