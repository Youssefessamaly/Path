package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.screening.RequirementType;
import com.internplatform.screening.ai.AiRubricDraftRequest;
import com.internplatform.screening.ai.AiRubricDraftResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicRubricDraftServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final AiRateLimiter rateLimiter = new AiRateLimiter();

    private AnthropicRubricDraftService service(AnthropicMessagesClient client) {
        return new AnthropicRubricDraftService(
                client, new AiPromptBuilder(), new AiResponseParser(), rateLimiter, skillRepository,
                "claude-haiku-5-5", 1024, 50);
    }

    @Test
    void returnsDraftedRequirementsOnAHappyPathResponse() throws Exception {
        UUID skillId = UUID.randomUUID();
        when(skillRepository.existsById(skillId)).thenReturn(true);

        JsonNode toolInput = objectMapper.readTree("""
                {
                  "requirements": [
                    { "requirementType": "REQUIRED", "canonicalSkillId": "%s", "suggestedWeight": 40 }
                  ]
                }
                """.formatted(skillId));
        AnthropicMessagesClient stubClient = request ->
                new AnthropicMessageResponse(true, "submit_rubric_draft", toolInput, "tool_use");

        AiRubricDraftRequest request = new AiRubricDraftRequest(UUID.randomUUID(), "We need a backend intern with Java.");

        AiRubricDraftResult result = service(stubClient).draftFromInstructions(request);

        assertThat(result.completed()).isTrue();
        assertThat(result.suggestedRequirements()).hasSize(1);
        assertThat(result.suggestedRequirements().get(0).canonicalSkillId()).isEqualTo(skillId);
        assertThat(result.suggestedRequirements().get(0).requirementType()).isEqualTo(RequirementType.REQUIRED);
    }

    @Test
    void nullsOutASkillIdTheModelInventedThatDoesNotExistInTheRealCatalog() throws Exception {
        UUID inventedSkillId = UUID.randomUUID();
        when(skillRepository.existsById(inventedSkillId)).thenReturn(false);

        JsonNode toolInput = objectMapper.readTree("""
                {
                  "requirements": [
                    { "requirementType": "PREFERRED", "canonicalSkillId": "%s", "label": "Something", "suggestedWeight": 20 }
                  ]
                }
                """.formatted(inventedSkillId));
        AnthropicMessagesClient stubClient = request ->
                new AnthropicMessageResponse(true, "submit_rubric_draft", toolInput, "tool_use");

        AiRubricDraftRequest request = new AiRubricDraftRequest(UUID.randomUUID(), "Anything");

        AiRubricDraftResult result = service(stubClient).draftFromInstructions(request);

        assertThat(result.suggestedRequirements()).hasSize(1);
        assertThat(result.suggestedRequirements().get(0).canonicalSkillId()).isNull();
    }

    @Test
    void degradesToDisabledOnProviderFailure() {
        AnthropicMessagesClient failingClient = request -> {
            throw new AiProviderException("timeout", true);
        };

        AiRubricDraftResult result = service(failingClient)
                .draftFromInstructions(new AiRubricDraftRequest(UUID.randomUUID(), "Anything"));

        assertThat(result.completed()).isFalse();
        assertThat(result.suggestedRequirements()).isEmpty();
    }

    @Test
    void rejectsBlankInstructionsWithoutCallingTheProvider() {
        AnthropicMessagesClient client = mock(AnthropicMessagesClient.class);

        AiRubricDraftResult result = service(client)
                .draftFromInstructions(new AiRubricDraftRequest(UUID.randomUUID(), "   "));

        assertThat(result.completed()).isFalse();
    }

    @Test
    void acceptsArabicInstructionsText() throws Exception {
        JsonNode toolInput = objectMapper.readTree("""
                { "requirements": [ { "requirementType": "REQUIRED", "label": "جافا", "suggestedWeight": 50 } ] }
                """);
        AnthropicMessagesClient stubClient = request ->
                new AnthropicMessageResponse(true, "submit_rubric_draft", toolInput, "tool_use");

        AiRubricDraftRequest request = new AiRubricDraftRequest(
                UUID.randomUUID(), "نحتاج متدرب لديه خبرة في جافا.");

        AiRubricDraftResult result = service(stubClient).draftFromInstructions(request);

        assertThat(result.completed()).isTrue();
        assertThat(result.suggestedRequirements().get(0).label()).isEqualTo("جافا");
    }
}
