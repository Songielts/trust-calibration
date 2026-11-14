package com.trustcal.controller;

import com.trustcal.config.ApiException;
import com.trustcal.config.WebConfig;
import com.trustcal.dto.AdminDtos.*;
import com.trustcal.service.AnalyticsService;
import com.trustcal.service.LlmAgentService;
import com.trustcal.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Researcher endpoints, protected by the X-Admin-Token header (see WebConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AnalyticsService analyticsService;
    private final SimulationService simulationService;
    private final LlmAgentService llmAgentService;
    private final WebConfig webConfig;

    public AdminController(AnalyticsService analyticsService, SimulationService simulationService,
                           LlmAgentService llmAgentService, WebConfig webConfig) {
        this.analyticsService = analyticsService;
        this.simulationService = simulationService;
        this.llmAgentService = llmAgentService;
        this.webConfig = webConfig;
    }

    @PostMapping("/login")
    public Map<String, Boolean> login(@Valid @RequestBody LoginRequest request) {
        if (!webConfig.isValidAdminToken(request.token())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect admin token");
        }
        return Map.of("ok", true);
    }

    @GetMapping("/report")
    public AnalyticsReport report(@RequestParam(defaultValue = "false") boolean includeSimulated,
                                  @RequestParam(defaultValue = "ALL") String agentMode) {
        return analyticsService.report(includeSimulated, agentMode);
    }

    @GetMapping("/participants")
    public List<ParticipantRow> participants(@RequestParam(defaultValue = "false") boolean includeSimulated) {
        return analyticsService.participantRows(includeSimulated);
    }

    @GetMapping("/tasks")
    public List<TaskRow> tasks() {
        return analyticsService.taskRows();
    }

    @GetMapping("/domains")
    public List<DomainRow> domains() {
        return analyticsService.domainRows();
    }

    @GetMapping("/export/trials.csv")
    public ResponseEntity<byte[]> exportTrials(@RequestParam(defaultValue = "false") boolean includeSimulated) {
        return csv("trials.csv", analyticsService.trialsCsv(includeSimulated));
    }

    @GetMapping("/export/participants.csv")
    public ResponseEntity<byte[]> exportParticipants(@RequestParam(defaultValue = "false") boolean includeSimulated) {
        return csv("participants.csv", analyticsService.participantsCsv(includeSimulated));
    }

    @PostMapping("/simulate")
    public SimulationResult simulate(@RequestParam(defaultValue = "40") int count) {
        if (count < 1 || count > 500) {
            throw ApiException.badRequest("count must be between 1 and 500");
        }
        int n = simulationService.simulate(count);
        return new SimulationResult(n, "Simulated participants are synthetic demo data, not study results.");
    }

    @DeleteMapping("/simulated")
    public SimulationResult deleteSimulated() {
        return new SimulationResult(simulationService.deleteSimulated(), "Simulated participants deleted.");
    }

    // ------------------------------------------------------------------ LLM agent

    @GetMapping("/llm/status")
    public AgentStatus llmStatus() {
        return llmAgentService.status();
    }

    @PutMapping("/llm/mode")
    public AgentStatus setMode(@RequestBody ModeRequest request) {
        return llmAgentService.setMode(request.mode());
    }

    @PostMapping("/llm/test")
    public TestCallResult testCall(@RequestParam(required = false) Long taskId) {
        return llmAgentService.testCall(taskId);
    }

    @PostMapping("/llm/runs")
    public RunSummary startRun(@RequestParam(defaultValue = "3") int repetitions) {
        return llmAgentService.startRun(repetitions);
    }

    @GetMapping("/llm/runs")
    public List<RunSummary> runs() {
        return llmAgentService.runs();
    }

    @GetMapping("/llm/runs/{id}")
    public RunDetail run(@PathVariable Long id) {
        return llmAgentService.runDetail(id);
    }

    @PostMapping("/llm/runs/{id}/freeze")
    public List<FrozenRow> freeze(@PathVariable Long id) {
        return llmAgentService.freeze(id);
    }

    @GetMapping("/llm/frozen")
    public List<FrozenRow> frozen() {
        return llmAgentService.frozenBank();
    }

    @PutMapping("/llm/frozen/{taskId}")
    public List<FrozenRow> chooseFrozen(@PathVariable Long taskId, @RequestBody FreezeRequest request) {
        return llmAgentService.chooseFrozen(taskId, request.adviceId());
    }

    @GetMapping("/export/llm_calls.csv")
    public ResponseEntity<byte[]> exportLlmCalls() {
        return csv("llm_calls.csv", llmAgentService.callsCsv());
    }

    @DeleteMapping("/demo")
    public SimulationResult deleteDemo() {
        return new SimulationResult(simulationService.deleteDemo(), "Demo sessions deleted.");
    }

    private ResponseEntity<byte[]> csv(String filename, String body) {
        // UTF-8 BOM so that Excel opens the file with the right encoding
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(content, 0, out, bom.length, content.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out);
    }
}
