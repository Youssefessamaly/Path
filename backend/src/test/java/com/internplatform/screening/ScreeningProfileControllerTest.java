package com.internplatform.screening;

import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class ScreeningProfileControllerTest {

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
    private SkillRepository skillRepository;

    private User companyUser;
    private CompanyProfile companyProfile;
    private UUID skillId1;
    private UUID skillId2;

    @BeforeEach
    void setUp() {
        companyUser = createUser(Role.COMPANY);
        companyProfile = createCompletedCompanyProfile(companyUser);

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

    private UUID createProfile(String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", "A reusable template");

        String response = mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private Map<String, Object> skillRequirement(UUID skillId, String type, int weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", type);
        body.put("skillId", skillId.toString());
        body.put("weight", weight);
        body.put("displayOrder", 0);
        return body;
    }

    private Map<String, Object> labelRequirement(String label, String type, int weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", type);
        body.put("label", label);
        body.put("weight", weight);
        body.put("displayOrder", 0);
        return body;
    }

    // --- Profile CRUD ------------------------------------------------------------------

    @Test
    void createProfileSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Backend Screening", "description", "For backend roles"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Backend Screening"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.companyProfileId").value(companyProfile.getId().toString()))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    void createProfileRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonCompanyRoleCannotAccessScreeningProfiles() throws Exception {
        User internUser = createUser(Role.INTERN);

        mockMvc.perform(get("/api/v1/company/screening-profiles").with(asUser(internUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProfilesReturnsOnlyTheCallersOwnCompanyProfiles() throws Exception {
        createProfile("Mine");

        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);
        mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asUser(otherCompanyUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Theirs"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/company/screening-profiles").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mine"));
    }

    @Test
    void getProfileReturns404ForAnotherCompanysProfile() throws Exception {
        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);
        String response = mockMvc.perform(post("/api/v1/company/screening-profiles")
                        .with(asUser(otherCompanyUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Theirs"))))
                .andReturn().getResponse().getContentAsString();
        UUID theirProfileId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/v1/company/screening-profiles/" + theirProfileId).with(asCompany()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfileChangesNameAndDescription() throws Exception {
        UUID profileId = createProfile("Original name");

        mockMvc.perform(patch("/api/v1/company/screening-profiles/" + profileId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Updated name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.description").value("A reusable template"));
    }

    @Test
    void updateProfileRejectsBlankName() throws Exception {
        UUID profileId = createProfile("Original name");

        mockMvc.perform(patch("/api/v1/company/screening-profiles/" + profileId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archiveThenActivateProfileChangesStatus() throws Exception {
        UUID profileId = createProfile("Toggle me");

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/archive").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/activate").with(asCompany()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void duplicateProfileCopiesRequirementsIndependently() throws Exception {
        UUID profileId = createProfile("Original");
        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 90))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/duplicate")
                        .with(asCompany()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Original (Copy)"))
                .andExpect(jsonPath("$.requirements.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID copyId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        UUID copyRequirementId = UUID.fromString(
                objectMapper.readTree(response).get("requirements").get(0).get("id").asText());

        // Editing the copy's requirement must never affect the original's.
        mockMvc.perform(patch("/api/v1/company/screening-profiles/" + copyId + "/requirements/" + copyRequirementId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("weight", 10))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company/screening-profiles/" + profileId).with(asCompany()))
                .andExpect(jsonPath("$.requirements[0].weight").value(90));
    }

    // --- Requirement CRUD / validation --------------------------------------------------

    @Test
    void addRequirementWithSkillSucceeds() throws Exception {
        UUID profileId = createProfile("Backend Screening");

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 80))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requirementType").value("REQUIRED"))
                .andExpect(jsonPath("$.skillId").value(skillId1.toString()))
                .andExpect(jsonPath("$.skillName").isNotEmpty())
                .andExpect(jsonPath("$.weight").value(80));
    }

    @Test
    void addRequirementWithLabelOnlySucceeds() throws Exception {
        UUID profileId = createProfile("Backend Screening");

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(labelRequirement("Valid driving license", "BONUS", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Valid driving license"))
                .andExpect(jsonPath("$.skillId").doesNotExist());
    }

    @Test
    void addRequirementRejectsWhenNeitherSkillNorLabelProvided() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        Map<String, Object> body = new HashMap<>();
        body.put("requirementType", "REQUIRED");
        body.put("weight", 50);
        body.put("displayOrder", 0);

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addRequirementRejectsUnknownSkillId() throws Exception {
        UUID profileId = createProfile("Backend Screening");

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(UUID.randomUUID(), "REQUIRED", 50))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addRequirementRejectsDuplicateSkillInSameProfile() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 50))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "PREFERRED", 20))))
                .andExpect(status().isConflict());
    }

    @Test
    void addRequirementRejectsWeightAboveOneHundred() throws Exception {
        UUID profileId = createProfile("Backend Screening");

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 101))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRequirementChangesWeightAndNotes() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        String created = mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 50))))
                .andReturn().getResponse().getContentAsString();
        UUID requirementId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(patch("/api/v1/company/screening-profiles/" + profileId + "/requirements/" + requirementId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("weight", 95, "recruiterNotes", "Must be hands-on"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(95))
                .andExpect(jsonPath("$.recruiterNotes").value("Must be hands-on"));
    }

    @Test
    void updateRequirementRejectsBlankLabelForLabelOnlyRequirement() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        String created = mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(labelRequirement("Driving license", "BONUS", 10))))
                .andReturn().getResponse().getContentAsString();
        UUID requirementId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(patch("/api/v1/company/screening-profiles/" + profileId + "/requirements/" + requirementId)
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("label", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRequirementRemovesIt() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        String created = mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 50))))
                .andReturn().getResponse().getContentAsString();
        UUID requirementId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(delete("/api/v1/company/screening-profiles/" + profileId + "/requirements/" + requirementId)
                        .with(asCompany()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/company/screening-profiles/" + profileId).with(asCompany()))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    void requirementOperationsReturn404ForAnotherCompanysProfile() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        String created = mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(skillRequirement(skillId1, "REQUIRED", 50))))
                .andReturn().getResponse().getContentAsString();
        UUID requirementId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        User otherCompanyUser = createUser(Role.COMPANY);
        createCompletedCompanyProfile(otherCompanyUser);

        mockMvc.perform(patch("/api/v1/company/screening-profiles/" + profileId + "/requirements/" + requirementId)
                        .with(asUser(otherCompanyUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("weight", 99))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/company/screening-profiles/" + profileId + "/requirements/" + requirementId)
                        .with(asUser(otherCompanyUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptedEvidenceSourcesRoundTripAndDefaultToUnrestricted() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        Map<String, Object> body = skillRequirement(skillId1, "REQUIRED", 50);

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedEvidenceSources").isEmpty());

        body.put("acceptedEvidenceSources", List.of("PROJECT", "WORK_EXPERIENCE"));
        body.put("skillId", skillId2.toString());

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedEvidenceSources.length()").value(2));
    }

    @Test
    void minimumEvidenceStrengthMustBeAValidEnumValue() throws Exception {
        UUID profileId = createProfile("Backend Screening");
        Map<String, Object> body = skillRequirement(skillId1, "REQUIRED", 50);
        body.put("minimumEvidenceStrength", "VERY_STRONG");

        mockMvc.perform(post("/api/v1/company/screening-profiles/" + profileId + "/requirements")
                        .with(asCompany())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest());
    }
}
