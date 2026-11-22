package com.internplatform.ats.candidate;

import java.util.UUID;

/** One stage in a pipeline's ordered sequence — used to render "pipeline progression" without
 *  the client ever needing to call the separate pipeline-structure endpoint just to show it
 *  alongside a candidate's current position. */
public record StageSummaryResponse(
        UUID id,
        String name,
        int position
) {
}
