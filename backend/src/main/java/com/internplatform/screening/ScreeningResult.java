package com.internplatform.screening;

/**
 * The deterministic outcome of evaluating one candidate against one requirement — see
 * ScreeningService. Never used to automatically accept/reject an application; purely
 * informational for a recruiter.
 */
public enum ScreeningResult {
    /** Qualifying evidence was found — meets the requirement's source/strength bar. */
    MATCHED,
    /** No evidence at all: not CV-detected and not manually selected. A confident negative,
     *  only ever assigned when the CV text itself was readable. */
    NOT_EVIDENCED,
    /** Some evidence exists but falls short of what the requirement demands — e.g. only a
     *  bare skills-list mention when project/work evidence was required, evidence weaker
     *  than the configured minimum strength, or only a self-declared manual skill when the
     *  requirement restricts accepted evidence sources. */
    INSUFFICIENT_EVIDENCE,
    /** The result cannot be determined automatically — the CV text was unavailable/unreadable
     *  (so absence of evidence cannot be trusted), or this is a free-text requirement with no
     *  canonical skill to check against candidate data at all. A human must look. */
    NEEDS_REVIEW
}
