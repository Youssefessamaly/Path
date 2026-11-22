package com.internplatform.screening.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the real Spring context — not a mock — wires the disabled AI implementations by
 * default (no app.ai.* override is set anywhere in test config), and that calling them
 * behaves exactly like "AI unavailable" rather than throwing. This is the regression net for
 * the @ConditionalOnProperty bean selection itself: if a future provider's conditional is
 * misconfigured such that two beans match (or none do), this test fails loudly instead of
 * only surfacing as a confusing runtime error somewhere else.
 */
@SpringBootTest
class AiDisabledByDefaultIntegrationTest {

    @Autowired
    private AiEvidenceAnalysisService evidenceAnalysisService;

    @Autowired
    private AiRubricDraftService rubricDraftService;

    @Test
    void evidenceAnalysisServiceIsTheDisabledImplementationByDefault() {
        assertThat(evidenceAnalysisService).isInstanceOf(DisabledAiEvidenceAnalysisService.class);
        assertThat(evidenceAnalysisService.isEnabled()).isFalse();
    }

    @Test
    void analyzingEvidenceWhenDisabledNeverThrowsAndNeverReturnsSuggestions() {
        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "some CV text", List.of(UUID.randomUUID()));

        AiEvidenceAnalysisResult result = evidenceAnalysisService.analyzeEvidence(request);

        assertThat(result.completed()).isFalse();
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.unavailableReason()).isNotBlank();
    }

    @Test
    void rubricDraftServiceIsTheDisabledImplementationByDefault() {
        assertThat(rubricDraftService).isInstanceOf(DisabledAiRubricDraftService.class);
        assertThat(rubricDraftService.isEnabled()).isFalse();
    }

    @Test
    void draftingARubricWhenDisabledNeverThrowsAndNeverReturnsRequirements() {
        AiRubricDraftRequest request = new AiRubricDraftRequest(UUID.randomUUID(), "We need a backend intern with Java.");

        AiRubricDraftResult result = rubricDraftService.draftFromInstructions(request);

        assertThat(result.completed()).isFalse();
        assertThat(result.suggestedRequirements()).isEmpty();
        assertThat(result.unavailableReason()).isNotBlank();
    }
}
