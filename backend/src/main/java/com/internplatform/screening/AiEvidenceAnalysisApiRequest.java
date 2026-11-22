package com.internplatform.screening;

import java.util.List;
import java.util.UUID;

/**
 * Request body for POST .../screening-reports/{reportId}/ai-evidence-analysis. Entirely
 * optional: omit requirementIds (or pass null) to analyze every requirement the referenced
 * report already marked NEEDS_REVIEW or INSUFFICIENT_EVIDENCE. When present, requirementIds is
 * only ever used to narrow that same server-computed eligible set — see
 * CompanyAiAssistService#analyzeEvidence, which intersects it rather than trusting it outright,
 * so a client can never smuggle in an arbitrary requirement id (from this rubric, another
 * rubric, or one that the deterministic engine already confidently resolved) this way.
 */
public record AiEvidenceAnalysisApiRequest(
        List<UUID> requirementIds
) {
}
