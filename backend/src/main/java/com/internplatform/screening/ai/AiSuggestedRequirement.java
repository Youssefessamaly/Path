package com.internplatform.screening.ai;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.screening.RequirementType;

import java.util.Set;
import java.util.UUID;

/**
 * One AI-proposed requirement for a rubric draft — raw model output, never persisted as an
 * actual rubric requirement directly. Turning this into a real requirement always goes
 * through the existing, unmodified Stage 3 endpoints (ScreeningProfileService /
 * InternshipRubricService), which independently re-validate canonicalSkillId against the
 * real catalog — this type carries no authority of its own.
 */
public record AiSuggestedRequirement(
        RequirementType requirementType,
        /** Null when the AI could not map this to a canonical skill — must then be treated as
         *  a free-text label candidate, never silently dropped or silently invented as a
         *  "mandatory qualification." */
        UUID canonicalSkillId,
        String label,
        Integer suggestedWeight,
        Set<EvidenceSource> suggestedAcceptedEvidenceSources,
        EvidenceStrength suggestedMinimumEvidenceStrength
) {
}
