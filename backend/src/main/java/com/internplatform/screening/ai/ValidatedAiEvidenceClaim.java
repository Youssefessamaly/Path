package com.internplatform.screening.ai;

import com.internplatform.internprofile.EvidenceStrength;

/**
 * The result of running one AiSuggestedEvidence through EvidenceClaimValidator. Nothing
 * downstream should ever read the original, unvalidated claim directly — this is always the
 * record of what is actually safe to persist or show a recruiter.
 */
public record ValidatedAiEvidenceClaim(
        AiSuggestedEvidence originalClaim,
        EvidenceClaimStatus status,
        /** The strength actually supported by evidence — may be lower than
         *  originalClaim.suggestedStrength(). Null when status is REJECTED. */
        EvidenceStrength effectiveStrength,
        /** Always present: why this status was assigned. Never contains CV content beyond
         *  what the recruiter is already authorized to see via the excerpt itself. */
        String note
) {
}
