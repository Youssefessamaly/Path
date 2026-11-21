package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Partial update payload — every field null means "omitted, leave unchanged," matching the
 * project's established PATCH semantics (see InternshipApplicationQuestionUpdateRequest).
 * There is deliberately no way to clear an already-set skillId/label back to the other via
 * this endpoint — a requirement's fundamental identity (skill-based vs free-text) is not
 * changed in place; delete and recreate it instead.
 */
public record RequirementUpdateRequest(

        RequirementType requirementType,

        @Size(max = 200, message = "Label must be at most 200 characters")
        String label,

        @Min(value = 1, message = "Weight must be between 1 and 100")
        @Max(value = 100, message = "Weight must be between 1 and 100")
        Integer weight,

        EvidenceStrength minimumEvidenceStrength,

        /** Omitted means "leave unchanged"; an explicit (possibly empty) set replaces it. */
        Set<EvidenceSource> acceptedEvidenceSources,

        @Size(max = 1000, message = "Recruiter notes must be at most 1000 characters")
        String recruiterNotes,

        @Min(value = 0, message = "Display order must be zero or greater")
        Integer displayOrder

) {
}
