package com.internplatform.screening.ai;

import java.util.List;

/**
 * Output of AiEvidenceAnalysisService. completed=false whenever AI is disabled, unavailable,
 * times out, or returns something unusable — callers must treat that exactly like "no
 * suggestions," never as an error that should affect the already-computed deterministic
 * report (see ScreeningService). suggestions here are RAW model output, not yet validated —
 * always run them through EvidenceClaimValidator before using them for anything.
 */
public record AiEvidenceAnalysisResult(
        boolean completed,
        List<AiSuggestedEvidence> suggestions,
        /** Human-readable reason when completed=false, e.g. "AI evidence analysis is
         *  disabled." Never contains CV content. */
        String unavailableReason
) {
    public static AiEvidenceAnalysisResult disabled(String reason) {
        return new AiEvidenceAnalysisResult(false, List.of(), reason);
    }
}
