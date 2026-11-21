package com.internplatform.screening;

/** How important one screening requirement is. Never used to auto-reject — see
 *  InternshipRubricService and ScreeningProfileService javadocs. */
public enum RequirementType {
    REQUIRED,
    PREFERRED,
    BONUS
}
