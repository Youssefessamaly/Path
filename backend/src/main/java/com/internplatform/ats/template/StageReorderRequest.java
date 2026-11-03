package com.internplatform.ats.template;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** The complete new stage order, by id. Must be an exact permutation of the parent's current
 *  stage ids — the service rejects anything that adds, drops, or duplicates a stage id, rather
 *  than trying to guess intent from a partial list. */
public record StageReorderRequest(

        @NotEmpty(message = "stageIds is required")
        List<UUID> stageIds

) {
}
