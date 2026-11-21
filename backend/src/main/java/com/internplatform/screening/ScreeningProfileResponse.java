package com.internplatform.screening;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ScreeningProfileResponse(
        UUID id,
        UUID companyProfileId,
        String name,
        String description,
        ScreeningProfileStatus status,
        List<RequirementResponse> requirements,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
