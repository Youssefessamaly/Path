package com.internplatform.screening.ai;

/**
 * Provider-independent natural-language rubric drafting (English or Arabic recruiter
 * instructions). Never writes to any ScreeningProfile or InternshipRubric directly — see
 * AiRubricDraftResult. Selected via app.ai.rubric-draft.mode (disabled by default — see
 * DisabledAiRubricDraftService).
 */
public interface AiRubricDraftService {

    boolean isEnabled();

    AiRubricDraftResult draftFromInstructions(AiRubricDraftRequest request);
}
