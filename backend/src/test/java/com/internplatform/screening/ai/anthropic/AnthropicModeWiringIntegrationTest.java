package com.internplatform.screening.ai.anthropic;

import com.internplatform.screening.ai.AiEvidenceAnalysisService;
import com.internplatform.screening.ai.AiRubricDraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the real Spring context selects the Anthropic-backed implementations when, and
 * only when, mode=anthropic is explicitly set — mirroring AiDisabledByDefaultIntegrationTest's
 * check for the default ("disabled") case. This test NEVER calls analyzeEvidence/
 * draftFromInstructions and NEVER triggers a real network call: constructing
 * HttpAnthropicMessagesClient does not itself contact Anthropic (see its own javadoc), it only
 * would if invoked, which this test deliberately never does. No real API key is configured.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.ai.evidence-analysis.mode=anthropic",
        "app.ai.rubric-draft.mode=anthropic"
})
class AnthropicModeWiringIntegrationTest {

    @Autowired
    private AiEvidenceAnalysisService evidenceAnalysisService;

    @Autowired
    private AiRubricDraftService rubricDraftService;

    @Test
    void wiresTheAnthropicEvidenceAnalysisServiceWhenModeIsAnthropic() {
        assertThat(evidenceAnalysisService).isInstanceOf(AnthropicEvidenceAnalysisService.class);
        assertThat(evidenceAnalysisService.isEnabled()).isTrue();
    }

    @Test
    void wiresTheAnthropicRubricDraftServiceWhenModeIsAnthropic() {
        assertThat(rubricDraftService).isInstanceOf(AnthropicRubricDraftService.class);
        assertThat(rubricDraftService.isEnabled()).isTrue();
    }
}
