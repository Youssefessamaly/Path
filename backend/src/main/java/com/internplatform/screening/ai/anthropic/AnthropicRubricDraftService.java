package com.internplatform.screening.ai.anthropic;

import com.internplatform.internprofile.SkillRepository;
import com.internplatform.screening.ai.AiRubricDraftRequest;
import com.internplatform.screening.ai.AiRubricDraftResult;
import com.internplatform.screening.ai.AiRubricDraftService;
import com.internplatform.screening.ai.AiSuggestedRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Real AI rubric-drafting implementation backed by the Anthropic Messages API. Active only
 * when app.ai.rubric-draft.mode=anthropic; DisabledAiRubricDraftService remains the default.
 *
 * A drafted requirement here carries no authority of its own (see AiRubricDraftResult's own
 * javadoc) — turning one into a real requirement always goes through the existing, unmodified
 * ScreeningProfileService/InternshipRubricService endpoints, which independently re-validate
 * canonicalSkillId against the real catalog. This class additionally defensively nulls out any
 * canonicalSkillId the model invented that does not exist in the real catalog, so the UI never
 * even displays a suggestion pointing at a nonexistent skill — belt-and-suspenders, not a
 * replacement for that downstream re-validation.
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rubric-draft", name = "mode", havingValue = "anthropic")
public class AnthropicRubricDraftService implements AiRubricDraftService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicRubricDraftService.class);
    private static final String TOOL_NAME = "submit_rubric_draft";

    private final AnthropicMessagesClient client;
    private final AiPromptBuilder promptBuilder;
    private final AiResponseParser responseParser;
    private final AiRateLimiter rateLimiter;
    private final SkillRepository skillRepository;
    private final String model;
    private final int maxOutputTokens;
    private final int dailyCallLimitPerCompany;

    public AnthropicRubricDraftService(
            AnthropicMessagesClient client,
            AiPromptBuilder promptBuilder,
            AiResponseParser responseParser,
            AiRateLimiter rateLimiter,
            SkillRepository skillRepository,
            @Value("${app.ai.provider.model}") String model,
            @Value("${app.ai.provider.max-output-tokens:1024}") int maxOutputTokens,
            @Value("${app.ai.provider.daily-call-limit-per-company:50}") int dailyCallLimitPerCompany
    ) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.rateLimiter = rateLimiter;
        this.skillRepository = skillRepository;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.dailyCallLimitPerCompany = dailyCallLimitPerCompany;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public AiRubricDraftResult draftFromInstructions(AiRubricDraftRequest request) {
        if (request.instructionsText() == null || request.instructionsText().isBlank()) {
            return AiRubricDraftResult.disabled("No instructions provided");
        }

        if (!rateLimiter.tryAcquire(request.companyProfileId(), dailyCallLimitPerCompany)) {
            log.info("AI rubric draft skipped: daily rate limit reached for company");
            return AiRubricDraftResult.disabled("Daily AI usage limit reached for this company");
        }

        AnthropicMessageResponse response;
        try {
            AnthropicMessageRequest providerRequest =
                    promptBuilder.buildRubricDraftRequest(model, maxOutputTokens, request.instructionsText());
            response = client.send(providerRequest);
        } catch (AiProviderException ex) {
            log.warn("AI rubric draft unavailable: {}", ex.getMessage());
            return AiRubricDraftResult.disabled("AI provider request failed");
        }

        List<AiSuggestedRequirement> rawRequirements;
        try {
            rawRequirements = responseParser.parseRubricDraft(response, TOOL_NAME);
        } catch (AiResponseParseException ex) {
            log.warn("AI rubric draft unavailable: {}", ex.getMessage());
            return AiRubricDraftResult.disabled("AI provider returned an unusable response");
        }

        List<AiSuggestedRequirement> sanitized = new ArrayList<>();
        for (AiSuggestedRequirement requirement : rawRequirements) {
            boolean knownSkill = requirement.canonicalSkillId() != null
                    && skillRepository.existsById(requirement.canonicalSkillId());
            sanitized.add(knownSkill
                    ? requirement
                    : new AiSuggestedRequirement(
                            requirement.requirementType(), null, requirement.label(),
                            requirement.suggestedWeight(), requirement.suggestedAcceptedEvidenceSources(),
                            requirement.suggestedMinimumEvidenceStrength()));
        }

        return new AiRubricDraftResult(true, sanitized, null);
    }
}
