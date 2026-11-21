package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Shared create payload for both a ScreeningProfile's and an InternshipRubric's requirements —
 * the two are identical in shape. Exactly one of skillId/label must be present; validated in
 * RequirementValidationSupport rather than here, since "skill must exist in the catalog" needs
 * a repository lookup that a field annotation can't express.
 */
public record RequirementCreateRequest(

        @NotNull(message = "Requirement type is required")
        RequirementType requirementType,

        UUID skillId,

        @Size(max = 200, message = "Label must be at most 200 characters")
        String label,

        @NotNull(message = "Weight is required")
        @Min(value = 1, message = "Weight must be between 1 and 100")
        @Max(value = 100, message = "Weight must be between 1 and 100")
        Integer weight,

        EvidenceStrength minimumEvidenceStrength,

        /** Null/omitted means "no restriction — any evidence source accepted." */
        Set<EvidenceSource> acceptedEvidenceSources,

        @Size(max = 1000, message = "Recruiter notes must be at most 1000 characters")
        String recruiterNotes,

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order must be zero or greater")
        Integer displayOrder

) {
}
