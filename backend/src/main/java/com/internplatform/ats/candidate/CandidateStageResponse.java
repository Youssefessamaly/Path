package com.internplatform.ats.candidate;

import com.internplatform.internshipapplication.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An application's current ATS tracking, if any, plus enough pipeline context (name, full
 * ordered stage list, next stage) for a UI to render progression without a second call.
 * pipelineId, pipelineName, and the current/next-stage fields are all null together when the
 * application isn't tracked — either no pipeline is assigned to its internship, or it was already decided
 * (ACCEPTED/REJECTED) before any pipeline existed (see AtsApplicationIntakeService — a decided
 * application is deliberately never guessed into a stage). stages is always the internship's
 * full pipeline when one is assigned, even for an untracked application, so a UI can still
 * show "this pipeline has N stages" context — null only when no pipeline is assigned at all.
 * Never confuse an untracked application with a missing/invalid one: this response always
 * exists for any application id the caller is authorized to see.
 */
public record CandidateStageResponse(
        UUID applicationId,
        ApplicationStatus applicationStatus,
        UUID pipelineId,
        /** sourceTemplateName, or "Default Pipeline" when assigned from the hardcoded starter —
         *  the same fallback company-web's existing pipeline-structure UI already uses. */
        String pipelineName,
        List<StageSummaryResponse> stages,
        UUID currentStageId,
        String currentStageName,
        OffsetDateTime enteredCurrentStageAt,
        /** Null when untracked, or when already at the pipeline's final stage. */
        UUID nextStageId,
        String nextStageName
) {
}
