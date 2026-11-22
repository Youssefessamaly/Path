package com.internplatform.screening.ai.anthropic;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The only component in this codebase that makes a real network call to Anthropic. Never
 * instantiated eagerly in a way that blocks application startup when AI is disabled (no
 * network/credential check happens in the constructor — only at {@link #send}) — see
 * AnthropicEvidenceAnalysisService/AnthropicRubricDraftService, which are the only callers and
 * are themselves conditional on an explicit "anthropic" mode.
 *
 * Never logs the request body, response body, or API key — only structural metadata (status
 * code, elapsed time, attempt count, a generic error class/reason).
 */
@Component
public class HttpAnthropicMessagesClient implements AnthropicMessagesClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAnthropicMessagesClient.class);
    private static final String MESSAGES_PATH = "/v1/messages";

    private final String apiKey;
    private final String baseUrl;
    private final String apiVersion;
    private final int timeoutMs;
    private final int maxRetries;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpAnthropicMessagesClient(
            @Value("${app.ai.provider.api-key:}") String apiKey,
            @Value("${app.ai.provider.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${app.ai.provider.api-version:2023-06-01}") String apiVersion,
            @Value("${app.ai.provider.timeout-ms:8000}") int timeoutMs,
            @Value("${app.ai.provider.max-retries:1}") int maxRetries
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.timeoutMs = timeoutMs;
        this.maxRetries = Math.max(0, maxRetries);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public AnthropicMessageResponse send(AnthropicMessageRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("Anthropic API key is not configured", false);
        }

        String body = buildRequestBody(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + MESSAGES_PATH))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", apiVersion)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        int attempt = 0;
        while (true) {
            attempt++;
            long startedAt = System.currentTimeMillis();
            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                long elapsedMs = System.currentTimeMillis() - startedAt;
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    log.info("Anthropic API call succeeded (attempt {}, {} ms)", attempt, elapsedMs);
                    return parseResponseBody(response.body());
                }

                boolean retryableStatus = status >= 500;
                log.warn("Anthropic API returned status {} (attempt {}, {} ms)", status, attempt, elapsedMs);
                if (retryableStatus && attempt <= maxRetries) {
                    continue;
                }
                throw new AiProviderException("Anthropic API returned status " + status, retryableStatus);

            } catch (IOException ex) {
                long elapsedMs = System.currentTimeMillis() - startedAt;
                log.warn("Anthropic API call failed (attempt {}, {} ms): {}", attempt, elapsedMs, ex.getClass().getSimpleName());
                if (attempt <= maxRetries) {
                    continue;
                }
                throw new AiProviderException("Anthropic API request failed: " + ex.getClass().getSimpleName(), true, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Anthropic API request was interrupted", false, ex);
            }
        }
    }

    private String buildRequestBody(AnthropicMessageRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.model());
        root.put("max_tokens", request.maxOutputTokens());
        root.put("system", request.systemPrompt());

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", request.userMessage());

        ArrayNode tools = root.putArray("tools");
        ObjectNode tool = tools.addObject();
        tool.put("name", request.toolName());
        tool.put("description", request.toolDescription());
        tool.set("input_schema", request.toolInputSchema());

        ObjectNode toolChoice = root.putObject("tool_choice");
        toolChoice.put("type", "tool");
        toolChoice.put("name", request.toolName());

        return root.toString();
    }

    private AnthropicMessageResponse parseResponseBody(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException ex) {
            throw new AiProviderException("Anthropic API returned a malformed response body", false, ex);
        }

        String stopReason = root.path("stop_reason").asText(null);
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("tool_use".equals(block.path("type").asText(null))) {
                    String name = block.path("name").asText(null);
                    JsonNode input = block.path("input");
                    return new AnthropicMessageResponse(true, name, input, stopReason);
                }
            }
        }
        return new AnthropicMessageResponse(false, null, null, stopReason);
    }
}
