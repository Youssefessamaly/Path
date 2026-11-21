package com.internplatform.screening;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Bounded batch screening request — explicit, recruiter-initiated, never automatic. */
public record ScreeningBatchRequest(

        @NotEmpty(message = "At least one application id is required")
        @Size(max = 25, message = "At most 25 applications can be screened in a single batch")
        List<UUID> applicationIds

) {
}
