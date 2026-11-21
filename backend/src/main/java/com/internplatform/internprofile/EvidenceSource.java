package com.internplatform.internprofile;

/**
 * Where in a CV a detected skill's supporting evidence was found. UNKNOWN means detection
 * could not confidently place the mention under any recognized section — never guessed.
 */
public enum EvidenceSource {
    SKILLS_LIST,
    PROJECT,
    WORK_EXPERIENCE,
    INTERNSHIP,
    COURSEWORK,
    CERTIFICATION,
    RESEARCH,
    UNKNOWN
}
