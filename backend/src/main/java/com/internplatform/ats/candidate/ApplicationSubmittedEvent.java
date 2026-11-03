package com.internplatform.ats.candidate;

import java.util.UUID;

/**
 * Published by InternshipApplicationService.submitApplication after its transaction has
 * fully persisted the new application — handled by AtsApplicationIntakeService strictly
 * AFTER_COMMIT (see there for why), so a failure here can never affect the student's already-
 * successful submission.
 */
public record ApplicationSubmittedEvent(UUID applicationId, UUID internshipId) {
}
