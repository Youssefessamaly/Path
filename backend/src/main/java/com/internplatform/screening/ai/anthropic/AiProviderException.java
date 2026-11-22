package com.internplatform.screening.ai.anthropic;

/**
 * Thrown by {@link AnthropicMessagesClient} on any transport failure, non-2xx response, or
 * malformed provider payload. The message is always a short, generic, human-safe description
 * (e.g. "Anthropic API returned status 500") — never the request body, response body, prompt
 * content, or API key. Callers (the Anthropic{EvidenceAnalysis,RubricDraft}Service classes)
 * catch this and degrade to a disabled/unavailable result; it must never propagate up to a
 * recruiter-facing endpoint as a 500.
 */
public class AiProviderException extends RuntimeException {

    private final boolean retryable;

    public AiProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AiProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
