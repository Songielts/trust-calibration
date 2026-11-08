package com.trustcal.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Connection settings for the LLM. The API key is read from the environment
 * (LLM_API_KEY or DEEPSEEK_API_KEY) and is never written to the database, logs or API responses.
 */
@Component
public class LlmSettings {

    @Value("${llm.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${llm.model:deepseek-chat}")
    private String model;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.temperature:0.7}")
    private double temperature;

    @Value("${llm.timeout-seconds:45}")
    private int timeoutSeconds;

    @Value("${llm.max-tokens:600}")
    private int maxTokens;

    @Value("${llm.evaluation-threads:4}")
    private int evaluationThreads;

    public LlmSettings() {
    }

    /** For tests. */
    public LlmSettings(String baseUrl, String model, String apiKey, double temperature) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.timeoutSeconds = 45;
        this.maxTokens = 600;
        this.evaluationThreads = 2;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public String getApiKey() { return apiKey; }
    public double getTemperature() { return temperature; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxTokens() { return maxTokens; }
    public int getEvaluationThreads() { return Math.max(1, evaluationThreads); }
}
