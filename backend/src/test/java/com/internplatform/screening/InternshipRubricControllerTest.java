package com.internplatform.screening;

import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.ApplicationMethod;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internship.InternshipStatus;
import com.internplatform.internship.WorkMode;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InternshipRubricControllerTest {

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
    private InternshipRepository internshipRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private InternshipRubricRepository rubricRepository;

    @Autowired
    private InternshipRubricRequirementRepository rubricRequirementRepository;

    private User companyUser;
    private CompanyProfile companyProfile;
    private Internship internship;
    private UUID skillId1;
    private UUID skillId2;

    @BeforeEach
    void setUp() {
        companyUser = createUser(Role.COMPANY);
        companyProfile = createCompletedCompanyProfile(companyUser);
        internship = createInternship(companyProfile);

        List<Skill> skills = skillRepository.findAllByOrderByNameAsc();
        skillId1 = skills.get(0).getId();
        skillId2 = skills.get(1).getId();
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

    private Internship createInternship(CompanyProfile owner) {
        Internship created = new Internship(owner.getId());
        created.setTitle("Backend Engineering Intern");
        created.setDescription("Work on our core platform.");
        created.setLocation("Cairo, Egypt");
        created.setWorkMode(WorkMode.HYBRID);
        created.setApplicationDeadline(LocalDate.now().plusDays(30));
        created.setStatus(InternshipStatus.OPEN);
        created.setSkills(new HashSet<>(Set.of(skillRepository.findAllByOrderByNameAsc().get(0))));
        created.setTargetMajors(Set.of());
        created.setApplicationMethod(ApplicationMethod.INTERNAL);
        return internshipRepository.saveAndFlush(created);
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

    private String rubricUrl() {
        return "/api/v1/company/internships/" + internship.getId() + "/rubric";
    }

    private Map<String, Object> skillRequirement(UUID skillId, String type, int weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", type);
        body.put("skillId", skillId.toString());
        body.put("weight", weight);
        body.put("displayOrder", 0);
        return body;
    }

    private void createDraft() throws Exception {
        mockMvc.perform(post(rubricUrl()).with(asCompany())).andExpect(status().isCreated());
    }

    private UUID addRequirementToDraft(UUID skillId, String type, int weight) throws Exception {
        String response = mockMvc.perform(post(rubricUrl() + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId, type, weight))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // --- Draft creation / current rubric ------------------------------------------------

    @Test
    void createDraftStartsAtVersionOne() throws Exception {
        mockMvc.perform(post(rubricUrl()).with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    void createDraftRejectsWhenDraftAlreadyExists() throws Exception {
        createDraft();

        mockMvc.perform(post(rubricUrl()).with(asCompany()))
                .andExpect(status().isConflict());
    }

    @Test
    void createDraftFromScreeningProfileCopiesItsRequirements() throws Exception {
        String profileResponse = mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Backend Template"))))
                .andReturn().getResponse().getContentAsString();
        UUID profileId = UUID.fromString(objectMapper.readTree(profileResponse).get("id").asText());

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 80))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(rubricUrl())
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("screeningProfileId", profileId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.screeningProfileId").value(profileId.toString()))
                .andExpect(jsonPath("$.requirements.length()").value(1))
                .andExpect(jsonPath("$.requirements[0].skillId").value(skillId1.toString()));
    }

    @Test
    void createDraftRejectsScreeningProfileOwnedByAnotherCompany() throws Exception {
        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);
        String profileResponse = mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asUser(otherCompanyUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Theirs"))))
                .andReturn().getResponse().getContentAsString();
        UUID theirProfileId = UUID.fromString(objectMapper.readTree(profileResponse).get("id").asText());

        mockMvc.perform(post(rubricUrl())
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("screeningProfileId", theirProfileId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCurrentRubricReturns404WhenNoneConfigured() throws Exception {
        mockMvc.perform(get(rubricUrl()).with(asCompany()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCurrentRubricPrefersDraftOverActive() throws Exception {
        createDraft();
        addRequirementToDraft(skillId1, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());

        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get(rubricUrl()).with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // --- Ownership -----------------------------------------------------------------------

    @Test
    void anotherCompanyCannotSeeOrEditThisInternshipsRubric() throws Exception {
        createDraft();
        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);

        mockMvc.perform(get(rubricUrl()).with(asUser(otherCompanyUser)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(rubricUrl() + "/requirements")
                        .with(asUser(otherCompanyUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 50))))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonCompanyRoleCannotAccessRubricEndpoints() throws Exception {
        User internUser = createUser(Role.INTERN);

        mockMvc.perform(post(rubricUrl()).with(asUser(internUser)))
                .andExpect(status().isForbidden());
    }

    // --- Requirement CRUD / validation on a draft ----------------------------------------

    @Test
    void addRequirementRejectsWhenNoDraftExists() throws Exception {
        mockMvc.perform(post(rubricUrl() + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 50))))
                .andExpect(status().isConflict());
    }

    @Test
    void addRequirementRejectsDuplicateSkillInSameRubric() throws Exception {
        createDraft();
        addRequirementToDraft(skillId1, "REQUIRED", 50);

        mockMvc.perform(post(rubricUrl() + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "PREFERRED", 20))))
                .andExpect(status().isConflict());
    }

    @Test
    void addRequirementRejectsUnknownSkillId() throws Exception {
        createDraft();

        mockMvc.perform(post(rubricUrl() + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(UUID.randomUUID(), "REQUIRED", 50))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAndDeleteRequirementWorkOnADraft() throws Exception {
        createDraft();
        UUID requirementId = addRequirementToDraft(skillId1, "REQUIRED", 50);

        mockMvc.perform(patch(rubricUrl() + "/requirements/" + requirementId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("weight", 95))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(95));

        mockMvc.perform(delete(rubricUrl() + "/requirements/" + requirementId).with(asCompany()))
                .andExpect(status().isNoContent());
    }

    // --- Versioning / immutability --------------------------------------------------------

    @Test
    void activateRejectsEmptyDraft() throws Exception {
        createDraft();

        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateRejectsWhenNoDraftExists() throws Exception {
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany()))
                .andExpect(status().isNotFound());
    }

    @Test
    void activatingADraftArchivesThePreviousActiveVersionAndIncrementsVersion() throws Exception {
        createDraft();
        addRequirementToDraft(skillId1, "REQUIRED", 80);
        String firstActivation = mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID firstRubricId = UUID.fromString(objectMapper.readTree(firstActivation).get("id").asText());

        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requirements.length()").value(1));

        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Exactly one ACTIVE rubric must ever exist for this internship.
        InternshipRubric archived = rubricRepository.findById(firstRubricId).orElseThrow();
        assertThat(archived.getStatus()).isEqualTo(RubricStatus.ARCHIVED);
        List<InternshipRubric> versions = rubricRepository.findAllByInternshipIdOrderByVersionDesc(internship.getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.stream().filter(r -> r.getStatus() == RubricStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void onlyADraftRubricsRequirementsCanBeEdited() throws Exception {
        createDraft();
        UUID requirementId = addRequirementToDraft(skillId1, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());

        // Now ACTIVE — its requirements must be frozen.
        mockMvc.perform(patch(rubricUrl() + "/requirements/" + requirementId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("weight", 10))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(rubricUrl() + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId2, "PREFERRED", 20))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(rubricUrl() + "/requirements/" + requirementId).with(asCompany()))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateRejectsWhenDraftAlreadyExists() throws Exception {
        createDraft();
        addRequirementToDraft(skillId1, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());
        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany())).andExpect(status().isCreated());

        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany()))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicatingAnArchivedRubricCarriesForwardTheScreeningProfileReference() throws Exception {
        String profileResponse = mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Backend Template"))))
                .andReturn().getResponse().getContentAsString();
        UUID profileId = UUID.fromString(objectMapper.readTree(profileResponse).get("id").asText());

        mockMvc.perform(post(rubricUrl())
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("screeningProfileId", profileId.toString()))))
                .andExpect(status().isCreated());
        addRequirementToDraft(skillId1, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());

        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.screeningProfileId").value(profileId.toString()));
    }

    @Test
    void discardDraftRemovesItWithoutAffectingTheActiveVersion() throws Exception {
        createDraft();
        addRequirementToDraft(skillId1, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());
        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany())).andExpect(status().isCreated());

        mockMvc.perform(delete(rubricUrl()).with(asCompany()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(rubricUrl()).with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void discardDraftRejectsWhenNoDraftExists() throws Exception {
        mockMvc.perform(delete(rubricUrl()).with(asCompany()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listVersionsReturnsAllVersionsNewestFirst() throws Exception {
        createDraft();
        addRequirementToDraft(skillId1, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());
        mockMvc.perform(post(rubricUrl() + "/duplicate").with(asCompany())).andExpect(status().isCreated());

        mockMvc.perform(get(rubricUrl() + "/versions").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].version").value(1))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    void existingInternshipRequiredSkillsAreUntouchedByRubricConfiguration() throws Exception {
        UUID originalRequiredSkillId = internship.getSkills().iterator().next().getId();
        createDraft();
        addRequirementToDraft(skillId2, "REQUIRED", 80);
        mockMvc.perform(post(rubricUrl() + "/activate").with(asCompany())).andExpect(status().isOk());

        Internship reloaded = internshipRepository.findById(internship.getId()).orElseThrow();
        assertThat(reloaded.getSkills()).extracting(Skill::getId).containsExactly(originalRequiredSkillId);
    }
}
