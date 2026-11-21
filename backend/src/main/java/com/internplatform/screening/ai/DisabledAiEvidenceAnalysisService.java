package com.internplatform.screening.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * The default, zero-cost, zero-risk implementation: no network calls, no model, nothing sent
 * anywhere. Active whenever app.ai.evidence-analysis.mode is "disabled" or unset — this is
 * what ships in Stage 5A. A real provider implementation (local or hosted) would instead be
 * conditional on a different value of the same property, so the deterministic engine's
 * dependency on this interface is always satisfiable and swapping providers is a config
 * change, never a code change to any caller.
 */
@Service
@ConditionalOnProperty(
        prefix = "app.ai.evidence-analysis",
        name = "mode",
        havingValue = "disabled",
        matchIfMissing = true
)
public class DisabledAiEvidenceAnalysisService implements AiEvidenceAnalysisService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AiEvidenceAnalysisResult analyzeEvidence(AiEvidenceAnalysisRequest request) {
        return AiEvidenceAnalysisResult.disabled("AI evidence analysis is disabled");
    }
}
