package com.trustcal.controller;

import com.trustcal.dto.ParticipantDtos.*;
import com.trustcal.service.ExperimentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Participant-facing endpoints. Participants are identified by a random public id (UUID). */
@RestController
@RequestMapping("/api")
public class StudyController {

    private final ExperimentService experimentService;

    public StudyController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        return java.util.Map.of("status", "UP");
    }

    @GetMapping("/study/info")
    public StudyInfo info() {
        return experimentService.studyInfo();
    }

    @GetMapping("/domains")
    public List<DomainOption> domains() {
        return experimentService.domainOptions();
    }

    @PostMapping("/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse register(@Valid @RequestBody DemographicsRequest request) {
        return experimentService.register(request);
    }

    @GetMapping("/participants/{pid}")
    public SessionResponse session(@PathVariable String pid) {
        return experimentService.session(pid);
    }

    @GetMapping("/participants/{pid}/trials/{index}")
    public TrialView trial(@PathVariable String pid, @PathVariable int index) {
        return experimentService.getTrial(pid, index);
    }

    /** Stage 1: lock in the participant's own answer; the response reveals the AI's advice. */
    @PostMapping("/participants/{pid}/trials/{index}/initial")
    public TrialView initial(@PathVariable String pid, @PathVariable int index,
                             @Valid @RequestBody InitialAnswerRequest request) {
        return experimentService.submitInitial(pid, index, request);
    }

    /** Stage 2: final decision, confidence and trust rating after seeing the advice. */
    @PostMapping("/participants/{pid}/trials/{index}/final")
    public FinalAnswerResponse finalAnswer(@PathVariable String pid, @PathVariable int index,
                                           @Valid @RequestBody FinalAnswerRequest request) {
        return experimentService.submitFinal(pid, index, request);
    }

    @PostMapping("/participants/{pid}/survey")
    public DebriefView survey(@PathVariable String pid, @Valid @RequestBody SurveyRequest request) {
        return experimentService.submitSurvey(pid, request);
    }

    @GetMapping("/participants/{pid}/debrief")
    public DebriefView debrief(@PathVariable String pid) {
        return experimentService.debrief(pid);
    }
}
