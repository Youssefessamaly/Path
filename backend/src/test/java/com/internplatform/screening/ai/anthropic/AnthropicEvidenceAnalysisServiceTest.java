package com.internplatform.screening.ai.anthropic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.screening.InternshipRubricRequirement;
import com.internplatform.screening.InternshipRubricRequirementRepository;
import com.internplatform.screening.RequirementType;
import com.internplatform.screening.ai.AiEvidenceAnalysisRequest;
import com.internplatform.screening.ai.AiEvidenceAnalysisResult;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.EvidenceClaimValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests — no Spring context, no real network call. AnthropicMessagesClient is a
 * hand-written stub returning canned AnthropicMessageResponse objects (or throwing
 * AiProviderException), exactly matching the real HttpAnthropicMessagesClient's output shape —
 * this is the "mocked API responses" automated test coverage for the adapter's business logic.
 */
class AnthropicEvidenceAnalysisServiceTest {

    private static final String CV_TEXT = "WORK EXPERIENCE\nBuilt REST APIs using Java and Spring Boot at a startup internship.\n\nSKILLS\nJava, Python";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final InternshipRubricRequirementRepository requirementRepository = mock(InternshipRubricRequirementRepository.class);
    private final EvidenceClaimValidator validator = new EvidenceClaimValidator(skillRepository, new CvSkillExtractionService());
    private final AiRateLimiter rateLimiter = new AiRateLimiter();

    private InternshipRubricRequirement requirement(UUID id, UUID rubricId, RequirementType type, String label) {
        InternshipRubricRequirement requirement = new InternshipRubricRequirement(rubricId, type);
        ReflectionTestUtils.setField(requirement, "id", id);
        ReflectionTestUtils.setField(requirement, "label", label);
        ReflectionTestUtils.setField(requirement, "acceptedEvidenceSources", Set.<EvidenceSource>of());
        return requirement;
    }

    private AnthropicEvidenceAnalysisService service(AnthropicMessagesClient client) {
        return new AnthropicEvidenceAnalysisService(
                client, new AiPromptBuilder(), new AiResponseParser(), validator, rateLimiter,
                new PersonalIdentifierRedactor(), requirementRepository, skillRepository,
                "claude-haiku-5-5", 1024, 50, true);
    }

    @Test
    void returnsAValidatedAcceptedSuggestionOnAHappyPathResponse() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        JsonNode toolInput = objectMapper.readTree("""
                {
                  "suggestions": [
                    {
                      "requirementId": "%s",
                      "excerptQuote": "Built REST APIs using Java and Spring Boot at a startup internship.",
                      "suggestedStrength": "STRONG",
                      "uncertain": false,
                      "explanation": "Direct work-experience evidence of Java usage"
                    }
                  ]
                }
                """.formatted(requirementId));
        AnthropicMessagesClient stubClient = request ->
                new AnthropicMessageResponse(true, "submit_evidence_analysis", toolInput, "tool_use");

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        AiEvidenceAnalysisResult result = service(stubClient).analyzeEvidence(request);

