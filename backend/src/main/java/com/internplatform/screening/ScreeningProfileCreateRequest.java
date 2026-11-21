package com.internplatform.screening;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScreeningProfileCreateRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description

) {
}
