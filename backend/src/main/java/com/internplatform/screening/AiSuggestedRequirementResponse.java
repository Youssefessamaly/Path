package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;

import java.util.Set;
import java.util.UUID;

/**
 * One AI-drafted requirement suggestion — never a real ScreeningProfileRequirement or
 * InternshipRubricRequirement, and this response carries no power to create one (see
 * CompanyAiAssistService#draftRubric). A recruiter who wants to use a suggestion still goes
 * through the existing, unmodified POST .../rubric/requirements (or screening-profiles
 * equivalent) endpoint by hand, which independently re-validates resolvedSkillId against the
 * real catalog regardless of what's asserted here.
 */
public record AiSuggestedRequirementResponse(
        RequirementType requirementType,
        /** Non-null only when this exact label matched a real catalog skill by exact
         *  (case-insensitive) name — see CompanyAiAssistService#resolveSkillByExactName. */
        UUID resolvedSkillId,
        String resolvedSkillName,
        /** Always present even when resolvedSkillId is set, so the UI never needs to re-derive
         *  a display label from a skill id lookup of its own. */
        String label,
        Integer suggestedWeight,
        Set<EvidenceSource> suggestedAcceptedEvidenceSources,
        EvidenceStrength suggestedMinimumEvidenceStrength,
        /** False means: free-text only, no confident catalog match — the recruiter must either
         *  pick a real skill themselves or keep this as a free-text requirement. */
        boolean skillResolved
) {
}
