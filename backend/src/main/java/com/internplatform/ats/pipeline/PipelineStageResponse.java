package com.internplatform.ats.pipeline;

import java.util.UUID;

public record PipelineStageResponse(
        UUID id,
        String name,
        int position
) {
}
