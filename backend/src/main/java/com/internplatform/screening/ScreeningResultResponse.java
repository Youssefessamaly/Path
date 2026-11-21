package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;

import java.util.UUID;

public record ScreeningResultResponse(
        UUID requirementId,
        RequirementType requirementType,
        UUID skillId,
        String skillName,
        String label,
        int weight,
        ScreeningResult result,
        EvidenceSource evidenceSource,
        EvidenceStrength evidenceStrength,
        /** Authorized context only — the API never returns this to anyone but the owning
         *  company (see CompanyScreeningController); never the full CV, always the one
         *  matching line, already length-capped by CvSkillExtractionService. */
        String textReference,
        String reasoning
) {
}
