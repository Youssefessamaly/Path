package com.internplatform.screening.ai.anthropic;

import java.util.Set;
import java.util.UUID;

/**
 * Everything the model needs to know about ONE requirement it is being asked to analyze —
 * hydrated by AnthropicEvidenceAnalysisService from the real InternshipRubricRequirement/Skill
 * rows before prompting, since the Stage 5A AiEvidenceAnalysisRequest interface deliberately
 * carries only requirement ids. Kept as a plain DTO (not the JPA entity) so AiPromptBuilder
 * stays trivially unit-testable without a persistence context.
 */
public record EvidenceRequirementContext(
        UUID requirementId,
        /** The skill name when skillId is set, otherwise the free-text label. */
        String skillOrLabel,
        String requirementType,
        String minimumEvidenceStrength,
        Set<String> acceptedEvidenceSources
) {
}
