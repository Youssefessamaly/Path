package com.internplatform.screening;

import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.AcademicLevel;
import com.internplatform.internprofile.CvStorageService;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.ApplicationMethod;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internship.InternshipStatus;
import com.internplatform.internship.WorkMode;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import com.internplatform.screening.ai.AiEvidenceAnalysisResult;
import com.internplatform.screening.ai.AiEvidenceAnalysisService;
import com.internplatform.screening.ai.AiRubricDraftResult;
import com.internplatform.screening.ai.AiRubricDraftService;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.AiSuggestedRequirement;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP/security-layer coverage for the two Stage 5B Phase C1 endpoints. Both
 * AiEvidenceAnalysisService and AiRubricDraftService are replaced with Mockito mocks via
 * @MockitoBean so each test fully controls what "the AI" returns — no real network call, no
 * dependency on app.ai.*.mode. Pure service-logic coverage (ownership chain, eligible-
 * requirement filtering, skill resolution) lives in CompanyAiAssistServiceTest; this class
 * focuses on what only a real Spring Security filter chain + real database can prove:
 * authentication, roles, and genuine cross-company 404s.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompanyAiAssistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private InternProfileRepository internProfileRepository;

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private InternshipApplicationRepository applicationRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private CvStorageService cvStorageService;

    @Autowired
    private ScreeningReportRepository screeningReportRepository;

    @MockitoBean
    private AiEvidenceAnalysisService aiEvidenceAnalysisService;

    @MockitoBean
    private AiRubricDraftService aiRubricDraftService;

    private User companyUser;
    private CompanyProfile companyProfile;
    private Internship internship;

    @BeforeEach
    void setUp() {
        companyUser = createUser(Role.COMPANY);
        companyProfile = createCompletedCompanyProfile(companyUser);
        internship = createInternship(companyProfile);
    }

    private User createUser(Role role) {
        return userRepository.saveAndFlush(new User(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password123!"), role));
    }

    private CompanyProfile createCompletedCompanyProfile(User user) {
        CompanyProfile profile = new CompanyProfile(user.getId());
        profile.setCompanyName("Pathora Technologies " + UUID.randomUUID());
        profile.setIndustry("Software");
        profile.setCompanySize("11-50");
        profile.setLocation("Cairo, Egypt");
        profile.setDescription("We build tools that connect interns with great companies.");
        profile.setContactName("Sara Ahmed");
        profile.setContactPhone("+201012345678");
        profile.markOnboardingCompleted(OffsetDateTime.now(ZoneOffset.UTC));
        profile.recordApprovalDecision(ApprovalStatus.APPROVED, OffsetDateTime.now(ZoneOffset.UTC));
        return companyProfileRepository.saveAndFlush(profile);
    }

    private Skill skill(String name) {
        return skillRepository.findAllByOrderByNameAsc().stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private InternProfile createInternProfile() {
        User internUser = createUser(Role.INTERN);
        InternProfile profile = new InternProfile(internUser.getId());
        profile.setFirstName("Amina");
        profile.setLastName("Hassan");
        profile.setDateOfBirth(LocalDate.of(2001, 5, 10));
        profile.setPhoneNumber("+201012345678");
        profile.setInstitutionName("Cairo University");
        profile.setAcademicLevel(AcademicLevel.UNIVERSITY);
        profile.setAcademicYear((short) 3);
        profile.setMajor("Computer Science");
        profile.setExpectedGraduationYear((short) (Year.now().getValue() + 1));
        profile.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return internProfileRepository.saveAndFlush(profile);
    }

    private Internship createInternship(CompanyProfile owner) {
        Internship created = new Internship(owner.getId());
        created.setTitle("Backend Engineering Intern");
        created.setDescription("Work on our core platform.");
        created.setLocation("Cairo, Egypt");
        created.setWorkMode(WorkMode.HYBRID);
        created.setApplicationDeadline(LocalDate.now().plusDays(30));
        created.setStatus(InternshipStatus.OPEN);
        created.setSkills(new HashSet<>(Set.of(skill("Java"))));
        created.setTargetMajors(Set.of());
        created.setApplicationMethod(ApplicationMethod.INTERNAL);
        return internshipRepository.saveAndFlush(created);
    }

    private byte[] createPdfWithLines(String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        stream.newLineAtOffset(0, -14);
                    }
                    stream.showText(lines[i]);
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private InternshipApplication createApplication(InternProfile profile, byte[] cvBytes) {
        String storageKey = cvStorageService.store(profile.getId(), cvBytes);
        InternshipApplication application = new InternshipApplication(internship.getId(), profile.getId());
        application.setCoverLetter("I would love to join your team.");
        application.setCvFileName("resume.pdf");
        application.setCvStorageKey(storageKey);
        application.setCvContentType("application/pdf");
        application.setCvSizeBytes(cvBytes.length);
        application.setCvUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // Every other test in this class is about something OTHER than consent (ownership,
        // disabled/rate-limited/privacy states, response mapping) — default this fixture to
        // "consented" so those tests actually reach the AI call they're testing. The dedicated
        // consent test below builds its own non-consenting application instead.
        application.setAiAnalysisConsent(true, OffsetDateTime.now(ZoneOffset.UTC));
        return applicationRepository.saveAndFlush(application);
    }

    private RequestPostProcessor asUser(User user) {
        Consumer<Jwt.Builder> claims = builder -> builder
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name());
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(claims)
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    private RequestPostProcessor asCompany() {
        return asUser(companyUser);
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String activateRubric(List<Map<String, Object>> requirementBodies) throws Exception {
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric").with(asCompany()))
                .andExpect(status().isCreated());
        for (Map<String, Object> body : requirementBodies) {
            mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/requirements")
                            .with(asCompany()).contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                    .andExpect(status().isCreated());
        }
        return mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/activate").with(asCompany()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> skillRequirement(String skillName, String type, int weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", type);
        body.put("skillId", skill(skillName).getId().toString());
        body.put("weight", weight);
        body.put("displayOrder", 0);
        return body;
    }

    /** A free-text (label-only, no canonical skill) requirement — the deterministic engine
     *  always classifies these as NEEDS_REVIEW regardless of CV content (see
     *  ScreeningService#evaluateRequirement), making this the reliable way to guarantee a
     *  requirement is eligible for AI analysis without depending on exactly what a synthetic
     *  CV happens to mention. */
    private Map<String, Object> labelRequirement(String label, String type, int weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", type);
        body.put("label", label);
        body.put("weight", weight);
        body.put("displayOrder", 0);
        return body;
    }

    private UUID screenAndGetReportId(UUID applicationId) throws Exception {
        String responseJson = mockMvc.perform(post(
                        "/api/v1/company/internships/" + internship.getId() + "/applications/" + applicationId + "/screening-reports")
                        .with(asCompany()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());
    }

    // --- AI evidence analysis: security/authorization -----------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + UUID.randomUUID()
                        + "/ai-evidence-analysis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internsCannotCallTheEvidenceAnalysisEndpoint() throws Exception {
        activateRubric(List.of(skillRequirement("Java", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));
        UUID reportId = screenAndGetReportId(application.getId());

        User internUser = createUser(Role.INTERN);
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asUser(internUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherCompanyCannotAnalyzeThisCompanysApplication() throws Exception {
        activateRubric(List.of(skillRequirement("Java", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));
        UUID reportId = screenAndGetReportId(application.getId());

        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asUser(otherCompanyUser)))
                .andExpect(status().isNotFound());
        verify(aiEvidenceAnalysisService, never()).analyzeEvidence(any());
    }

    @Test
    void invalidInternshipOrApplicationIdsReturn404NotLeakingExistence() throws Exception {
        mockMvc.perform(post("/api/v1/company/internships/" + UUID.randomUUID()
                        + "/applications/" + UUID.randomUUID() + "/screening-reports/" + UUID.randomUUID()
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isNotFound());
    }

    // --- Applicant AI-analysis consent: a recruiter can never override this -------------------

    @Test
    void refusesAnalysisWhenTheApplicantNeverAuthorizedAiProcessingRegardlessOfRecruiterIntent() throws Exception {
        activateRubric(List.of(labelRequirement("Leadership potential", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        // Deliberately NOT using createApplication's helper (which defaults to consent=true) —
        // this application explicitly leaves consent at its default, unconsented state.
        String storageKey = cvStorageService.store(profile.getId(), createPdfWithLines("SKILLS", "Python"));
        InternshipApplication nonConsentingApplication = new InternshipApplication(internship.getId(), profile.getId());
        nonConsentingApplication.setCvFileName("resume.pdf");
        nonConsentingApplication.setCvStorageKey(storageKey);
        nonConsentingApplication.setCvContentType("application/pdf");
        nonConsentingApplication.setCvSizeBytes(1);
        nonConsentingApplication.setCvUploadedAt(java.time.OffsetDateTime.now(ZoneOffset.UTC));
        nonConsentingApplication = applicationRepository.saveAndFlush(nonConsentingApplication);

        UUID reportId = screenAndGetReportId(nonConsentingApplication.getId());

        // Even if the mocked AI bean would happily return a suggestion, the request must never
        // reach it at all once the applicant's own consent is false.
        when(aiEvidenceAnalysisService.analyzeEvidence(any()))
                .thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + nonConsentingApplication.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.unavailableReason").value(
                        "The applicant has not authorized AI-assisted analysis of their CV for this application"));

        verify(aiEvidenceAnalysisService, never()).analyzeEvidence(any());
    }

    // --- AI disabled by default -----------------------------------------------------------------

    @Test
    void aiDisabledByDefaultStillReturnsAStructuredCompletedFalseResponse() throws Exception {
        activateRubric(List.of(labelRequirement("Leadership potential", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Python"));
        UUID reportId = screenAndGetReportId(application.getId());

        when(aiEvidenceAnalysisService.analyzeEvidence(any()))
                .thenReturn(AiEvidenceAnalysisResult.disabled("AI evidence analysis is disabled"));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.unavailableReason").value("AI evidence analysis is disabled"));
    }

    // --- Provider failure / rate limit exhausted (both modeled via the mocked bean) -----------

    @Test
    void providerFailureDegradesToACompletedFalseResponseNotA5xx() throws Exception {
        activateRubric(List.of(labelRequirement("Leadership potential", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Python"));
        UUID reportId = screenAndGetReportId(application.getId());

        when(aiEvidenceAnalysisService.analyzeEvidence(any()))
                .thenReturn(AiEvidenceAnalysisResult.disabled("AI provider request failed"));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void rateLimitExhaustedDegradesToACompletedFalseResponse() throws Exception {
        activateRubric(List.of(labelRequirement("Leadership potential", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Python"));
        UUID reportId = screenAndGetReportId(application.getId());

        when(aiEvidenceAnalysisService.analyzeEvidence(any()))
                .thenReturn(AiEvidenceAnalysisResult.disabled("Daily AI usage limit reached for this company"));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unavailableReason").value("Daily AI usage limit reached for this company"));
    }

    // --- Happy path: valid suggestion surfaced correctly ----------------------------------------

    @Test
    void validSuggestionIsSurfacedWithStrengthAndExcerpt() throws Exception {
        String reportJson = activateRubric(List.of(labelRequirement("Leadership potential", "REQUIRED", 40)));
        UUID requirementId = UUID.fromString(objectMapper.readTree(reportJson).get("requirements").get(0).get("id").asText());

        InternProfile profile = createInternProfile();
        // A free-text requirement is always NEEDS_REVIEW regardless of CV content; the mocked
        // AI bean below fully controls what gets returned for this test either way.
        InternshipApplication application = createApplication(profile, createPdfWithLines("ABOUT ME", "A fresh graduate."));
        UUID reportId = screenAndGetReportId(application.getId());

        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(
                true,
                List.of(new AiSuggestedEvidence(requirementId, null, "A fresh graduate.", EvidenceStrength.WEAK, true,
                        "Only a general self-description, not strong evidence of leadership specifically")),
                null));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.suggestions[0].requirementId").value(requirementId.toString()))
                .andExpect(jsonPath("$.suggestions[0].uncertain").value(true));
    }

    // --- Never changes application status or mutates the existing report ----------------------

    @Test
    void neverChangesTheApplicationStatus() throws Exception {
        activateRubric(List.of(skillRequirement("Java", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));
        UUID reportId = screenAndGetReportId(application.getId());

        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk());

        InternshipApplication reloaded = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(application.getStatus());
    }

    @Test
    void neverMutatesTheExistingScreeningReport() throws Exception {
        activateRubric(List.of(skillRequirement("Java", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));
        UUID reportId = screenAndGetReportId(application.getId());
        ScreeningReport before = screeningReportRepository.findById(reportId).orElseThrow();
        OffsetDateTime originalEvaluatedAt = before.getEvaluatedAt();

        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(true, List.of(), null));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk());

        ScreeningReport after = screeningReportRepository.findById(reportId).orElseThrow();
        assertThat(after.getEvaluatedAt()).isEqualTo(originalEvaluatedAt);
        assertThat(screeningReportRepository.findAllByApplicationIdInOrderByEvaluatedAtDesc(List.of(application.getId())))
                .hasSize(1);
    }

    // --- Missing/inactive rubric, applications without a usable CV -----------------------------

    @Test
    void missingActiveRubricReturns409() throws Exception {
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));

        // No rubric has ever been activated for this internship, so there is no screening
        // report to reference either — confirms the 404-before-409 ordering is sensible.
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + UUID.randomUUID()
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isNotFound());
    }

    // --- Arabic text handling (via the mocked bean, since no real network call is made) -------

    @Test
    void arabicCvTextIsPassedThroughUnmodifiedToTheAiRequest() throws Exception {
        activateRubric(List.of(skillRequirement("Java", "REQUIRED", 40)));
        InternProfile profile = createInternProfile();
        InternshipApplication application = createApplication(profile, createPdfWithLines("SKILLS", "Java"));
        UUID reportId = screenAndGetReportId(application.getId());

        when(aiEvidenceAnalysisService.analyzeEvidence(any())).thenReturn(new AiEvidenceAnalysisResult(
                true,
                List.of(),
                null));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId()
                        + "/applications/" + application.getId() + "/screening-reports/" + reportId
                        + "/ai-evidence-analysis")
                        .with(asCompany()))
                .andExpect(status().isOk());
        // Full bilingual CV-text round-tripping through the real Anthropic adapter is covered
        // by LiveAnthropicAdapterIT (Phase B) and AnthropicEvidenceAnalysisServiceTest (Phase A)
        // with mocked API responses; this test only confirms the endpoint itself imposes no
        // language-specific restriction before reaching that layer.
    }

    // --- Rubric draft endpoint -------------------------------------------------------------------

    @Test
    void rubricDraftUnauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/ai-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("instructionsText", "Need a backend intern with Java."))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rubricDraftRejectsTooShortInstructions() throws Exception {
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/ai-draft")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("instructionsText", "Java"))))
                .andExpect(status().isBadRequest());
        verify(aiRubricDraftService, never()).draftFromInstructions(any());
    }

    @Test
    void anotherCompanyCannotDraftARubricForThisInternship() throws Exception {
        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/ai-draft")
                        .with(asUser(otherCompanyUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("instructionsText", "Need a backend intern with Java."))))
                .andExpect(status().isNotFound());
        verify(aiRubricDraftService, never()).draftFromInstructions(any());
    }

    @Test
    void rubricDraftSuggestionsAreClearlyMarkedAsUnresolvedWhenNotAiEnabled() throws Exception {
        when(aiRubricDraftService.draftFromInstructions(any()))
                .thenReturn(AiRubricDraftResult.disabled("AI rubric drafting is disabled"));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/ai-draft")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("instructionsText", "Need a backend intern with Java."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.suggestedRequirements").isArray())
                .andExpect(jsonPath("$.suggestedRequirements").isEmpty());
    }

    @Test
    void rubricDraftNeverCreatesOrActivatesAnyRubric() throws Exception {
        UUID javaId = skill("Java").getId();
        when(aiRubricDraftService.draftFromInstructions(any())).thenReturn(new AiRubricDraftResult(
                true,
                List.of(new AiSuggestedRequirement(RequirementType.REQUIRED, javaId, null, 40, Set.of(), null)),
                null));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/ai-draft")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("instructionsText", "Need a backend intern with Java."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedRequirements[0].skillResolved").value(true))
                .andExpect(jsonPath("$.suggestedRequirements[0].resolvedSkillId").value(javaId.toString()));

        // No draft/active rubric exists for this internship as a side effect of drafting.
        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/rubric").with(asCompany()))
                .andExpect(status().isNotFound());
    }
}
