package com.internplatform.internprofile;

/**
 * One detected skill plus where/how strongly it was evidenced in a CV. Produced by
 * CvSkillExtractionService#detectSkillEvidence; persisted as InternCvSkillEvidence.
 */
public record SkillEvidence(
        Skill skill,
        EvidenceSource source,
        EvidenceStrength strength,
        /** The CV line the match was found on, trimmed and length-capped. Never the full CV. */
        String textReference
) {
}
