package com.internplatform.ats.candidate;

import com.internplatform.ats.access.AtsMemberRole;
import com.internplatform.ats.access.CompanyMember;
import com.internplatform.ats.access.CompanyMemberRepository;
import com.internplatform.ats.access.MemberStatus;
import com.internplatform.ats.pipeline.AtsInternshipPipeline;
import com.internplatform.ats.pipeline.AtsInternshipPipelineRepository;
import com.internplatform.ats.pipeline.AtsStage;
import com.internplatform.ats.pipeline.AtsStageRepository;
import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.AcademicLevel;
import com.internplatform.internprofile.CvStorageService;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.internship.ApplicationMethod;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internship.InternshipStatus;
import com.internplatform.internship.WorkMode;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internshipapplication.ApplicationStatus;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashMap;
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
class CandidateControllerTest {

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
    private CompanyMemberRepository companyMemberRepository;

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private InternshipApplicationRepository applicationRepository;

    @Autowired
    private InternProfileRepository internProfileRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private CvStorageService cvStorageService;

    @Autowired
    private AtsInternshipPipelineRepository pipelineRepository;

    @Autowired
    private AtsStageRepository stageRepository;

    @Autowired
    private AtsApplicationStateRepository applicationStateRepository;

    private User createUser(Role role) {
        return userRepository.saveAndFlush(new User(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password123!"),
                role));
    }

    private CompanyProfile createCompanyProfile(User owner) {
        CompanyProfile profile = new CompanyProfile(owner.getId());
        profile.setCompanyName("Pathora Technologies");
        profile.markOnboardingCompleted(OffsetDateTime.now(ZoneOffset.UTC));
        profile.recordApprovalDecision(ApprovalStatus.APPROVED, OffsetDateTime.now(ZoneOffset.UTC));
        return companyProfileRepository.saveAndFlush(profile);
    }

    private void addMember(CompanyProfile profile, User user, AtsMemberRole role, MemberStatus status) {
        companyMemberRepository.saveAndFlush(new CompanyMember(profile.getId(), user.getId(), role, status));
    }

    private Internship createInternship(CompanyProfile owner) {
        Internship internship = new Internship(owner.getId());
        internship.setTitle("Backend Engineering Intern");
        internship.setDescription("Work on our core platform.");
        internship.setLocation("Cairo, Egypt");
        internship.setWorkMode(WorkMode.HYBRID);
        internship.setApplicationDeadline(LocalDate.now(ZoneOffset.UTC).plusDays(30));
        internship.setStatus(InternshipStatus.OPEN);
        internship.setSkills(Set.of(skillRepository.findAllByOrderByNameAsc().get(0)));
        internship.setTargetMajors(Set.of());
        internship.setApplicationMethod(ApplicationMethod.INTERNAL);
        return internshipRepository.saveAndFlush(internship);
    }

    private AtsInternshipPipeline createPipelineWithStages(Internship internship, String... stageNames) {
        AtsInternshipPipeline pipeline = pipelineRepository.saveAndFlush(
                new AtsInternshipPipeline(internship.getId(), internship.getCompanyProfileId()));
        for (int i = 0; i < stageNames.length; i++) {
            stageRepository.saveAndFlush(new AtsStage(pipeline.getId(), stageNames[i], i));
        }
        return pipeline;
    }

    private InternProfile createInternProfile() {
        User internUser = createUser(Role.INTERN);
        InternProfile profile = new InternProfile(internUser.getId());
        profile.setFirstName("Amina");
        profile.setLastName("Hassan");
        return internProfileRepository.saveAndFlush(profile);
    }

