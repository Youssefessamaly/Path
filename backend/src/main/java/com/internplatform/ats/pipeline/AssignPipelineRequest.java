package com.internplatform.ats.pipeline;

import java.util.UUID;

/** templateId omitted (null) -> fall back to the company's own default template if one is
 *  marked, else the hardcoded starter (Applied -> Screening -> Interview -> Offer) — see
 *  InternshipPipelineService.assignPipeline. */
public record AssignPipelineRequest(
        UUID templateId
) {
}
