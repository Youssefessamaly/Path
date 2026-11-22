package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.JsonNode;
import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.screening.RequirementType;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.AiSuggestedRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Converts a schema-forced Anthropic tool_use response into our domain suggestion types.
 * Structural problems (wrong/missing tool, missing top-level array) fail the whole response
 * via AiResponseParseException, since that indicates the provider did not follow the forced
 * schema at all. A problem isolated to ONE item (a malformed UUID, an unknown enum value)
 * drops only that item rather than discarding every other, otherwise-valid suggestion — this
 * is schema validation, not content validation; EvidenceClaimValidator still independently
 * checks every surviving item's content afterward.
 *
 * Never logs the parsed content itself (it may contain candidate CV excerpts) — only counts.
 */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    public List<AiSuggestedEvidence> parseEvidenceAnalysis(AnthropicMessageResponse response, String expectedToolName) {
        requireExpectedTool(response, expectedToolName);

        JsonNode suggestionsNode = response.toolInput().path("suggestions");
        if (!suggestionsNode.isArray()) {
            throw new AiResponseParseException("Anthropic response did not include a 'suggestions' array");
        }

        List<AiSuggestedEvidence> results = new ArrayList<>();
        int skipped = 0;
        for (JsonNode item : suggestionsNode) {
            UUID requirementId = parseUuid(item.path("requirementId").asText(null));
            String excerptQuote = item.path("excerptQuote").asText(null);
            if (requirementId == null || excerptQuote == null || excerptQuote.isBlank()) {
                skipped++;
                continue;
            }
            UUID canonicalSkillId = parseUuid(item.path("canonicalSkillId").asText(null));
            EvidenceStrength strength = parseEnum(EvidenceStrength.class, item.path("suggestedStrength").asText(null));
            boolean uncertain = item.path("uncertain").asBoolean(false);
            String explanation = item.path("explanation").asText("");

            results.add(new AiSuggestedEvidence(requirementId, canonicalSkillId, excerptQuote, strength, uncertain, explanation));
        }
        if (skipped > 0) {
            log.info("Dropped {} malformed evidence suggestion item(s) from Anthropic response", skipped);
        }
        return results;
    }

    public List<AiSuggestedRequirement> parseRubricDraft(AnthropicMessageResponse response, String expectedToolName) {
        requireExpectedTool(response, expectedToolName);

        JsonNode requirementsNode = response.toolInput().path("requirements");
        if (!requirementsNode.isArray()) {
            throw new AiResponseParseException("Anthropic response did not include a 'requirements' array");
        }

        List<AiSuggestedRequirement> results = new ArrayList<>();
        int skipped = 0;
        for (JsonNode item : requirementsNode) {
            RequirementType requirementType = parseEnum(RequirementType.class, item.path("requirementType").asText(null));
            if (requirementType == null || !item.has("suggestedWeight")) {
                skipped++;
                continue;
            }
            UUID canonicalSkillId = parseUuid(item.path("canonicalSkillId").asText(null));
            String label = item.path("label").asText(null);
            Integer suggestedWeight = item.path("suggestedWeight").asInt();
            EvidenceStrength minimumStrength = parseEnum(EvidenceStrength.class, item.path("suggestedMinimumEvidenceStrength").asText(null));

            Set<EvidenceSource> sources = new HashSet<>();
            JsonNode sourcesNode = item.path("suggestedAcceptedEvidenceSources");
            if (sourcesNode.isArray()) {
                for (JsonNode sourceNode : sourcesNode) {
                    EvidenceSource source = parseEnum(EvidenceSource.class, sourceNode.asText(null));
                    if (source != null) {
                        sources.add(source);
                    }
                }
            }

            results.add(new AiSuggestedRequirement(
                    requirementType, canonicalSkillId, label, suggestedWeight, sources, minimumStrength));
        }
        if (skipped > 0) {
            log.info("Dropped {} malformed rubric requirement item(s) from Anthropic response", skipped);
        }
        return results;
    }

    private void requireExpectedTool(AnthropicMessageResponse response, String expectedToolName) {
        if (!response.toolUseFound()) {
            throw new AiResponseParseException("Anthropic response did not use the required tool");
        }
        if (!expectedToolName.equals(response.toolName())) {
            throw new AiResponseParseException("Anthropic response used an unexpected tool");
        }
        if (response.toolInput() == null || !response.toolInput().isObject()) {
            throw new AiResponseParseException("Anthropic tool call input was not a JSON object");
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
