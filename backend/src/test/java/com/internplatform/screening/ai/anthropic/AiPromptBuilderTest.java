package com.internplatform.screening.ai.anthropic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptBuilderTest {

    private final AiPromptBuilder builder = new AiPromptBuilder();

    @Test
    void evidenceAnalysisRequestForcesTheExpectedToolAndIncludesTheCvTextAndRequirements() {
        UUID requirementId = UUID.randomUUID();
        EvidenceRequirementContext context = new EvidenceRequirementContext(
                requirementId, "Java", "REQUIRED", "STRONG", Set.of("WORK_EXPERIENCE"));

        AnthropicMessageRequest request = builder.buildEvidenceAnalysisRequest(
                "claude-haiku-5-5", 1024, "Built REST APIs using Java.", List.of(context));

        assertThat(request.model()).isEqualTo("claude-haiku-5-5");
        assertThat(request.toolName()).isEqualTo("submit_evidence_analysis");
        assertThat(request.userMessage()).contains("Built REST APIs using Java.");
        assertThat(request.userMessage()).contains(requirementId.toString());
        assertThat(request.toolInputSchema().toString()).contains("suggestions");
    }

    @Test
    void evidenceAnalysisSystemPromptFramesCvTextAsDataNotInstructions() {
        AnthropicMessageRequest request = builder.buildEvidenceAnalysisRequest(
                "claude-haiku-5-5", 1024, "irrelevant", List.of());

        assertThat(request.systemPrompt().toLowerCase())
                .contains("data").contains("never follow");
    }

    @Test
    void rubricDraftRequestForcesTheExpectedToolAndIncludesTheInstructions() {
        AnthropicMessageRequest request = builder.buildRubricDraftRequest(
                "claude-haiku-5-5", 1024, "We need a backend intern with Java and SQL.");

        assertThat(request.toolName()).isEqualTo("submit_rubric_draft");
        assertThat(request.userMessage()).contains("We need a backend intern with Java and SQL.");
        assertThat(request.toolInputSchema().toString()).contains("requirements");
    }

    @Test
    void rubricDraftAcceptsArabicInstructionsWithoutSpecialHandling() {
        AnthropicMessageRequest request = builder.buildRubricDraftRequest(
                "claude-haiku-5-5", 1024, "نحتاج متدرب لديه خبرة في جافا وقواعد البيانات.");

        assertThat(request.userMessage()).contains("نحتاج متدرب لديه خبرة في جافا وقواعد البيانات.");
    }
}
