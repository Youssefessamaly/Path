package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real HTTP transport against a local, in-process JDK HttpServer serving canned
 * responses — a genuine wire-level round trip with a MOCKED Anthropic response, never a real
 * network call to api.anthropic.com. No new test dependency: com.sun.net.httpserver is part of
 * the standard JDK.
 */
class HttpAnthropicMessagesClientTest {

    private HttpServer server;
    private final Deque<CannedResponse> queuedResponses = new ArrayDeque<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();

    private record CannedResponse(int status, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestCount.incrementAndGet();
            lastHeaders.set(Map.of(
                    "x-api-key", firstHeader(exchange, "x-api-key"),
                    "anthropic-version", firstHeader(exchange, "anthropic-version")));

            CannedResponse response;
            synchronized (queuedResponses) {
                response = queuedResponses.isEmpty()
                        ? new CannedResponse(500, "{}")
                        : queuedResponses.pollFirst();
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String firstHeader(com.sun.net.httpserver.HttpExchange exchange, String name) {
        var values = exchange.getRequestHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private HttpAnthropicMessagesClient client(int timeoutMs, int maxRetries) {
        return new HttpAnthropicMessagesClient("test-api-key", baseUrl(), "2023-06-01", timeoutMs, maxRetries);
    }

    private AnthropicMessageRequest sampleRequest() {
        ObjectNode schema = new tools.jackson.databind.ObjectMapper().createObjectNode();
        schema.put("type", "object");
        return new AnthropicMessageRequest(
                "claude-haiku-5-5", 256, "system prompt", "user message",
                "submit_evidence_analysis", "desc", schema);
    }

    @Test
    void sendsTheCorrectHeadersAndPathAndParsesAValidToolUseResponse() {
        queuedResponses.add(new CannedResponse(200, """
                {
                  "stop_reason": "tool_use",
                  "content": [
                    { "type": "tool_use", "name": "submit_evidence_analysis", "input": { "suggestions": [] } }
                  ]
                }
                """));

        AnthropicMessageResponse response = client(5000, 0).send(sampleRequest());

        assertThat(response.toolUseFound()).isTrue();
        assertThat(response.toolName()).isEqualTo("submit_evidence_analysis");
        assertThat(response.toolInput().path("suggestions").isArray()).isTrue();
        assertThat(lastHeaders.get().get("x-api-key")).isEqualTo("test-api-key");
        assertThat(lastHeaders.get().get("anthropic-version")).isEqualTo("2023-06-01");
    }

    @Test
    void returnsToolUseFoundFalseWhenTheModelDidNotCallTheTool() {
        queuedResponses.add(new CannedResponse(200, """
                { "stop_reason": "end_turn", "content": [ { "type": "text", "text": "I cannot help with that." } ] }
                """));

        AnthropicMessageResponse response = client(5000, 0).send(sampleRequest());

        assertThat(response.toolUseFound()).isFalse();
    }

    @Test
    void retriesOnceOnA500ThenSucceeds() {
        queuedResponses.add(new CannedResponse(500, "{}"));
        queuedResponses.add(new CannedResponse(200, """
                { "stop_reason": "tool_use", "content": [ { "type": "tool_use", "name": "submit_evidence_analysis", "input": {} } ] }
                """));

        AnthropicMessageResponse response = client(5000, 1).send(sampleRequest());

        assertThat(response.toolUseFound()).isTrue();
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void exhaustsRetriesAndThrowsARetryableExceptionOnRepeated500s() {
        queuedResponses.add(new CannedResponse(500, "{}"));
        queuedResponses.add(new CannedResponse(500, "{}"));

        assertThatThrownBy(() -> client(5000, 1).send(sampleRequest()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).isRetryable()).isTrue());
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void neverRetriesOnA401AndTheExceptionMessageNeverLeaksTheResponseBody() {
        queuedResponses.add(new CannedResponse(401, "{\"error\":\"super-secret-should-not-leak\"}"));

        assertThatThrownBy(() -> client(5000, 1).send(sampleRequest()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).isRetryable()).isFalse())
                .hasMessageNotContaining("super-secret-should-not-leak");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void throwsImmediatelyWhenNoApiKeyIsConfiguredWithoutMakingAnyRequest() {
        HttpAnthropicMessagesClient noKeyClient =
                new HttpAnthropicMessagesClient("", baseUrl(), "2023-06-01", 5000, 0);

        assertThatThrownBy(() -> noKeyClient.send(sampleRequest()))
                .isInstanceOf(AiProviderException.class);
        assertThat(requestCount.get()).isZero();
    }
}
