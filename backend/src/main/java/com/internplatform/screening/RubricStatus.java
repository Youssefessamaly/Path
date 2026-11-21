package com.internplatform.screening;

/**
 * Lifecycle of one versioned internship rubric. DRAFT is freely editable and not yet used for
 * screening. ACTIVE is the single version currently used for screening — immutable, so any
 * screening result produced against it stays traceable to exactly what it was. ARCHIVED is a
 * past ACTIVE version, kept (never deleted) for that same traceability after a newer version
 * is activated.
 */
public enum RubricStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
