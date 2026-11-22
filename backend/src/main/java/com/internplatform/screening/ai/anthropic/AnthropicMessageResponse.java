package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.JsonNode;

/**
 * Provider-agnostic shape of a successful {@link AnthropicMessagesClient} response, already
 * unwrapped from the Anthropic API's response envelope by {@link HttpAnthropicMessagesClient}.
 * toolUseFound=false means the model did not call the forced tool (e.g. it refused, or replied
 * with plain text) — callers must treat this exactly like a provider error, never guess at
 * intent from stopReason alone.
 */
public record AnthropicMessageResponse(
        boolean toolUseFound,
        String toolName,
        /** The tool call's structured "input" object, already parsed as JSON — never raw
         *  provider text. Null when toolUseFound is false. */
        JsonNode toolInput,
        String stopReason
) {
}
