package com.internplatform.ats.template;

import com.internplatform.ats.access.AtsMemberRole;
import com.internplatform.ats.access.CompanyMember;
import com.internplatform.ats.access.CompanyMemberRepository;
import com.internplatform.ats.access.MemberStatus;
import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PipelineTemplateControllerTest {

    private static final String BASE = "/api/v1/company/ats/pipeline-templates";

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

    private User createUser(Role role) {
        return userRepository.saveAndFlush(new User(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password123!"),
                role));
    }

    private CompanyProfile createCompanyProfile(User owner, ApprovalStatus status) {
        CompanyProfile profile = new CompanyProfile(owner.getId());
        profile.setCompanyName("Pathora Technologies");
        profile.setIndustry("Software");
        profile.setCompanySize("11-50");
        profile.setLocation("Cairo, Egypt");
        profile.setDescription("We build tools that connect interns with great companies.");
        profile.setContactName("Sara Ahmed");
        profile.setContactPhone("+201012345678");
        profile.markOnboardingCompleted(OffsetDateTime.now(ZoneOffset.UTC));
        if (status != ApprovalStatus.PENDING) {
            profile.recordApprovalDecision(status, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return companyProfileRepository.saveAndFlush(profile);
    }

    private void addMember(CompanyProfile profile, User user, AtsMemberRole role, MemberStatus status) {
        companyMemberRepository.saveAndFlush(new CompanyMember(profile.getId(), user.getId(), role, status));
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

    private UUID createTemplate(User owner, String name) throws Exception {
        String json = mockMvc.perform(post(BASE)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID addStage(User owner, UUID templateId, String name) throws Exception {
        String json = mockMvc.perform(post(BASE + "/" + templateId + "/stages")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    // ===== Create / list / get =====

    @Test
    void ownerCanCreateTemplateWithNoStages() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);

        mockMvc.perform(post(BASE)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Engineering Pipeline", "description", "For dev roles"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Engineering Pipeline"))
                .andExpect(jsonPath("$.description").value("For dev roles"))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.stages.length()").value(0));
    }

    @Test
    void recruiterCannotCreateOrMutateTemplatesButCanViewThem() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Engineering Pipeline");

        User recruiter = createUser(Role.COMPANY);
        addMember(profile, recruiter, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(post(BASE)
                        .with(asUser(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Hijacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(BASE + "/" + templateId)
                        .with(asUser(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Hijacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE).with(asUser(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(BASE + "/" + templateId).with(asUser(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Engineering Pipeline"));
    }

    @Test
    void listExcludesArchivedByDefaultButIncludesWhenRequested() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Old Pipeline");

        mockMvc.perform(post(BASE + "/" + templateId + "/archive").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get(BASE).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(BASE).param("includeArchived", "true").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ===== Update / default / archive =====

    @Test
    void settingDefaultClearsAnyExistingDefaultForTheSameCompany() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateA = createTemplate(owner, "Template A");
        UUID templateB = createTemplate(owner, "Template B");

        mockMvc.perform(patch(BASE + "/" + templateA)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("isDefault", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(patch(BASE + "/" + templateB)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("isDefault", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(get(BASE + "/" + templateA).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void archivingTheDefaultTemplateClearsItsDefaultFlag() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Template A");

        mockMvc.perform(patch(BASE + "/" + templateId)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("isDefault", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/" + templateId + "/archive").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archivedTemplateCannotBeEditedOrHaveStagesChanged() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Template A");
        UUID stageId = addStage(owner, templateId, "Applied");

        mockMvc.perform(post(BASE + "/" + templateId + "/archive").with(asUser(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(patch(BASE + "/" + templateId)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "New name"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/" + templateId + "/stages")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Screening"))))
                .andExpect(status().isConflict());

        mockMvc.perform(patch(BASE + "/" + templateId + "/stages/" + stageId)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Renamed"))))
                .andExpect(status().isConflict());
    }

    // ===== Duplicate =====

    @Test
    void duplicateDeepCopiesStagesIntoAFullyIndependentNewTemplate() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID originalId = createTemplate(owner, "Original");
        addStage(owner, originalId, "Applied");
        addStage(owner, originalId, "Interview");

        mockMvc.perform(patch(BASE + "/" + originalId)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("isDefault", true))))
                .andExpect(status().isOk());

        String duplicateJson = mockMvc.perform(post(BASE + "/" + originalId + "/duplicate").with(asUser(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Original (Copy)"))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        UUID duplicateId = UUID.fromString(objectMapper.readTree(duplicateJson).get("id").asText());
        assertThat(duplicateId).isNotEqualTo(originalId);

        // Mutating the duplicate's stages must never touch the original's.
        mockMvc.perform(post(BASE + "/" + duplicateId + "/stages")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Offer"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE + "/" + originalId).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages.length()").value(2));

        mockMvc.perform(get(BASE + "/" + duplicateId).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages.length()").value(3));
    }

    // ===== Stage CRUD =====

    @Test
    void stagesAreAppendedInOrderAndCanBeRenamed() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Template A");

        UUID stage1 = addStage(owner, templateId, "Applied");
        addStage(owner, templateId, "Interview");

        mockMvc.perform(get(BASE + "/" + templateId).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].name").value("Applied"))
                .andExpect(jsonPath("$.stages[0].position").value(0))
                .andExpect(jsonPath("$.stages[1].name").value("Interview"))
                .andExpect(jsonPath("$.stages[1].position").value(1));

        mockMvc.perform(patch(BASE + "/" + templateId + "/stages/" + stage1)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Application Received"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Application Received"));
    }

    @Test
    void cannotDeleteTheLastRemainingStage() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Template A");
        UUID onlyStage = addStage(owner, templateId, "Applied");

        mockMvc.perform(delete(BASE + "/" + templateId + "/stages/" + onlyStage).with(asUser(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingAStageRepositionsRemainingStagesContiguously() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Template A");
        UUID stage1 = addStage(owner, templateId, "Applied");
        UUID stage2 = addStage(owner, templateId, "Screening");
        addStage(owner, templateId, "Interview");

        mockMvc.perform(delete(BASE + "/" + templateId + "/stages/" + stage2).with(asUser(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + templateId).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].id").value(stage1.toString()))
                .andExpect(jsonPath("$.stages[0].position").value(0))
                .andExpect(jsonPath("$.stages[1].position").value(1));
    }

    @Test
    void reorderRequiresAnExactPermutationOfCurrentStageIds() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.APPROVED);
        UUID templateId = createTemplate(owner, "Template A");
        UUID stage1 = addStage(owner, templateId, "Applied");
        UUID stage2 = addStage(owner, templateId, "Screening");

        // Valid reorder: reverse order.
        mockMvc.perform(put(BASE + "/" + templateId + "/stages/order")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("stageIds", List.of(stage2.toString(), stage1.toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stage2.toString()))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].id").value(stage1.toString()))
                .andExpect(jsonPath("$[1].position").value(1));

        // Invalid: missing a stage id.
        mockMvc.perform(put(BASE + "/" + templateId + "/stages/order")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("stageIds", List.of(stage2.toString())))))
                .andExpect(status().isBadRequest());

        // Invalid: a duplicate id.
        mockMvc.perform(put(BASE + "/" + templateId + "/stages/order")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("stageIds", List.of(stage1.toString(), stage1.toString())))))
                .andExpect(status().isBadRequest());

        // Invalid: an id from outside this template.
        mockMvc.perform(put(BASE + "/" + templateId + "/stages/order")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("stageIds", List.of(UUID.randomUUID().toString(), stage1.toString())))))
                .andExpect(status().isBadRequest());
    }

    // ===== Cross-company isolation =====

    @Test
    void anotherCompanysTemplateIsNotFoundAndCannotBeMutated() throws Exception {
        User ownerA = createUser(Role.COMPANY);
        createCompanyProfile(ownerA, ApprovalStatus.APPROVED);
        UUID templateIdA = createTemplate(ownerA, "Company A Template");

        User ownerB = createUser(Role.COMPANY);
        createCompanyProfile(ownerB, ApprovalStatus.APPROVED);

        mockMvc.perform(get(BASE + "/" + templateIdA).with(asUser(ownerB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(BASE + "/" + templateIdA)
                        .with(asUser(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Hijacked"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(BASE + "/" + templateIdA + "/archive").with(asUser(ownerB)))
                .andExpect(status().isNotFound());

        // Company B's own list never includes company A's template.
        mockMvc.perform(get(BASE).with(asUser(ownerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ===== Platform role separation =====

    @Test
    void internCannotUsePipelineTemplateEndpoints() throws Exception {
        User internUser = createUser(Role.INTERN);

        mockMvc.perform(get(BASE).with(asUser(internUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminCannotUsePipelineTemplateEndpoints() throws Exception {
        User adminUser = createUser(Role.ADMIN);

        mockMvc.perform(get(BASE).with(asUser(adminUser)))
                .andExpect(status().isForbidden());
    }

    // ===== Unapproved company restriction =====

    @Test
    void unapprovedCompanyCannotUsePipelineTemplateEndpoints() throws Exception {
        User owner = createUser(Role.COMPANY);
        createCompanyProfile(owner, ApprovalStatus.PENDING);

        mockMvc.perform(post(BASE)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Template A"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE).with(asUser(owner)))
                .andExpect(status().isForbidden());
    }
}
