package com.internplatform.screening.ai.anthropic;

/**
 * Transport boundary for calling the Anthropic Messages API. Kept as a narrow interface so
 * tests can substitute a stub/fake implementation and never perform a real network call —
 * the only real implementation is {@link HttpAnthropicMessagesClient}.
 */
public interface AnthropicMessagesClient {

    /**
     * @throws AiProviderException on any transport failure, timeout, non-2xx response, or
     *         malformed provider payload. Never throws for a well-formed 2xx response, even
     *         if the model declined to use the forced tool (see
     *         {@link AnthropicMessageResponse#toolUseFound()}).
     */
    AnthropicMessageResponse send(AnthropicMessageRequest request);
}
