package com.internplatform.screening.ai;

import java.util.UUID;

/** instructionsText is recruiter-authored (English or Arabic), not candidate data — still
 *  treated as untrusted free text by any implementation, never as executable instructions
 *  beyond "draft a rubric from this description." */
public record AiRubricDraftRequest(
        UUID companyProfileId,
        String instructionsText
) {
}