        assertThat(result.completed()).isTrue();
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).requirementId()).isEqualTo(requirementId);
    }

    @Test
    void dropsAClaimThatEvidenceClaimValidatorRejects() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        // Excerpt does not appear verbatim anywhere in CV_TEXT -> EvidenceClaimValidator rejects it.
        JsonNode toolInput = objectMapper.readTree("""
                {
                  "suggestions": [
                    { "requirementId": "%s", "excerptQuote": "This sentence is nowhere in the CV.",
                      "suggestedStrength": "STRONG", "uncertain": false, "explanation": "fabricated" }
                  ]
                }
                """.formatted(requirementId));
        AnthropicMessagesClient stubClient = request ->
                new AnthropicMessageResponse(true, "submit_evidence_analysis", toolInput, "tool_use");

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        AiEvidenceAnalysisResult result = service(stubClient).analyzeEvidence(request);

        assertThat(result.completed()).isTrue();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void downgradesAndFlagsAnOverclaimedStrengthRatherThanDroppingOrTrustingIt() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        // "SKILLS" section mention only supports WEAK, but the model claims STRONG.
        JsonNode toolInput = objectMapper.readTree("""
                {
                  "suggestions": [
                    { "requirementId": "%s", "excerptQuote": "Java, Python",
                      "suggestedStrength": "STRONG", "uncertain": false, "explanation": "found in skills list" }
                  ]
                }
                """.formatted(requirementId));
        AnthropicMessagesClient stubClient = request ->
                new AnthropicMessageResponse(true, "submit_evidence_analysis", toolInput, "tool_use");

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        AiEvidenceAnalysisResult result = service(stubClient).analyzeEvidence(request);

        assertThat(result.suggestions()).hasSize(1);
        AiSuggestedEvidence suggestion = result.suggestions().get(0);
        assertThat(suggestion.uncertain()).isTrue();
        assertThat(suggestion.explanation()).contains("Reviewer note");
    }

    @Test
    void degradesToDisabledOnProviderFailureWithoutThrowing() {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        AnthropicMessagesClient failingClient = request -> {
            throw new AiProviderException("Anthropic API returned status 500", true);
        };

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        AiEvidenceAnalysisResult result = service(failingClient).analyzeEvidence(request);

        assertThat(result.completed()).isFalse();
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.unavailableReason()).isNotBlank();
    }

    @Test
    void degradesToDisabledOnAMalformedProviderResponse() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        AnthropicMessagesClient brokenClient = request ->
                new AnthropicMessageResponse(false, null, null, "end_turn");

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        AiEvidenceAnalysisResult result = service(brokenClient).analyzeEvidence(request);

        assertThat(result.completed()).isFalse();
    }

    @Test
    void neverCallsTheProviderOnceTheDailyRateLimitIsReached() {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        AtomicInteger callCount = new AtomicInteger();
        AnthropicMessagesClient countingClient = request -> {
            callCount.incrementAndGet();
            throw new AiProviderException("should not be reached after limit", false);
        };

        AnthropicEvidenceAnalysisService svc = new AnthropicEvidenceAnalysisService(
                countingClient, new AiPromptBuilder(), new AiResponseParser(), validator, rateLimiter,
                new PersonalIdentifierRedactor(), requirementRepository, skillRepository,
                "claude-haiku-5-5", 1024, 1, true);

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        // First call exhausts the limit of 1 (it will fail with AiProviderException and degrade).
        svc.analyzeEvidence(request);
        assertThat(callCount.get()).isEqualTo(1);

        // Second call must be blocked by the rate limiter before ever reaching the client.
        AiEvidenceAnalysisResult secondResult = svc.analyzeEvidence(request);
        assertThat(secondResult.completed()).isFalse();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void ignoresRequirementIdsThatDoNotBelongToTheRubricBeingScreened() {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID unrelatedRequirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId)).thenReturn(List.of());

        AnthropicMessagesClient client = mock(AnthropicMessagesClient.class);

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(unrelatedRequirementId));

        AiEvidenceAnalysisResult result = service(client).analyzeEvidence(request);

        assertThat(result.completed()).isFalse();
        verify(client, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksAnalysisWhenThePrivacyReviewGateIsNotPassedEvenIfEverythingElseIsConfiguredCorrectly() {
        UUID companyId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        when(requirementRepository.findAllByInternshipRubricId(rubricId))
                .thenReturn(List.of(requirement(requirementId, rubricId, RequirementType.REQUIRED, "Java")));

        AnthropicMessagesClient client = mock(AnthropicMessagesClient.class);
        AnthropicEvidenceAnalysisService gatedService = new AnthropicEvidenceAnalysisService(
                client, new AiPromptBuilder(), new AiResponseParser(), validator, rateLimiter,
                new PersonalIdentifierRedactor(), requirementRepository, skillRepository,
                "claude-haiku-5-5", 1024, 50, false);

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                companyId, UUID.randomUUID(), rubricId, CV_TEXT, List.of(requirementId));

        AiEvidenceAnalysisResult result = gatedService.analyzeEvidence(request);

        assertThat(result.completed()).isFalse();
        assertThat(result.unavailableReason()).containsIgnoringCase("privacy");
        verify(client, never()).send(org.mockito.ArgumentMatchers.any());
    }
}
