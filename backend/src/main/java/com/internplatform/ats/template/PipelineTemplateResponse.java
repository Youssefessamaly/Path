package com.internplatform.ats.template;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PipelineTemplateResponse(
        UUID id,
        UUID companyProfileId,
        String name,
        String description,
        boolean isDefault,
        boolean archived,
        List<TemplateStageResponse> stages,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
