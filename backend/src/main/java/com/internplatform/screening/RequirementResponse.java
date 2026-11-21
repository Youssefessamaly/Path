package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        RequirementType requirementType,
        UUID skillId,
        /** Null when skillId is null, or (defensively) if the referenced skill no longer
         *  resolves. Never fabricated — always the catalog skill's own current name. */
        String skillName,
        String label,
        int weight,
        EvidenceStrength minimumEvidenceStrength,
        Set<EvidenceSource> acceptedEvidenceSources,
        String recruiterNotes,
        int displayOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
