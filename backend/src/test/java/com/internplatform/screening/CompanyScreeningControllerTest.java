package com.internplatform.screening;

import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.AcademicLevel;
import com.internplatform.internprofile.CvStorageService;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.ApplicationMethod;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internship.InternshipStatus;
import com.internplatform.internship.WorkMode;
import com.internplatform.internshipapplication.ApplicationStatus;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompanyScreeningControllerTest {

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
        return userRepository.saveAndFlush(
                new User(
                        role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                        passwordEncoder.encode("Password123!"),
                        role
                )
        );
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
                .filter(s -> s.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private InternProfile createInternProfile(Set<Skill> manualSkills) {
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
        profile.setSkills(manualSkills);
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

    /** Has a valid "%PDF-" signature but no real PDF structure — PDFBox cannot extract text
     *  from it, simulating an unparseable/corrupt CV snapshot. */
    private byte[] unparseablePdfBytes() {
        return "%PDF-1.4\nnot a real pdf body\n%%EOF".getBytes(StandardCharsets.US_ASCII);
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
        return applicationRepository.saveAndFlush(application);
    }

    private RequestPostProcessor asUser(User user) {
        Consumer<Jwt.Builder> claims = builder -> builder
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name());
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(claims)
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    private RequestPostProcessor asCompany() {
        return asUser(companyUser);
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> skillRequirement(String skillName, String type, int weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", type);
        body.put("skillId", skill(skillName).getId().toString());
        body.put("weight", weight);
        body.put("displayOrder", 0);
        return body;
    }

    private String screenUrl(UUID applicationId) {
        return "/api/v1/company/internships/" + internship.getId() + "/applications/" + applicationId + "/screening-reports";
    }

    /** Creates a draft rubric, adds each requirement body via the real Stage 3 API, then
     *  activates it — exercising Stage 3 and Stage 4 together, the way a recruiter actually
     *  would. */
    private String activateRubric(List<Map<String, Object>> requirementBodies) throws Exception {
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric").with(asCompany()))
                .andExpect(status().isCreated());
        for (Map<String, Object> body : requirementBodies) {
            mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/requirements")
                            .with(asCompany())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated());
        }
        return mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/activate").with(asCompany()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // --- Core matching: REQUIRED/PREFERRED/BONUS, evidence strength, evidence sources -----

    @Test
    void requiredRequirementMatchedWithQualifyingProjectEvidence() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        req.put("acceptedEvidenceSources", List.of("PROJECT", "WORK_EXPERIENCE"));
        req.put("minimumEvidenceStrength", "STRONG");
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile,
                createPdfWithLines("Projects", "Built a Java-based service for inventory management."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRequiredCount").value(1))
                .andExpect(jsonPath("$.matchedRequiredCount").value(1))
                .andExpect(jsonPath("$.weightedCoveragePercentage").value(100))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.results[0].result").value("MATCHED"))
                .andExpect(jsonPath("$.results[0].evidenceSource").value("PROJECT"))
                .andExpect(jsonPath("$.results[0].evidenceStrength").value("STRONG"))
                .andExpect(jsonPath("$.results[0].textReference").isNotEmpty());
    }

    @Test
    void requiredPreferredAndBonusCountedIndependently() throws Exception {
        Map<String, Object> required = skillRequirement("Java", "REQUIRED", 80);
        Map<String, Object> preferred = skillRequirement("Python", "PREFERRED", 15);
        Map<String, Object> bonus = skillRequirement("SQL", "BONUS", 5);
        activateRubric(List.of(required, preferred, bonus));

        InternProfile profile = createInternProfile(Set.of());
        // Java and SQL evidenced (any section is fine — no restriction configured); Python is not.
        InternshipApplication application = createApplication(profile,
                createPdfWithLines("Experienced with Java and SQL."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRequiredCount").value(1))
                .andExpect(jsonPath("$.matchedRequiredCount").value(1))
                .andExpect(jsonPath("$.totalPreferredCount").value(1))
                .andExpect(jsonPath("$.matchedPreferredCount").value(0))
                .andExpect(jsonPath("$.totalBonusCount").value(1))
                .andExpect(jsonPath("$.matchedBonusCount").value(1));
    }

    @Test
    void minimumEvidenceStrengthIsEnforced() throws Exception {
        Map<String, Object> req = skillRequirement("Python", "REQUIRED", 80);
        req.put("minimumEvidenceStrength", "STRONG");
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        // Bare skills-list mention only — WEAK, not STRONG.
        InternshipApplication application = createApplication(profile, createPdfWithLines("Skills", "Python"));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].result").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.results[0].evidenceStrength").value("WEAK"))
                .andExpect(jsonPath("$.matchedRequiredCount").value(0));
    }

    @Test
    void acceptedEvidenceSourcesAreEnforced() throws Exception {
        Map<String, Object> req = skillRequirement("Python", "REQUIRED", 80);
        req.put("acceptedEvidenceSources", List.of("WORK_EXPERIENCE"));
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        // Strong evidence, but from the wrong section — Projects, not Work Experience.
        InternshipApplication application = createApplication(profile,
                createPdfWithLines("Projects", "Built a Python data pipeline."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].result").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.results[0].evidenceSource").value("PROJECT"));
    }

    @Test
    void manualSkillAloneCannotSatisfyARestrictedRequirement() throws Exception {
        Map<String, Object> req = skillRequirement("SQL", "REQUIRED", 80);
        req.put("acceptedEvidenceSources", List.of("PROJECT"));
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of(skill("SQL")));
        InternshipApplication application = createApplication(profile, createPdfWithLines("Looking for new opportunities."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].result").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.matchedRequiredCount").value(0));
    }

    @Test
    void manualSkillSatisfiesAnUnrestrictedRequirement() throws Exception {
        Map<String, Object> req = skillRequirement("SQL", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of(skill("SQL")));
        InternshipApplication application = createApplication(profile, createPdfWithLines("Looking for new opportunities."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].result").value("MATCHED"))
                .andExpect(jsonPath("$.results[0].evidenceSource").doesNotExist())
                .andExpect(jsonPath("$.matchedRequiredCount").value(1));
    }

    @Test
    void duplicateCvMentionsAcrossSectionsProduceOneResultWithStrongestEvidence() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile,
                createPdfWithLines("Skills", "Java", "", "Projects", "Built a Java-based inventory system."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].evidenceSource").value("PROJECT"))
                .andExpect(jsonPath("$.results[0].evidenceStrength").value("STRONG"));
    }

    @Test
    void labelOnlyRequirementAlwaysNeedsReview() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("requirementType", "BONUS");
        req.put("label", "Valid driving license");
        req.put("weight", 10);
        req.put("displayOrder", 0);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Experienced driver with a license."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].result").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.needsReview").value(true));
    }

    @Test
    void unparseableCvProducesNeedsReviewNotAFalseNegative() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, unparseablePdfBytes());

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cvTextUnavailable").value(true))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.results[0].result").value("NEEDS_REVIEW"));
    }

    @Test
    void confidentNotEvidencedOnlyWhenCvIsReadableAndSkillTrulyAbsent() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile,
                createPdfWithLines("Looking forward to a great opportunity."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cvTextUnavailable").value(false))
                .andExpect(jsonPath("$.results[0].result").value("NOT_EVIDENCED"));
    }

    // --- No automatic rejection / application statuses unchanged ---------------------------

    @Test
    void screeningNeverChangesTheApplicationStatus() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("No relevant skills here."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany())).andExpect(status().isCreated());

        InternshipApplication reloaded = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    // --- Frozen rubric version / historical report preservation ---------------------------

    @Test
    void screeningRequiresAnActiveRubric() throws Exception {
        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isConflict());
    }

    @Test
    void reportReferencesTheFrozenRubricVersionEvenAfterANewerVersionIsActivated() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));

        String firstReport = mockMvc.perform(post(screenUrl(application.getId())).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rubricVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID firstReportId = UUID.fromString(objectMapper.readTree(firstReport).get("id").asText());

        // Activate a second rubric version (archives v1) without touching v1's own rows.
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/duplicate").with(asCompany()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/rubric/activate").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        // The earlier report must still exist, unmodified, still citing version 1.
        ScreeningReport stillThere = screeningReportRepository.findById(firstReportId).orElseThrow();
        assertThat(stillThere.getRubricVersion()).isEqualTo(1);
    }

    @Test
    void reScreeningCreatesANewReportRatherThanOverwritingHistory() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));

        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany())).andExpect(status().isCreated());
        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany())).andExpect(status().isCreated());

        List<ScreeningReport> all = screeningReportRepository.findAllByApplicationIdInOrderByEvaluatedAtDesc(List.of(application.getId()));
        assertThat(all).hasSize(2);

        mockMvc.perform(get(screenUrl(application.getId()) + "/latest").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(all.get(0).getId().toString()));
    }

    @Test
    void getLatestReportReturns404WhenNeverScreened() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));

        mockMvc.perform(get(screenUrl(application.getId()) + "/latest").with(asCompany()))
                .andExpect(status().isNotFound());
    }

    // --- Authorization ----------------------------------------------------------------------

    @Test
    void anotherCompanyCannotScreenOrReadReports() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));
        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany())).andExpect(status().isCreated());

        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);

        mockMvc.perform(post(screenUrl(application.getId())).with(asUser(otherCompanyUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(screenUrl(application.getId()) + "/latest").with(asUser(otherCompanyUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/screening-reports").with(asUser(otherCompanyUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void internCannotAccessScreeningEndpoints() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));
        User internUser = createUser(Role.INTERN);

        mockMvc.perform(post(screenUrl(application.getId())).with(asUser(internUser)))
                .andExpect(status().isForbidden());
    }

    // --- Batch limits and pagination ---------------------------------------------------------

    @Test
    void batchScreeningRejectsMoreThanMaxBatchSize() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            tooMany.add(UUID.randomUUID());
        }

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/screening-reports/batch")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("applicationIds", tooMany))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchScreeningRejectsAnApplicationFromAnotherInternship() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication ownApplication = createApplication(profile, createPdfWithLines("Java developer."));

        Internship otherInternship = createInternship(companyProfile);
        InternProfile otherProfile = createInternProfile(Set.of());
        InternshipApplication foreignApplication = new InternshipApplication(otherInternship.getId(), otherProfile.getId());
        foreignApplication.setCoverLetter("x");
        foreignApplication.setCvFileName("resume.pdf");
        foreignApplication.setCvStorageKey(cvStorageService.store(otherProfile.getId(), createPdfWithLines("Java")));
        foreignApplication.setCvContentType("application/pdf");
        foreignApplication.setCvSizeBytes(10);
        foreignApplication.setCvUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        applicationRepository.saveAndFlush(foreignApplication);

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/screening-reports/batch")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("applicationIds", List.of(ownApplication.getId(), foreignApplication.getId())))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchScreeningScreensEachApplicationAndPersistsReports() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        InternProfile profile1 = createInternProfile(Set.of());
        InternProfile profile2 = createInternProfile(Set.of());
        InternshipApplication app1 = createApplication(profile1, createPdfWithLines("Java developer."));
        InternshipApplication app2 = createApplication(profile2, createPdfWithLines("Python developer."));

        mockMvc.perform(post("/api/v1/company/internships/" + internship.getId() + "/screening-reports/batch")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("applicationIds", List.of(app1.getId(), app2.getId())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        assertThat(screeningReportRepository.findAllByApplicationIdInOrderByEvaluatedAtDesc(List.of(app1.getId(), app2.getId())))
                .hasSize(2);
    }

    @Test
    void listSummariesIsPaginatedAndNeverTriggersScreeningAsASideEffect() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        for (int i = 0; i < 3; i++) {
            InternProfile profile = createInternProfile(Set.of());
            createApplication(profile, createPdfWithLines("Java developer " + i));
        }

        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/screening-reports")
                        .with(asCompany())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].latestReportId").doesNotExist());

        assertThat(screeningReportRepository.findAll()).isEmpty();
    }

    @Test
    void listSummariesReflectsAnExistingReportWithoutRecomputingIt() throws Exception {
        Map<String, Object> req = skillRequirement("Java", "REQUIRED", 80);
        activateRubric(List.of(req));
        InternProfile profile = createInternProfile(Set.of());
        InternshipApplication application = createApplication(profile, createPdfWithLines("Java developer."));
        mockMvc.perform(post(screenUrl(application.getId())).with(asCompany())).andExpect(status().isCreated());

        assertThat(screeningReportRepository.findAll()).hasSize(1);

        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/screening-reports").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].weightedCoveragePercentage").value(100));

        // Still exactly one report — listing never re-screens.
        assertThat(screeningReportRepository.findAll()).hasSize(1);
    }
}
