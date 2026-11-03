package com.internplatform.ats.candidate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An application's current ATS tracking, if any. All three stage fields are null together when
 * the application isn't tracked — either no pipeline is assigned to its internship, or it was
 * already decided (ACCEPTED/REJECTED) before any pipeline existed (see
 * AtsApplicationIntakeService — a decided application is deliberately never guessed into a
 * stage). Never confuse an untracked application with a missing/invalid one: this response
 * always exists for any application id the caller is authorized to see.
 */
public record CandidateStageResponse(
        UUID applicationId,
        UUID currentStageId,
        String currentStageName,
        OffsetDateTime enteredCurrentStageAt
) {
}
