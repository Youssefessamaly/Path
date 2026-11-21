package com.internplatform.screening;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InternshipRubricResponse(
        UUID id,
        UUID internshipId,
        UUID screeningProfileId,
        int version,
        RubricStatus status,
        List<RequirementResponse> requirements,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime activatedAt
) {
}
