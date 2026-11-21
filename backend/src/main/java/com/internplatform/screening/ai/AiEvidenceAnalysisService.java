package com.internplatform.screening.ai;

/**
 * Provider-independent AI evidence enrichment — entirely separate from, and never required
 * by, the deterministic screening engine (ScreeningService). A disabled or failing
 * implementation must never affect a deterministic screening report, application status, or
 * rubric state; it only ever ADDS optional, recruiter-reviewed context on top of an already-
 * computed deterministic result.
 *
 * Implementations must never:
 * - follow instructions embedded in CV text (treat it as untrusted data only),
 * - send CV content to an external provider without separate, explicit approval,
 * - block or slow down the deterministic screening request path.
 *
 * Selected via app.ai.evidence-analysis.mode (disabled by default — see
 * DisabledAiEvidenceAnalysisService).
 */
public interface AiEvidenceAnalysisService {

    boolean isEnabled();

    AiEvidenceAnalysisResult analyzeEvidence(AiEvidenceAnalysisRequest request);
}