    private InternshipApplication createApplication(Internship internship, ApplicationStatus status) {
        InternProfile internProfile = createInternProfile();
        InternshipApplication application = new InternshipApplication(internship.getId(), internProfile.getId());
        application.setCvFileName("resume.pdf");
        application.setCvStorageKey(UUID.randomUUID() + "/resume.pdf");
        application.setCvContentType("application/pdf");
        application.setCvSizeBytes(1024);
        application.setCvUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        InternshipApplication saved = applicationRepository.saveAndFlush(application);
        if (status != ApplicationStatus.APPLIED) {
            saved.setStatus(status);
            saved = applicationRepository.saveAndFlush(saved);
        }
        return saved;
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

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ===== Listing: tracked + lazily-backfilled + deliberately-untracked =====

    @Test
    void listCandidatesShowsCurrentStageForApplicationsAndBackfillsUndecidedOnes() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Interview");

        // Simulates a pre-existing, still-undecided application that predates this phase —
        // never explicitly tracked. The listing call itself must lazily backfill it.
        InternshipApplication appliedApplication = createApplication(internship, ApplicationStatus.APPLIED);
        // Simulates a pre-existing, ALREADY-decided application — must remain untracked.
        InternshipApplication rejectedApplication = createApplication(internship, ApplicationStatus.REJECTED);

        String json = mockMvc.perform(get("/api/v1/company/ats/internships/" + internship.getId() + "/candidates")
                        .with(asUser(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var root = objectMapper.readTree(json);
        assertThat(root).hasSize(2);

        for (var node : root) {
            String applicationId = node.get("applicationId").asText();
            if (applicationId.equals(appliedApplication.getId().toString())) {
                assertThat(node.get("currentStageName").asText()).isEqualTo("Applied");
                assertThat(node.get("currentStageId").isNull()).isFalse();
            } else if (applicationId.equals(rejectedApplication.getId().toString())) {
                assertThat(node.get("currentStageId").isNull()).isTrue();
                assertThat(node.get("currentStageName").isNull()).isTrue();
            } else {
                throw new AssertionError("Unexpected applicationId in response: " + applicationId);
            }
        }

        // The backfill actually persisted a real tracking row for the undecided application.
        assertThat(applicationStateRepository.findById(appliedApplication.getId())).isPresent();
        assertThat(applicationStateRepository.findById(rejectedApplication.getId())).isEmpty();
    }

    // ===== Single stage + history =====

    @Test
    void getCandidateStageAndHistoryReflectTheEnteredEvent() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Interview", "Offer");
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/company/ats/applications/" + application.getId()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(application.getId().toString()))
                .andExpect(jsonPath("$.currentStageName").value("Applied"));

        mockMvc.perform(get("/api/v1/company/ats/applications/" + application.getId() + "/history").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromStageId").doesNotExist())
                .andExpect(jsonPath("$[0].toStageName").value("Applied"))
                .andExpect(jsonPath("$[0].eventKind").value("ENTERED"))
                .andExpect(jsonPath("$[0].actorMemberId").doesNotExist());
    }

    // ===== Authorization: member-level (not owner-only), cross-company isolation, role gate =====

    @Test
    void activeRecruiterCanViewCandidatesAndHistory() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied");
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        User recruiter = createUser(Role.COMPANY);
        addMember(company, recruiter, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/company/ats/internships/" + internship.getId() + "/candidates")
                        .with(asUser(recruiter)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company/ats/applications/" + application.getId()).with(asUser(recruiter)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company/ats/applications/" + application.getId() + "/history")
                        .with(asUser(recruiter)))
                .andExpect(status().isOk());
    }

    @Test
    void crossCompanyAccessToCandidatesIsRejected() throws Exception {
        User ownerA = createUser(Role.COMPANY);
        CompanyProfile companyA = createCompanyProfile(ownerA);

        User ownerB = createUser(Role.COMPANY);
        CompanyProfile companyB = createCompanyProfile(ownerB);
        Internship internshipB = createInternship(companyB);
        createPipelineWithStages(internshipB, "Applied");
        InternshipApplication applicationB = createApplication(internshipB, ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/company/ats/internships/" + internshipB.getId() + "/candidates")
                        .with(asUser(ownerA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/company/ats/applications/" + applicationB.getId()).with(asUser(ownerA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/company/ats/applications/" + applicationB.getId() + "/history")
                        .with(asUser(ownerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void internAndPlatformAdminCannotAccessCandidateEndpoints() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);

        User internUser = createUser(Role.INTERN);
        mockMvc.perform(get("/api/v1/company/ats/internships/" + internship.getId() + "/candidates")
                        .with(asUser(internUser)))
                .andExpect(status().isForbidden());

        User adminUser = createUser(Role.ADMIN);
        mockMvc.perform(get("/api/v1/company/ats/internships/" + internship.getId() + "/candidates")
                        .with(asUser(adminUser)))
                .andExpect(status().isForbidden());
    }

    // ===== Real end-to-end: a genuine submission through the public API auto-enters stage 1 =====
    //
    // This is the one test in the ATS suite that must force a REAL commit: the automatic-intake
    // listener is a @TransactionalEventListener(phase = AFTER_COMMIT), which — correctly — never
    // fires inside this class's own @Transactional rollback-per-test wrapping (there is no real
    // commit for it to react to). TestTransaction.end() forces the preceding work to actually
    // commit so the listener genuinely runs, exactly as it would in production. The rows this
    // test commits are intentionally left in the isolated ATS test database afterward, same as
    // every other ensureTracked-exercising test in this suite (see AtsApplicationIntakeServiceTest).

    private InternProfile createCompletedInternProfileForSubmission(User user) {
        InternProfile profile = new InternProfile(user.getId());
        profile.setFirstName("Amina");
        profile.setLastName("Hassan");
        profile.setDateOfBirth(LocalDate.of(2001, 5, 10));
        profile.setPhoneNumber("+201012345678");
        profile.setInstitutionName("Cairo University");
        profile.setAcademicLevel(AcademicLevel.UNIVERSITY);
        profile.setAcademicYear((short) 3);
        profile.setMajor("Computer Science");
        profile.setExpectedGraduationYear((short) (Year.now().getValue() + 1));
        profile.setSkills(Set.of(skillRepository.findAllByOrderByNameAsc().get(0)));
        profile.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return internProfileRepository.saveAndFlush(profile);
    }

    private void attachCv(InternProfile profile) {
        String storageKey = cvStorageService.store(profile.getId(), "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII));
        profile.setCvOriginalFilename("resume.pdf");
        profile.setCvStorageKey(storageKey);
        profile.setCvContentType("application/pdf");
        profile.setCvSizeBytes(13);
        profile.setCvUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        internProfileRepository.saveAndFlush(profile);
    }

    @Test
    void submittingARealApplicationAutomaticallyEntersTheFirstStageAndIntactResponseShape() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Screening", "Interview", "Offer");

        User internUser = createUser(Role.INTERN);
        InternProfile internProfile = createCompletedInternProfileForSubmission(internUser);
        attachCv(internProfile);

        Map<String, Object> body = new HashMap<>();

        String responseJson = mockMvc.perform(post("/api/v1/intern/internships/" + internship.getId() + "/applications")
                        .with(asUser(internUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isCreated())
                // Intern-facing response shape is completely unaffected by the ATS wiring —
                // same fields as before, nothing ATS-related leaked into it.
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.applicationId").exists())
                .andExpect(jsonPath("$.currentStageId").doesNotExist())
                .andExpect(jsonPath("$.currentStageName").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID applicationId = UUID.fromString(objectMapper.readTree(responseJson).get("applicationId").asText());

        // Force everything so far to actually commit, so the AFTER_COMMIT listener fires for
        // real, exactly as it does in production.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        AtsApplicationState state = applicationStateRepository.findById(applicationId).orElse(null);
        assertThat(state).isNotNull();

        AtsStage firstStage = stageRepository.findAllByPipelineIdOrderByPositionAsc(state.getPipelineId()).get(0);
        assertThat(state.getCurrentStageId()).isEqualTo(firstStage.getId());
        assertThat(firstStage.getName()).isEqualTo("Applied");

        // The pre-existing application record itself is completely unchanged by any of this.
        InternshipApplication application = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);

        // This test forced a real commit above (required to observe the AFTER_COMMIT
        // listener) — unlike every other test in this class, nothing here is rolled back
        // automatically. Explicitly delete both top-level users; every row this test created
        // (company profile, internship, pipeline, stages, intern profile, application, ATS
        // state, ATS event) cascades away from those two deletes (see the FK ON DELETE CASCADE
        // chains in V17-V20), leaving the isolated ATS test database exactly as it was before
        // this test ran.
        TestTransaction.start();
        userRepository.deleteById(owner.getId());
        userRepository.deleteById(internUser.getId());
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }
}
