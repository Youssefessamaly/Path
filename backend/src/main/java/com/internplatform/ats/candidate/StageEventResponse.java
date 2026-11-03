package com.internplatform.ats.candidate;

import java.time.OffsetDateTime;
import java.util.UUID;

/** fromStageId/fromStageName are null for the initial ENTERED event (there is no "from" when
 *  first entering a pipeline). actorMemberId is always null in this phase — every event here
 *  is system-initiated (automatic intake or lazy backfill), never a human move. */
public record StageEventResponse(
        UUID id,
        UUID fromStageId,
        String fromStageName,
        UUID toStageId,
        String toStageName,
        StageEventKind eventKind,
        UUID actorMemberId,
        OffsetDateTime occurredAt
) {
}
