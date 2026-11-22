package com.internplatform.screening;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST .../rubric/ai-draft. instructionsText is the recruiter's own
 * free-text description (English or Arabic) of who they want — never candidate data, but
 * still treated as untrusted input to interpret, not instructions to execute, all the way
 * down into AiPromptBuilder.
 */
public record AiRubricDraftApiRequest(

        @NotBlank(message = "Instructions are required")
        @Size(min = 10, max = 2000, message = "Instructions must be between 10 and 2000 characters")
        String instructionsText

) {
}
