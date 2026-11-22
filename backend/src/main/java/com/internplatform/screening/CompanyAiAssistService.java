package com.internplatform.screening;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.CvStorageService;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import com.internplatform.screening.ai.AiEvidenceAnalysisRequest;
import com.internplatform.screening.ai.AiEvidenceAnalysisResult;
import com.internplatform.screening.ai.AiEvidenceAnalysisService;
import com.internplatform.screening.ai.AiRubricDraftRequest;
import com.internplatform.screening.ai.AiRubricDraftResult;
import com.internplatform.screening.ai.AiRubricDraftService;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.AiSuggestedRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the two Stage 5B company-facing AI-assist actions on top of the existing,
 * unmodified Stage 4/Stage 5A/5B building blocks — ScreeningReport/ScreeningReportResult,
 * InternshipRubric(Requirement), CvStorageService/CvSkillExtractionService, and the
 * provider-independent AiEvidenceAnalysisService/AiRubricDraftService interfaces. Deliberately
 * writes NOTHING: neither method here ever creates, updates, or deletes a ScreeningReport,
 * ScreeningReportResult, ScreeningProfile, or InternshipRubric row, and neither ever touches
 * InternshipApplication.status. Every suggestion returned is advisory; turning one into a real
 * requirement, or acting on an evidence suggestion, always goes through the existing,
 * unmodified Stage 3/Stage 4 endpoints by explicit recruiter action.
 */
@Service
public class CompanyAiAssistService {

    private final CompanyAccessResolver companyAccessResolver;
    private final InternshipRepository internshipRepository;
    private final InternshipApplicationRepository applicationRepository;
    private final ScreeningReportRepository reportRepository;
    private final ScreeningReportResultRepository resultRepository;
    private final InternshipRubricRepository rubricRepository;
    private final InternshipRubricRequirementRepository requirementRepository;
    private final SkillRepository skillRepository;
    private final CvStorageService cvStorageService;
    private final CvSkillExtractionService cvSkillExtractionService;
    private final AiEvidenceAnalysisService aiEvidenceAnalysisService;
    private final AiRubricDraftService aiRubricDraftService;

    public CompanyAiAssistService(
            CompanyAccessResolver companyAccessResolver,
            InternshipRepository internshipRepository,
            InternshipApplicationRepository applicationRepository,
            ScreeningReportRepository reportRepository,
            ScreeningReportResultRepository resultRepository,
            InternshipRubricRepository rubricRepository,
            InternshipRubricRequirementRepository requirementRepository,
            SkillRepository skillRepository,
            CvStorageService cvStorageService,
            CvSkillExtractionService cvSkillExtractionService,
            AiEvidenceAnalysisService aiEvidenceAnalysisService,
            AiRubricDraftService aiRubricDraftService
    ) {
        this.companyAccessResolver = companyAccessResolver;
        this.internshipRepository = internshipRepository;
        this.applicationRepository = applicationRepository;
        this.reportRepository = reportRepository;
        this.resultRepository = resultRepository;
        this.rubricRepository = rubricRepository;
        this.requirementRepository = requirementRepository;
        this.skillRepository = skillRepository;
        this.cvStorageService = cvStorageService;
        this.cvSkillExtractionService = cvSkillExtractionService;
        this.aiEvidenceAnalysisService = aiEvidenceAnalysisService;
        this.aiRubricDraftService = aiRubricDraftService;
    }

    /**
     * Requires an EXISTING deterministic ScreeningReport (created via the existing
     * screening-reports endpoints) — this action augments that specific, already-computed
     * report; it never implicitly triggers a new deterministic screening pass as a side
     * effect, and it never writes back into the report it reads.
     */
    @Transactional(readOnly = true)
    public AiEvidenceAnalysisResponse analyzeEvidence(
            UUID userId, UUID internshipId, UUID applicationId, UUID reportId, AiEvidenceAnalysisApiRequest request
    ) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipApplication application = findOwnedApplicationOrThrow(applicationId, internship.getId());

