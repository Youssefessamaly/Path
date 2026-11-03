package com.internplatform.ats.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renames a stage. Position is changed only via the dedicated reorder endpoint, never here. */
public record TemplateStageUpdateRequest(

        @NotBlank(message = "Stage name is required")
        @Size(max = 80, message = "Stage name must be at most 80 characters")
        String name

) {
}
