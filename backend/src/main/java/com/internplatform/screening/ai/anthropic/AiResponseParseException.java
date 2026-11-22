package com.internplatform.screening.ai.anthropic;

/**
 * Thrown by {@link AiResponseParser} when the model's tool call is missing, uses the wrong
 * tool, or its structured input does not match the expected shape well enough to safely
 * convert. Always a short, generic message — never includes the raw provider payload (which
 * may contain candidate CV excerpts). Callers degrade to a disabled/unavailable result.
 */
public class AiResponseParseException extends RuntimeException {

    public AiResponseParseException(String message) {
        super(message);
    }
}
