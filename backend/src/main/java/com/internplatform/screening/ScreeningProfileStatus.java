package com.internplatform.screening;

/** Whether a reusable screening profile template is currently offered for new rubrics.
 *  Archiving never deletes or affects any rubric already copied from it. */
public enum ScreeningProfileStatus {
    ACTIVE,
    ARCHIVED
}
