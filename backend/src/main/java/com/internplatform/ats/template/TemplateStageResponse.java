package com.internplatform.ats.template;

import java.util.UUID;

public record TemplateStageResponse(
        UUID id,
        String name,
        int position
) {
}
