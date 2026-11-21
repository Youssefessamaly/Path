package com.internplatform.screening.ai;

import com.internplatform.internprofile.EvidenceStrength;

import java.util.UUID;

/**
 * One AI-proposed piece of supporting evidence for one rubric requirement, as emitted by an
 * AiEvidenceAnalysisService implementation — BEFORE validation. Never persisted or shown to a
 * recruiter directly; always passed through EvidenceClaimValidator first, which may downgrade
 * suggestedStrength or reject the claim outright (see ValidatedAiEvidenceClaim).
 */
public record AiSuggestedEvidence(
        UUID requirementId,
        /** Null when the AI could not map this to a canonical skill. */
        UUID canonicalSkillId,
        /** The exact text the model claims supports this evidence — must be checked against
         *  the authorized CV text verbatim; never trusted as-is. */
        String excerptQuote,
        EvidenceStrength suggestedStrength,
        /** True when the model itself flagged low confidence. Independent of, and in addition
         *  to, whatever EvidenceClaimValidator determines. */
        boolean uncertain,
        String explanation
) {
}
