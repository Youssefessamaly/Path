package com.internplatform.screening;

import jakarta.validation.constraints.Size;

/** Partial update — null means omitted. Status changes go through the dedicated
 *  activate/archive endpoints instead, matching the project's closeInternship convention. */
public record ScreeningProfileUpdateRequest(

        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description

) {
}
