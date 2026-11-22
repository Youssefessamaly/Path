package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceStrength;

import java.util.UUID;

/**
 * One AI-suggested piece of supporting evidence for one requirement, already validated by
 * EvidenceClaimValidator (see CompanyAiAssistService — a REJECTED claim never reaches this
 * response at all). Mirrors ScreeningResultResponse's requirement-display fields (requirementId,
 * requirementType, skillId, skillName, label, weight) for a consistent recruiter-facing shape,
 * plus the AI-specific fields. This is advisory context only: nothing here is, or ever becomes,
 * a ScreeningReportResult row — see requirement 11/12 in the Stage 5B Phase C1 spec.
 */
public record AiEvidenceSuggestionResponse(
        UUID requirementId,
        RequirementType requirementType,
        UUID skillId,
        String skillName,
        String label,
        int weight,
        String excerptQuote,
        EvidenceStrength strength,
        /** True when EvidenceClaimValidator downgraded an overclaimed strength, or the model
         *  itself flagged low confidence — a recruiter should look at this one personally
         *  rather than take it at face value. */
        boolean uncertain,
        String explanation
) {
}
