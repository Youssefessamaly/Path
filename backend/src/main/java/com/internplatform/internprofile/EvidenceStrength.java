package com.internplatform.internprofile;

/**
 * How strongly a skill's {@link EvidenceSource} supports actual practical use, as opposed to
 * a bare mention. Derived deterministically from the EvidenceSource — see
 * CvSkillExtractionService#strengthFor — never inferred by AI/ML.
 */
public enum EvidenceStrength {
    /** Demonstrated through work experience, an internship, a project, or research. */
    STRONG,
    /** Formal exposure without demonstrated hands-on use: a certification or coursework. */
    MODERATE,
    /** A bare mention only — a skills list, or text outside any recognized section. */
    WEAK
}
