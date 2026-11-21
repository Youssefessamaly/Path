package com.internplatform.screening.ai;

import java.util.List;

/** completed=false whenever AI is disabled/unavailable — callers must surface "no draft
 *  available" rather than inventing one. A completed draft is still only ever a PROPOSAL: it
 *  must remain pending until a recruiter explicitly reviews and accepts it through the
 *  existing, unmodified Stage 3 rubric/profile endpoints — this type itself has no power to
 *  create, activate, or modify any ScreeningProfile or InternshipRubric. */
public record AiRubricDraftResult(
        boolean completed,
        List<AiSuggestedRequirement> suggestedRequirements,
        String unavailableReason
) {
    public static AiRubricDraftResult disabled(String reason) {
        return new AiRubricDraftResult(false, List.of(), reason);
    }
}