        // The applicant's own choice, captured once at submission time — checked BEFORE the
        // report, rubric, or CV are even touched. A recruiter clicking "Analyze with AI" can
        // never override this; there is no parameter, role, or code path here that bypasses
        // it. Independent of, and in addition to, the separate business-side privacy-review
        // gate inside AnthropicEvidenceAnalysisService itself — both must hold.
        if (!application.isAiAnalysisConsent()) {
            return new AiEvidenceAnalysisResponse(false, reportId, null, 0, List.of(),
                    "The applicant has not authorized AI-assisted analysis of their CV for this application");
        }

        ScreeningReport report = findOwnedReportOrThrow(reportId, application.getId());

        InternshipRubric activeRubric = rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This internship has no active screening rubric"));
        if (!activeRubric.getId().equals(report.getInternshipRubricId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This report was computed against a rubric version that is no longer active — "
                            + "re-screen the application against the current rubric before requesting AI analysis");
        }

        Set<UUID> eligibleIds = resultRepository.findAllByScreeningReportId(report.getId()).stream()
                .filter(r -> r.getResult() == ScreeningResult.NEEDS_REVIEW || r.getResult() == ScreeningResult.INSUFFICIENT_EVIDENCE)
                .map(ScreeningReportResult::getInternshipRubricRequirementId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        if (request != null && request.requirementIds() != null) {
            eligibleIds.retainAll(new HashSet<>(request.requirementIds()));
        }

        if (eligibleIds.isEmpty()) {
            return new AiEvidenceAnalysisResponse(false, report.getId(), report.getInternshipRubricId(),
                    report.getRubricVersion(), List.of(), "No requirements in this report are eligible for AI analysis");
        }

        String cvText = extractFrozenCvText(application);
        if (cvText == null) {
            return new AiEvidenceAnalysisResponse(false, report.getId(), report.getInternshipRubricId(),
                    report.getRubricVersion(), List.of(), "The application's CV text could not be read");
        }

        AiEvidenceAnalysisRequest aiRequest = new AiEvidenceAnalysisRequest(
                company.getId(), report.getId(), report.getInternshipRubricId(), cvText, new ArrayList<>(eligibleIds));
        AiEvidenceAnalysisResult result = aiEvidenceAnalysisService.analyzeEvidence(aiRequest);

        return new AiEvidenceAnalysisResponse(
                result.completed(), report.getId(), report.getInternshipRubricId(), report.getRubricVersion(),
                toSuggestionResponses(result.suggestions()), result.unavailableReason());
    }

    /** Never reads or writes any ScreeningProfile/InternshipRubric row — purely a pass-through
     *  to AiRubricDraftService plus best-effort skill resolution against the real catalog. */
    @Transactional(readOnly = true)
    public AiRubricDraftResponse draftRubric(UUID userId, UUID internshipId, AiRubricDraftApiRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        findOwnedInternshipOrThrow(internshipId, company.getId());

        AiRubricDraftRequest aiRequest = new AiRubricDraftRequest(company.getId(), request.instructionsText());
        AiRubricDraftResult result = aiRubricDraftService.draftFromInstructions(aiRequest);

        List<AiSuggestedRequirementResponse> suggestions = result.suggestedRequirements().stream()
                .map(this::toSuggestedRequirementResponse)
                .toList();

        return new AiRubricDraftResponse(result.completed(), suggestions, result.unavailableReason());
    }

