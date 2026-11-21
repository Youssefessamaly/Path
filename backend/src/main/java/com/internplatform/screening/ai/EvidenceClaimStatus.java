package com.internplatform.screening.ai;

/** Outcome of validating one AiSuggestedEvidence claim — see EvidenceClaimValidator. */
public enum EvidenceClaimStatus {
    /** Grounded in the authorized CV text, references a real skill/requirement, and its
     *  claimed strength is fully supported by the cited excerpt's section. */
    ACCEPTED,
    /** Grounded and otherwise valid, but either the model flagged uncertainty itself or its
     *  claimed strength exceeded what the excerpt supports and had to be downgraded — a human
     *  should look before relying on it. */
    ACCEPTED_NEEDS_REVIEW,
    /** Fails a hard check (unknown requirement/skill, no excerpt, or the excerpt cannot be
     *  found in the authorized CV text at all) and must never be shown or persisted as if
     *  it were trustworthy. */
    REJECTED
}
