package com.internplatform.screening.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Default, zero-cost implementation — see DisabledAiEvidenceAnalysisService for the same
 *  rationale applied to rubric drafting. */
@Service
@ConditionalOnProperty(
        prefix = "app.ai.rubric-draft",
        name = "mode",
        havingValue = "disabled",
        matchIfMissing = true
)
public class DisabledAiRubricDraftService implements AiRubricDraftService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AiRubricDraftResult draftFromInstructions(AiRubricDraftRequest request) {
        return AiRubricDraftResult.disabled("AI rubric drafting is disabled");
    }
}