    private AiSuggestedRequirementResponse toSuggestedRequirementResponse(AiSuggestedRequirement suggestion) {
        Skill resolved = null;
        if (suggestion.canonicalSkillId() != null) {
            // Defensive re-check: never trust the provider's own claim that this id exists,
            // even though AnthropicRubricDraftService already performs the same check once.
            resolved = skillRepository.findById(suggestion.canonicalSkillId()).orElse(null);
        } else if (suggestion.label() != null && !suggestion.label().isBlank()) {
            resolved = skillRepository.findByNameIgnoreCase(suggestion.label().trim()).orElse(null);
        }

        if (resolved != null) {
            return new AiSuggestedRequirementResponse(
                    suggestion.requirementType(), resolved.getId(), resolved.getName(), resolved.getName(),
                    suggestion.suggestedWeight(), suggestion.suggestedAcceptedEvidenceSources(),
                    suggestion.suggestedMinimumEvidenceStrength(), true);
        }

        String safeLabel = suggestion.label() != null && !suggestion.label().isBlank()
                ? suggestion.label() : "Unlabeled suggestion — recruiter review required";
        return new AiSuggestedRequirementResponse(
                suggestion.requirementType(), null, null, safeLabel,
                suggestion.suggestedWeight(), suggestion.suggestedAcceptedEvidenceSources(),
                suggestion.suggestedMinimumEvidenceStrength(), false);
    }

    private List<AiEvidenceSuggestionResponse> toSuggestionResponses(List<AiSuggestedEvidence> suggestions) {
        if (suggestions.isEmpty()) {
            return List.of();
        }
        Set<UUID> requirementIds = new HashSet<>();
        for (AiSuggestedEvidence suggestion : suggestions) {
            requirementIds.add(suggestion.requirementId());
        }
        Map<UUID, InternshipRubricRequirement> requirementsById = new HashMap<>();
        for (InternshipRubricRequirement requirement : requirementRepository.findAllById(requirementIds)) {
            requirementsById.put(requirement.getId(), requirement);
        }
        Set<UUID> skillIds = new HashSet<>();
        for (InternshipRubricRequirement requirement : requirementsById.values()) {
            if (requirement.getSkillId() != null) {
                skillIds.add(requirement.getSkillId());
            }
        }
        Map<UUID, Skill> skillsById = new HashMap<>();
        for (Skill skill : skillRepository.findAllById(skillIds)) {
            skillsById.put(skill.getId(), skill);
        }

        List<AiEvidenceSuggestionResponse> responses = new ArrayList<>();
        for (AiSuggestedEvidence suggestion : suggestions) {
            InternshipRubricRequirement requirement = requirementsById.get(suggestion.requirementId());
            if (requirement == null) {
                // Should be unreachable (the AI service only ever returns ids we gave it), but
                // never surface a suggestion we can't attribute to a real requirement.
                continue;
            }
            Skill skill = requirement.getSkillId() != null ? skillsById.get(requirement.getSkillId()) : null;
            responses.add(new AiEvidenceSuggestionResponse(
                    requirement.getId(), requirement.getRequirementType(), requirement.getSkillId(),
                    skill != null ? skill.getName() : null, requirement.getLabel(), requirement.getWeight(),
                    suggestion.excerptQuote(), suggestion.suggestedStrength(), suggestion.uncertain(),
                    suggestion.explanation()));
        }
        return responses;
    }

    private String extractFrozenCvText(InternshipApplication application) {
        try {
            byte[] cvBytes = cvStorageService.read(application.getCvStorageKey());
            String text = cvSkillExtractionService.extractText(cvBytes);
            return (text == null || text.isBlank()) ? null : text;
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    private CompanyProfile findCompanyProfileOrThrow(UUID userId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        return companyAccessResolver.findCompanyProfile(context)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private Internship findOwnedInternshipOrThrow(UUID internshipId, UUID companyProfileId) {
        return internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));
    }

    private InternshipApplication findOwnedApplicationOrThrow(UUID applicationId, UUID internshipId) {
        return applicationRepository.findByIdAndInternshipId(applicationId, internshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private ScreeningReport findOwnedReportOrThrow(UUID reportId, UUID applicationId) {
        return reportRepository.findById(reportId)
                .filter(report -> report.getApplicationId().equals(applicationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening report not found"));
    }
}
