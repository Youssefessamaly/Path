package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.screening.RequirementType;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.AiSuggestedRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseParserTest {

    private static final String TOOL = "submit_evidence_analysis";
    private final AiResponseParser parser = new AiResponseParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesAWellFormedEvidenceAnalysisResponse() throws Exception {
        UUID requirementId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        JsonNode input = objectMapper.readTree("""
                {
                  "suggestions": [
                    {
                      "requirementId": "%s",
                      "canonicalSkillId": "%s",
                      "excerptQuote": "Built REST APIs using Java and Spring Boot",
                      "suggestedStrength": "STRONG",
                      "uncertain": false,
                      "explanation": "Direct work-experience mention of Java usage"
                    }
                  ]
                }
                """.formatted(requirementId, skillId));

        AnthropicMessageResponse response = new AnthropicMessageResponse(true, TOOL, input, "tool_use");

        List<AiSuggestedEvidence> result = parser.parseEvidenceAnalysis(response, TOOL);

        assertThat(result).hasSize(1);
        AiSuggestedEvidence suggestion = result.get(0);
        assertThat(suggestion.requirementId()).isEqualTo(requirementId);
        assertThat(suggestion.canonicalSkillId()).isEqualTo(skillId);
        assertThat(suggestion.excerptQuote()).isEqualTo("Built REST APIs using Java and Spring Boot");
        assertThat(suggestion.suggestedStrength()).isEqualTo(EvidenceStrength.STRONG);
        assertThat(suggestion.uncertain()).isFalse();
    }

    @Test
    void dropsOnlyTheMalformedItemAndKeepsTheRest() throws Exception {
        UUID goodRequirementId = UUID.randomUUID();
        JsonNode input = objectMapper.readTree("""
                {
                  "suggestions": [
                    { "requirementId": "not-a-uuid", "excerptQuote": "x", "suggestedStrength": "WEAK", "uncertain": false, "explanation": "" },
                    { "requirementId": "%s", "excerptQuote": "Led a Python data pipeline project", "suggestedStrength": "MODERATE", "uncertain": true, "explanation": "project mention" }
                  ]
                }
                """.formatted(goodRequirementId));

        AnthropicMessageResponse response = new AnthropicMessageResponse(true, TOOL, input, "tool_use");

        List<AiSuggestedEvidence> result = parser.parseEvidenceAnalysis(response, TOOL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).requirementId()).isEqualTo(goodRequirementId);
    }

    @Test
    void treatsAnUnknownEnumValueAsAbsentRatherThanFailing() throws Exception {
        UUID requirementId = UUID.randomUUID();
        JsonNode input = objectMapper.readTree("""
                {
                  "suggestions": [
                    { "requirementId": "%s", "excerptQuote": "x", "suggestedStrength": "EXTREME", "uncertain": false, "explanation": "" }
                  ]
                }
                """.formatted(requirementId));

        AnthropicMessageResponse response = new AnthropicMessageResponse(true, TOOL, input, "tool_use");

        List<AiSuggestedEvidence> result = parser.parseEvidenceAnalysis(response, TOOL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).suggestedStrength()).isNull();
    }

    @Test
    void rejectsAResponseThatDidNotUseTheTool() {
        AnthropicMessageResponse response = new AnthropicMessageResponse(false, null, null, "end_turn");

        assertThatThrownBy(() -> parser.parseEvidenceAnalysis(response, TOOL))
                .isInstanceOf(AiResponseParseException.class);
    }

    @Test
    void rejectsAResponseThatUsedTheWrongTool() {
        AnthropicMessageResponse response = new AnthropicMessageResponse(true, "some_other_tool",
                objectMapper.createObjectNode(), "tool_use");

        assertThatThrownBy(() -> parser.parseEvidenceAnalysis(response, TOOL))
                .isInstanceOf(AiResponseParseException.class);
    }

    @Test
    void rejectsAResponseMissingTheSuggestionsArray() throws Exception {
        JsonNode input = objectMapper.readTree("{}");
        AnthropicMessageResponse response = new AnthropicMessageResponse(true, TOOL, input, "tool_use");

        assertThatThrownBy(() -> parser.parseEvidenceAnalysis(response, TOOL))
                .isInstanceOf(AiResponseParseException.class);
    }

    @Test
    void parsesAWellFormedRubricDraftResponse() throws Exception {
        UUID skillId = UUID.randomUUID();
        JsonNode input = objectMapper.readTree("""
                {
                  "requirements": [
                    {
                      "requirementType": "REQUIRED",
                      "canonicalSkillId": "%s",
                      "suggestedWeight": 40,
                      "suggestedAcceptedEvidenceSources": ["WORK_EXPERIENCE", "PROJECT"],
                      "suggestedMinimumEvidenceStrength": "STRONG"
                    },
                    {
                      "requirementType": "BONUS",
                      "label": "Open-source contributions",
                      "suggestedWeight": 10
                    }
                  ]
                }
                """.formatted(skillId));

        AnthropicMessageResponse response = new AnthropicMessageResponse(true, "submit_rubric_draft", input, "tool_use");

        List<AiSuggestedRequirement> result = parser.parseRubricDraft(response, "submit_rubric_draft");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).requirementType()).isEqualTo(RequirementType.REQUIRED);
        assertThat(result.get(0).canonicalSkillId()).isEqualTo(skillId);
        assertThat(result.get(0).suggestedAcceptedEvidenceSources())
                .containsExactlyInAnyOrder(EvidenceSource.WORK_EXPERIENCE, EvidenceSource.PROJECT);
        assertThat(result.get(1).label()).isEqualTo("Open-source contributions");
        assertThat(result.get(1).canonicalSkillId()).isNull();
    }

    @Test
    void dropsARubricItemMissingTheRequiredWeightField() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "requirements": [
                    { "requirementType": "REQUIRED", "label": "No weight here" }
                  ]
                }
                """);

        AnthropicMessageResponse response = new AnthropicMessageResponse(true, "submit_rubric_draft", input, "tool_use");

        List<AiSuggestedRequirement> result = parser.parseRubricDraft(response, "submit_rubric_draft");

        assertThat(result).isEmpty();
    }
}
