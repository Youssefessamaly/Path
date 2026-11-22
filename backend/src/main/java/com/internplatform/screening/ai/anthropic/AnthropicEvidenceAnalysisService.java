package com.internplatform.screening.ai.anthropic;

import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.screening.InternshipRubricRequirement;
import com.internplatform.screening.InternshipRubricRequirementRepository;
import com.internplatform.screening.ai.AiEvidenceAnalysisRequest;
import com.internplatform.screening.ai.AiEvidenceAnalysisResult;
import com.internplatform.screening.ai.AiEvidenceAnalysisService;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.EvidenceClaimStatus;
import com.internplatform.screening.ai.EvidenceClaimValidator;
import com.internplatform.screening.ai.ValidatedAiEvidenceClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Real AI evidence-analysis implementation backed by the Anthropic Messages API
 * (model configured via app.ai.provider.model — see HttpAnthropicMessagesClient). Active only
 * when app.ai.evidence-analysis.mode=anthropic; DisabledAiEvidenceAnalysisService remains the
 * default (see its own @ConditionalOnProperty matchIfMissing=true), so this bean never even
 * exists unless explicitly configured — AI stays disabled by default.
 *
 * Every suggestion this class returns has ALREADY passed EvidenceClaimValidator: rejected
 * claims are dropped entirely here, never surfaced to a caller. This keeps the
 * AiEvidenceAnalysisService contract ("always run suggestions through EvidenceClaimValidator
 * before using them for anything") satisfied unconditionally by this implementation itself,
 * rather than relying on every future caller to remember to validate.
 *
 * Never touches InternshipApplication, its status, or any ScreeningReport/ScreeningReportResult
 * row — this class has no dependency on those repositories at all, by design (requirements 9
 * and 14: AI can never change application status or modify a historical screening report).
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.evidence-analysis", name = "mode", havingValue = "anthropic")
public class AnthropicEvidenceAnalysisService implements AiEvidenceAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicEvidenceAnalysisService.class);
    private static final String TOOL_NAME = "submit_evidence_analysis";

    private final AnthropicMessagesClient client;
    private final AiPromptBuilder promptBuilder;
    private final AiResponseParser responseParser;
    private final EvidenceClaimValidator evidenceClaimValidator;
    private final AiRateLimiter rateLimiter;
    private final PersonalIdentifierRedactor redactor;
    private final InternshipRubricRequirementRepository requirementRepository;
    private final SkillRepository skillRepository;
    private final String model;
    private final int maxOutputTokens;
    private final int dailyCallLimitPerCompany;
    private final boolean privacyReviewPassed;

    public AnthropicEvidenceAnalysisService(
            AnthropicMessagesClient client,
            AiPromptBuilder promptBuilder,
            AiResponseParser responseParser,
            EvidenceClaimValidator evidenceClaimValidator,
            AiRateLimiter rateLimiter,
            PersonalIdentifierRedactor redactor,
            InternshipRubricRequirementRepository requirementRepository,
            SkillRepository skillRepository,
            @Value("${app.ai.provider.model}") String model,
            @Value("${app.ai.provider.max-output-tokens:1024}") int maxOutputTokens,
            @Value("${app.ai.provider.daily-call-limit-per-company:50}") int dailyCallLimitPerCompany,
            @Value("${app.ai.evidence-analysis.privacy-review-passed:false}") boolean privacyReviewPassed
    ) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.evidenceClaimValidator = evidenceClaimValidator;
        this.rateLimiter = rateLimiter;
        this.redactor = redactor;
        this.requirementRepository = requirementRepository;
        this.skillRepository = skillRepository;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.dailyCallLimitPerCompany = dailyCallLimitPerCompany;
        this.privacyReviewPassed = privacyReviewPassed;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public AiEvidenceAnalysisResult analyzeEvidence(AiEvidenceAnalysisRequest request) {
        // Independent of app.ai.evidence-analysis.mode: that property only selects THIS bean
        // over the disabled one. This second, separate gate is the business-side guarantee
        // that real applicant CV text is never transmitted externally until the applicable
        // privacy notice, consent/disclosure, and lawful-processing prerequisites have been
        // confirmed and deliberately turned on (app.ai.evidence-analysis.privacy-review-passed,
        // false by default) — never inferred, never defaulted to true. Controlled integration
        // testing against synthetic data (see LiveAnthropicAdapterIT) constructs this class
        // directly in a test and is unaffected by this flag either way.
        if (!privacyReviewPassed) {
            return AiEvidenceAnalysisResult.disabled(
                    "AI evidence analysis is pending privacy/consent review and is not yet enabled for real applicant data");
        }

        if (request.authorizedCvText() == null || request.authorizedCvText().isBlank()
                || request.requirementIdsNeedingAnalysis() == null || request.requirementIdsNeedingAnalysis().isEmpty()) {
            return AiEvidenceAnalysisResult.disabled("Nothing to analyze");
        }

        if (!rateLimiter.tryAcquire(request.companyProfileId(), dailyCallLimitPerCompany)) {
            log.info("AI evidence analysis skipped: daily rate limit reached for company");
            return AiEvidenceAnalysisResult.disabled("Daily AI usage limit reached for this company");
        }

        List<InternshipRubricRequirement> rubricRequirements =
                requirementRepository.findAllByInternshipRubricId(request.internshipRubricId());
        Set<UUID> requestedIds = new HashSet<>(request.requirementIdsNeedingAnalysis());

        List<InternshipRubricRequirement> inScope = rubricRequirements.stream()
                .filter(r -> requestedIds.contains(r.getId()))
                .toList();

        if (inScope.isEmpty()) {
            return AiEvidenceAnalysisResult.disabled("No valid requirements to analyze");
        }

        Set<UUID> skillIds = inScope.stream()
                .map(InternshipRubricRequirement::getSkillId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, Skill> skillsById = new HashMap<>();
        skillRepository.findAllById(skillIds).forEach(skill -> skillsById.put(skill.getId(), skill));

        List<EvidenceRequirementContext> contexts = inScope.stream()
                .map(r -> toContext(r, skillsById))
                .toList();

        String redactedCvText = redactor.redact(request.authorizedCvText());

        AnthropicMessageResponse response;
        try {
            AnthropicMessageRequest providerRequest =
                    promptBuilder.buildEvidenceAnalysisRequest(model, maxOutputTokens, redactedCvText, contexts);
            response = client.send(providerRequest);
        } catch (AiProviderException ex) {
            log.warn("AI evidence analysis unavailable: {}", ex.getMessage());
            return AiEvidenceAnalysisResult.disabled("AI provider request failed");
        }

        List<AiSuggestedEvidence> rawSuggestions;
        try {
            rawSuggestions = responseParser.parseEvidenceAnalysis(response, TOOL_NAME);
        } catch (AiResponseParseException ex) {
            log.warn("AI evidence analysis unavailable: {}", ex.getMessage());
            return AiEvidenceAnalysisResult.disabled("AI provider returned an unusable response");
        }

        Set<UUID> validRequirementIds = inScope.stream().map(InternshipRubricRequirement::getId).collect(Collectors.toSet());
        List<ValidatedAiEvidenceClaim> validated =
                evidenceClaimValidator.validateAll(rawSuggestions, request.authorizedCvText(), validRequirementIds);

        List<AiSuggestedEvidence> accepted = new ArrayList<>();
        for (ValidatedAiEvidenceClaim claim : validated) {
            if (claim.status() == EvidenceClaimStatus.REJECTED) {
                continue;
            }
            AiSuggestedEvidence original = claim.originalClaim();
            boolean needsReview = claim.status() == EvidenceClaimStatus.ACCEPTED_NEEDS_REVIEW;
            String explanation = needsReview
                    ? original.explanation() + " [Reviewer note: " + claim.note() + "]"
                    : original.explanation();
            accepted.add(new AiSuggestedEvidence(
                    original.requirementId(),
                    original.canonicalSkillId(),
                    original.excerptQuote(),
                    claim.effectiveStrength(),
                    needsReview || original.uncertain(),
                    explanation
            ));
        }

        return new AiEvidenceAnalysisResult(true, accepted, null);
    }

    private EvidenceRequirementContext toContext(InternshipRubricRequirement requirement, Map<UUID, Skill> skillsById) {
        String skillOrLabel = requirement.getSkillId() != null
                ? skillsById.getOrDefault(requirement.getSkillId(), null) != null
                        ? skillsById.get(requirement.getSkillId()).getName()
                        : "Unknown skill"
                : requirement.getLabel();

        Set<String> acceptedSources = requirement.getAcceptedEvidenceSources().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        return new EvidenceRequirementContext(
                requirement.getId(),
                skillOrLabel,
                requirement.getRequirementType().name(),
                requirement.getMinimumEvidenceStrength() != null ? requirement.getMinimumEvidenceStrength().name() : null,
                acceptedSources
        );
    }
}
