package com.internplatform.screening;

import com.internplatform.ats.access.AtsMemberRole;
import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.CvStorageService;
import com.internplatform.internprofile.EvidenceStrength;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests — no Spring context, no real database, no network. Every repository and
 * both Stage 5A AI interfaces are mocked, matching the existing AnthropicEvidenceAnalysisServiceTest
 * style. HTTP/security-layer concerns (unauthenticated, wrong role, real cross-company 404
 * through the full filter chain) are covered separately by CompanyAiAssistControllerTest.
 */
class CompanyAiAssistServiceTest {

    private final CompanyAccessResolver companyAccessResolver = mock(CompanyAccessResolver.class);
    private final InternshipRepository internshipRepository = mock(InternshipRepository.class);
    private final InternshipApplicationRepository applicationRepository = mock(InternshipApplicationRepository.class);
    private final ScreeningReportRepository reportRepository = mock(ScreeningReportRepository.class);
    private final ScreeningReportResultRepository resultRepository = mock(ScreeningReportResultRepository.class);
    private final InternshipRubricRepository rubricRepository = mock(InternshipRubricRepository.class);
    private final InternshipRubricRequirementRepository requirementRepository = mock(InternshipRubricRequirementRepository.class);
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final CvStorageService cvStorageService = mock(CvStorageService.class);
    private final CvSkillExtractionService cvSkillExtractionService = mock(CvSkillExtractionService.class);
    private final AiEvidenceAnalysisService aiEvidenceAnalysisService = mock(AiEvidenceAnalysisService.class);
    private final AiRubricDraftService aiRubricDraftService = mock(AiRubricDraftService.class);

    private CompanyAiAssistService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyProfileId = UUID.randomUUID();
    private final UUID internshipId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID rubricId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID requirementId = UUID.randomUUID();

    private CompanyProfile companyProfile;
    private Internship internship;
    private InternshipApplication application;
    private InternshipRubric activeRubric;
    private ScreeningReport report;

