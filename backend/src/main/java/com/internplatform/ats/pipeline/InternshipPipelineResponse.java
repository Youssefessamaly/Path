package com.internplatform.ats.pipeline;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InternshipPipelineResponse(
        UUID id,
        UUID internshipId,
        UUID companyProfileId,
        /** Null when this pipeline was assigned from the hardcoded starter rather than a real
         *  company template (see InternshipPipelineService), or if that template was later
         *  removed — sourceTemplateName (below) still records what it was assigned from. */
        UUID sourceTemplateId,
        String sourceTemplateName,
        List<PipelineStageResponse> stages,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
