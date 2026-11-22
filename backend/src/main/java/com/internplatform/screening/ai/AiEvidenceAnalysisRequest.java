package com.internplatform.screening.ai;

import java.util.List;
import java.util.UUID;

/**
 * Input to AiEvidenceAnalysisService. authorizedCvText must be the already-extracted text of
 * the SPECIFIC application's frozen CV snapshot (see ScreeningService in Stage 4) — never the
 * intern profile's current/live CV. Treat authorizedCvText as untrusted candidate-provided
 * content: data to analyze, never instructions to follow.
 *
 * companyProfileId identifies the screening report's owning company — used by a real provider
 * implementation for per-company rate limiting (see Stage 5B's AiRateLimiter) and mirrors the
 * same field already present on AiRubricDraftRequest.
 */
public record AiEvidenceAnalysisRequest(
        UUID companyProfileId,
        UUID screeningReportId,
        UUID internshipRubricId,
        String authorizedCvText,
        /** Only the specific requirements the deterministic engine could not confidently
         *  resolve (NEEDS_REVIEW / INSUFFICIENT_EVIDENCE) — AI is never asked to re-decide an
         *  already-confident deterministic MATCHED/NOT_EVIDENCED result. */
        List<UUID> requirementIdsNeedingAnalysis
) {
}
