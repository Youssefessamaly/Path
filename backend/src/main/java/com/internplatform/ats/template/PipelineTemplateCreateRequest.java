package com.internplatform.ats.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineTemplateCreateRequest(

        @NotBlank(message = "Template name is required")
        @Size(max = 120, message = "Template name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description

) {
}
