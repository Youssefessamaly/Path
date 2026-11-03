package com.internplatform.ats.pipeline;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** The complete new stage order, by id. Must be an exact permutation of the pipeline's current
 *  stage ids. */
public record PipelineStageReorderRequest(

        @NotEmpty(message = "stageIds is required")
        List<UUID> stageIds

) {
}
