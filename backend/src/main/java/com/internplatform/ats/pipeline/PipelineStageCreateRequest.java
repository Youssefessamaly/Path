package com.internplatform.ats.pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineStageCreateRequest(

        @NotBlank(message = "Stage name is required")
        @Size(max = 80, message = "Stage name must be at most 80 characters")
        String name

) {
}