    @BeforeEach
    void setUp() {
        service = new CompanyAiAssistService(
                companyAccessResolver, internshipRepository, applicationRepository, reportRepository,
                resultRepository, rubricRepository, requirementRepository, skillRepository,
                cvStorageService, cvSkillExtractionService, aiEvidenceAnalysisService, aiRubricDraftService);

        companyProfile = new CompanyProfile(UUID.randomUUID());
        ReflectionTestUtils.setField(companyProfile, "id", companyProfileId);

        internship = new Internship(companyProfileId);
        ReflectionTestUtils.setField(internship, "id", internshipId);

        application = new InternshipApplication(internshipId, UUID.randomUUID());
        ReflectionTestUtils.setField(application, "id", applicationId);
        application.setCvStorageKey("fake-storage-key");

        activeRubric = new InternshipRubric(internshipId, null, 1);
        ReflectionTestUtils.setField(activeRubric, "id", rubricId);
        ReflectionTestUtils.setField(activeRubric, "status", RubricStatus.ACTIVE);

        report = new ScreeningReport(internshipId, applicationId, rubricId, 1, "fake-storage-key", "cv-rubric-matching-v1");
        ReflectionTestUtils.setField(report, "id", reportId);

        CompanyContext context = new CompanyContext(companyProfileId, UUID.randomUUID(), userId, AtsMemberRole.OWNER);
        when(companyAccessResolver.requireActiveMember(userId)).thenReturn(context);
        when(companyAccessResolver.findCompanyProfile(context)).thenReturn(Optional.of(companyProfile));
        when(internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)).thenReturn(Optional.of(internship));
        when(applicationRepository.findByIdAndInternshipId(applicationId, internshipId)).thenReturn(Optional.of(application));
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.ACTIVE)).thenReturn(Optional.of(activeRubric));
    }

    private ScreeningReportResult result(UUID reqId, ScreeningResult outcome) {
        return new ScreeningReportResult(reportId, reqId, outcome);
    }

    private InternshipRubricRequirement requirement(UUID id) {
        InternshipRubricRequirement req = new InternshipRubricRequirement(rubricId, RequirementType.REQUIRED);
        ReflectionTestUtils.setField(req, "id", id);
        ReflectionTestUtils.setField(req, "label", "Java");
        ReflectionTestUtils.setField(req, "weight", 40);
        ReflectionTestUtils.setField(req, "acceptedEvidenceSources", Set.of());
        return req;
    }

    // --- Evidence analysis: ownership / not-found chain -------------------------------------

    @Test
    void returns404WhenTheInternshipIsNotOwnedByTheCallersCompany() {
        when(internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyzeEvidence(userId, internshipId, applicationId, reportId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void returns404WhenTheApplicationDoesNotBelongToThisInternship() {
        when(applicationRepository.findByIdAndInternshipId(applicationId, internshipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyzeEvidence(userId, internshipId, applicationId, reportId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void returns404WhenTheReportBelongsToADifferentApplication() {
        ScreeningReport otherReport = new ScreeningReport(
                internshipId, UUID.randomUUID(), rubricId, 1, "other-key", "cv-rubric-matching-v1");
        ReflectionTestUtils.setField(otherReport, "id", reportId);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(otherReport));

        assertThatThrownBy(() -> service.analyzeEvidence(userId, internshipId, applicationId, reportId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void returns409WhenTheInternshipHasNoActiveRubric() {
        when(rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyzeEvidence(userId, internshipId, applicationId, reportId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void returns409WhenTheReportsRubricIsNoLongerTheActiveOne() {
        InternshipRubric newerRubric = new InternshipRubric(internshipId, null, 2);
        ReflectionTestUtils.setField(newerRubric, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(newerRubric, "status", RubricStatus.ACTIVE);
        when(rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.ACTIVE)).thenReturn(Optional.of(newerRubric));

        assertThatThrownBy(() -> service.analyzeEvidence(userId, internshipId, applicationId, reportId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    // --- Eligible-requirement filtering -------------------------------------------------------

    @Test
    void onlyNeedsReviewAndInsufficientEvidenceRequirementsAreEverSentToAi() {
        UUID matchedReqId = UUID.randomUUID();
        UUID needsReviewReqId = UUID.randomUUID();
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(matchedReqId, ScreeningResult.MATCHED),
                result(needsReviewReqId, ScreeningResult.NEEDS_REVIEW)
        ));
        when(cvStorageService.read("fake-storage-key")).thenReturn(new byte[]{1, 2, 3});
        when(cvSkillExtractionService.extractText(any())).thenReturn("some cv text");
        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        org.mockito.ArgumentCaptor<AiEvidenceAnalysisRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiEvidenceAnalysisRequest.class);
        verify(aiEvidenceAnalysisService).analyzeEvidence(captor.capture());
        assertThat(captor.getValue().requirementIdsNeedingAnalysis()).containsExactly(needsReviewReqId);
    }

    @Test
    void clientSuppliedRequirementIdsAreOnlyEverIntersectedNeverTrustedOutright() {
        UUID needsReviewReqId = UUID.randomUUID();
        UUID matchedReqId = UUID.randomUUID();
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(needsReviewReqId, ScreeningResult.NEEDS_REVIEW),
                result(matchedReqId, ScreeningResult.MATCHED)
        ));
        when(cvStorageService.read("fake-storage-key")).thenReturn(new byte[]{1});
        when(cvSkillExtractionService.extractText(any())).thenReturn("cv text");
        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        // Client asks for the MATCHED one (not eligible) plus a totally unrelated random id —
        // neither should ever reach the AI request.
        AiEvidenceAnalysisApiRequest clientRequest =
                new AiEvidenceAnalysisApiRequest(List.of(matchedReqId, UUID.randomUUID()));

        AiEvidenceAnalysisResponse response =
                service.analyzeEvidence(userId, internshipId, applicationId, reportId, clientRequest);

        assertThat(response.completed()).isFalse();
        assertThat(response.unavailableReason()).containsIgnoringCase("eligible");
        verify(aiEvidenceAnalysisService, never()).analyzeEvidence(any());
    }

    @Test
    void returnsCompletedFalseWithoutCallingAiWhenNoRequirementIsEligible() {
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(UUID.randomUUID(), ScreeningResult.MATCHED),
                result(UUID.randomUUID(), ScreeningResult.NOT_EVIDENCED)
        ));

        AiEvidenceAnalysisResponse response = service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        assertThat(response.completed()).isFalse();
        verify(aiEvidenceAnalysisService, never()).analyzeEvidence(any());
    }

    // --- Frozen CV snapshot handling ----------------------------------------------------------

    @Test
    void returnsCompletedFalseWithoutCallingAiWhenTheFrozenCvCannotBeRead() {
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(requirementId, ScreeningResult.NEEDS_REVIEW)));
        when(cvStorageService.read("fake-storage-key"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "not found"));

        AiEvidenceAnalysisResponse response = service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        assertThat(response.completed()).isFalse();
        assertThat(response.unavailableReason()).containsIgnoringCase("CV");
        verify(aiEvidenceAnalysisService, never()).analyzeEvidence(any());
    }

    @Test
    void usesTheApplicationsFrozenSnapshotNeverAnyLiveProfileCv() {
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(requirementId, ScreeningResult.NEEDS_REVIEW)));
        when(cvStorageService.read("fake-storage-key")).thenReturn(new byte[]{9, 9, 9});
        when(cvSkillExtractionService.extractText(any())).thenReturn("frozen snapshot text");
        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        org.mockito.ArgumentCaptor<AiEvidenceAnalysisRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiEvidenceAnalysisRequest.class);
        verify(aiEvidenceAnalysisService).analyzeEvidence(captor.capture());
        assertThat(captor.getValue().authorizedCvText()).isEqualTo("frozen snapshot text");
        verify(cvStorageService).read("fake-storage-key");
    }

    // --- Response mapping: valid and fabricated-style suggestions -----------------------------

    @Test
    void mapsAValidatedSuggestionBackToItsOwningRequirementForDisplay() {
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(requirementId, ScreeningResult.NEEDS_REVIEW)));
        when(cvStorageService.read("fake-storage-key")).thenReturn(new byte[]{1});
        when(cvSkillExtractionService.extractText(any())).thenReturn("cv text");
        when(requirementRepository.findAllById(anyIterable())).thenReturn(List.of(requirement(requirementId)));

        AiSuggestedEvidence suggestion = new AiSuggestedEvidence(
                requirementId, null, "Built REST APIs using Java", EvidenceStrength.STRONG, false, "work experience");
        when(aiEvidenceAnalysisService.analyzeEvidence(any()))
                .thenReturn(new AiEvidenceAnalysisResult(true, List.of(suggestion), null));

        AiEvidenceAnalysisResponse response = service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        assertThat(response.completed()).isTrue();
        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.suggestions().get(0).requirementId()).isEqualTo(requirementId);
        assertThat(response.suggestions().get(0).label()).isEqualTo("Java");
        assertThat(response.suggestions().get(0).excerptQuote()).isEqualTo("Built REST APIs using Java");
    }

    @Test
    void silentlyDropsASuggestionReferencingARequirementItCannotAttribute() {
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(requirementId, ScreeningResult.NEEDS_REVIEW)));
        when(cvStorageService.read("fake-storage-key")).thenReturn(new byte[]{1});
        when(cvSkillExtractionService.extractText(any())).thenReturn("cv text");
        // Simulates a fabricated/unexpected id slipping through the AI layer — the requirement
        // lookup simply returns nothing for it, so it must never be surfaced.
        when(requirementRepository.findAllById(anyIterable())).thenReturn(List.of());

        AiSuggestedEvidence fabricated = new AiSuggestedEvidence(
                requirementId, null, "fabricated", EvidenceStrength.STRONG, false, "x");
        when(aiEvidenceAnalysisService.analyzeEvidence(any()))
                .thenReturn(new AiEvidenceAnalysisResult(true, List.of(fabricated), null));

        AiEvidenceAnalysisResponse response = service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        assertThat(response.suggestions()).isEmpty();
    }

    // --- Never mutates anything -----------------------------------------------------------------

    @Test
    void neverWritesToAnyRepositoryDuringEvidenceAnalysis() {
        when(resultRepository.findAllByScreeningReportId(reportId)).thenReturn(List.of(
                result(requirementId, ScreeningResult.INSUFFICIENT_EVIDENCE)));
        when(cvStorageService.read("fake-storage-key")).thenReturn(new byte[]{1});
        when(cvSkillExtractionService.extractText(any())).thenReturn("cv text");
        when(requirementRepository.findAllById(anyIterable())).thenReturn(List.of(requirement(requirementId)));
        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        service.analyzeEvidence(userId, internshipId, applicationId, reportId, null);

        verify(reportRepository, never()).save(any());
        verify(reportRepository, never()).saveAndFlush(any());
        verify(resultRepository, never()).save(any());
        verify(resultRepository, never()).saveAll(any());
    }

    // --- Rubric draft: ownership, skill resolution, safe handling -----------------------------

    @Test
    void draftRubricReturns404WhenTheInternshipIsNotOwned() {
        when(internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.draftRubric(userId, internshipId, new AiRubricDraftApiRequest("Need a Java intern")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void resolvesACanonicalSkillIdTheAiAlreadyProvided() {
        UUID skillId = UUID.randomUUID();
        Skill java = new Skill("Java");
        ReflectionTestUtils.setField(java, "id", skillId);
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(java));

        AiSuggestedRequirement suggestion = new AiSuggestedRequirement(
                RequirementType.REQUIRED, skillId, null, 40, Set.of(), null);
        when(aiRubricDraftService.draftFromInstructions(any()))
                .thenReturn(new AiRubricDraftResult(true, List.of(suggestion), null));

        AiRubricDraftResponse response = service.draftRubric(userId, internshipId, new AiRubricDraftApiRequest("Need a Java intern"));

        assertThat(response.suggestedRequirements()).hasSize(1);
        assertThat(response.suggestedRequirements().get(0).skillResolved()).isTrue();
        assertThat(response.suggestedRequirements().get(0).resolvedSkillId()).isEqualTo(skillId);
    }

    @Test
    void resolvesAFreeTextLabelByExactCatalogNameMatch() {
        UUID skillId = UUID.randomUUID();
        Skill sql = new Skill("SQL");
        ReflectionTestUtils.setField(sql, "id", skillId);
        when(skillRepository.findByNameIgnoreCase("SQL")).thenReturn(Optional.of(sql));

        AiSuggestedRequirement suggestion = new AiSuggestedRequirement(
                RequirementType.PREFERRED, null, "SQL", 20, Set.of(), null);
        when(aiRubricDraftService.draftFromInstructions(any()))
                .thenReturn(new AiRubricDraftResult(true, List.of(suggestion), null));

        AiRubricDraftResponse response = service.draftRubric(userId, internshipId, new AiRubricDraftApiRequest("Need SQL knowledge"));

        assertThat(response.suggestedRequirements().get(0).skillResolved()).isTrue();
        assertThat(response.suggestedRequirements().get(0).resolvedSkillId()).isEqualTo(skillId);
    }

    @Test
    void leavesAnAmbiguousOrUnknownSkillUnresolvedRatherThanGuessing() {
        when(skillRepository.findByNameIgnoreCase("Some made-up skill")).thenReturn(Optional.empty());

        AiSuggestedRequirement suggestion = new AiSuggestedRequirement(
                RequirementType.BONUS, null, "Some made-up skill", 10, Set.of(), null);
        when(aiRubricDraftService.draftFromInstructions(any()))
                .thenReturn(new AiRubricDraftResult(true, List.of(suggestion), null));

        AiRubricDraftResponse response = service.draftRubric(userId, internshipId, new AiRubricDraftApiRequest("Something unusual"));

        assertThat(response.suggestedRequirements().get(0).skillResolved()).isFalse();
        assertThat(response.suggestedRequirements().get(0).resolvedSkillId()).isNull();
        assertThat(response.suggestedRequirements().get(0).label()).isEqualTo("Some made-up skill");
    }

    @Test
    void draftRubricNeverWritesToAnyRubricOrRequirementRepository() {
        when(aiRubricDraftService.draftFromInstructions(any()))
                .thenReturn(new AiRubricDraftResult(true, List.of(), null));

        service.draftRubric(userId, internshipId, new AiRubricDraftApiRequest("Need a Java intern"));

        verify(rubricRepository, never()).save(any());
        verify(rubricRepository, never()).saveAndFlush(any());
        verify(requirementRepository, never()).save(any());
        verify(requirementRepository, never()).saveAll(any());
    }

    @Test
    void draftRubricPassesTheRecruitersCompanyIdForRateLimitingNotAClientSuppliedOne() {
        when(aiRubricDraftService.draftFromInstructions(any()))
                .thenReturn(new AiRubricDraftResult(false, List.of(), "disabled"));

        service.draftRubric(userId, internshipId, new AiRubricDraftApiRequest("Need a Java intern"));

        org.mockito.ArgumentCaptor<AiRubricDraftRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRubricDraftRequest.class);
        verify(aiRubricDraftService).draftFromInstructions(captor.capture());
        assertThat(captor.getValue().companyProfileId()).isEqualTo(companyProfileId);
    }
}
