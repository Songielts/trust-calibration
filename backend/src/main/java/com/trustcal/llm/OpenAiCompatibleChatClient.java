package com.trustcal.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * Calls an OpenAI-compatible /chat/completions endpoint (DeepSeek by default; OpenAI, Qwen and
 * others work by changing llm.base-url and llm.Model). Uses only the JDK HTTP client.
 */
@Component
public class OpenAiCompatibleChatClient implements ChatClient {

    private final LlmSettings settings;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiCompatibleChatClient(LlmSettings settings) {
        this.settings = settings;
    }

    @Override
    public String complete(List<Message> messages, double temperature, boolean jsonMode) throws LlmException {
        if (!settings.isConfigured()) {
            throw new LlmException("No API key configured (set LLM_API_KEY or create backend/local-secrets.yml)");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", settings.getModel());
        body.put("temperature", temperature);
        body.put("max_tokens", settings.getMaxTokens());
        body.put("stream", false);
        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode msgs = body.putArray("messages");
        for (Message m : messages) {
            msgs.addObject().put("role", m.role()).put("content", m.content());
        }

        String url = settings.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + settings.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
        } catch (IOException | IllegalArgumentException e) {
            throw new LlmException("Could not build the request: " + e.getMessage(), e);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException("The model did not answer within " + settings.getTimeoutSeconds() + " s", e);
        } catch (IOException e) {
            String why = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new LlmException("Could not reach " + url + " (" + why + "). Check llm.base-url and your connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new LlmException("HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new LlmException("The model returned an empty reply");
            }
            return content.asText();
        } catch (IOException e) {
            throw new LlmException("Unreadable response: " + shorten(response.body()), e);
        }
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
