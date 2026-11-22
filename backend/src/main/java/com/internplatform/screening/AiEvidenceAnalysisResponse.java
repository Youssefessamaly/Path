package com.internplatform.screening;

import java.util.List;
import java.util.UUID;

/**
 * Response for POST .../screening-reports/{reportId}/ai-evidence-analysis. completed=false
 * covers every "AI assist isn't available right now" case uniformly — disabled by config,
 * pending privacy review, rate-limited, provider failure/timeout, or no requirement in this
 * report was eligible — always as a normal 200 response, never a 4xx/5xx, exactly mirroring
 * how Stage 5A/5B already model AI unavailability as data rather than an error. The
 * deterministic screening report this analysis was requested for is identified by id/version
 * but is never itself modified by this endpoint.
 */
public record AiEvidenceAnalysisResponse(
        boolean completed,
        UUID screeningReportId,
        UUID internshipRubricId,
        int rubricVersion,
        List<AiEvidenceSuggestionResponse> suggestions,
        String unavailableReason
) {
}
