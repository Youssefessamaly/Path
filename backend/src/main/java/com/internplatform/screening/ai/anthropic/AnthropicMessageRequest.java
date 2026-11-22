package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.node.ObjectNode;

/**
 * Provider-agnostic shape of one request to {@link AnthropicMessagesClient}. Wire-format
 * details specific to the Anthropic Messages API (field names, envelope, tool_choice shape)
 * live entirely in {@link HttpAnthropicMessagesClient} — callers only deal with this record.
 *
 * toolInputSchema forces a structured, schema-validated response (requirement: "structured
 * responses and strict server-side schema validation") rather than free-form text the model
 * might format inconsistently.
 */
public record AnthropicMessageRequest(
        String model,
        int maxOutputTokens,
        /** Framing/instructions only — never embeds candidate or recruiter-provided text
         *  as something to "obey." */
        String systemPrompt,
        /** The actual untrusted content (redacted CV text + requirement context, or recruiter
         *  rubric instructions) to analyze. Always treated as data, never as instructions, by
         *  the system prompt's own framing. */
        String userMessage,
        String toolName,
        String toolDescription,
        ObjectNode toolInputSchema
) {
}
