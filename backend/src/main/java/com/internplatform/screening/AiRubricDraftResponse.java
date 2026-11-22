package com.internplatform.screening;

import java.util.List;

/**
 * Response for POST .../rubric/ai-draft. completed=false covers disabled-by-config,
 * rate-limited, provider failure/timeout, and blank-instructions cases uniformly, always as a
 * normal 200 response. Never touches any ScreeningProfile/InternshipRubric row, ACTIVE or
 * otherwise — see CompanyAiAssistService#draftRubric.
 */
public record AiRubricDraftResponse(
        boolean completed,
        List<AiSuggestedRequirementResponse> suggestedRequirements,
        String unavailableReason
) {
}
