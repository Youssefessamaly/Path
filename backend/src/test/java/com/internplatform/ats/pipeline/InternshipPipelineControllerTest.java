package com.internplatform.ats.pipeline;

import com.internplatform.ats.access.AtsMemberRole;
import com.internplatform.ats.access.CompanyMember;
import com.internplatform.ats.access.CompanyMemberRepository;
import com.internplatform.ats.access.MemberStatus;
import com.internplatform.ats.template.PipelineTemplateService;
import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internship.ApplicationMethod;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internship.InternshipStatus;
import com.internplatform.internship.WorkMode;
import com.internplatform.internprofile.SkillRepository;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InternshipPipelineControllerTest {

    private static final String TEMPLATES_BASE = "/api/v1/company/ats/pipeline-templates";

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
    private SkillRepository skillRepository;

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

    private Internship createInternship(CompanyProfile owner) {
        Internship internship = new Internship(owner.getId());
        internship.setTitle("Backend Engineering Intern");
        internship.setDescription("Work on our core platform.");
        internship.setLocation("Cairo, Egypt");
        internship.setWorkMode(WorkMode.HYBRID);
        internship.setApplicationDeadline(LocalDate.now().plusDays(30));
        internship.setStatus(InternshipStatus.OPEN);
        internship.setSkills(new HashSet<>(Set.of(skillRepository.findAllByOrderByNameAsc().get(0))));
        internship.setTargetMajors(Set.of());
        internship.setApplicationMethod(ApplicationMethod.INTERNAL);
        return internshipRepository.saveAndFlush(internship);
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

    private String pipelineUrl(UUID internshipId) {
        return "/api/v1/company/ats/internships/" + internshipId + "/pipeline";
    }

    private UUID createTemplateWithStages(User owner, String name, String... stageNames) throws Exception {
        String json = mockMvc.perform(post(TEMPLATES_BASE)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID templateId = UUID.fromString(objectMapper.readTree(json).get("id").asText());

        for (String stageName : stageNames) {
            mockMvc.perform(post(TEMPLATES_BASE + "/" + templateId + "/stages")
                            .with(asUser(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("name", stageName))))
                    .andExpect(status().isCreated());
        }

        return templateId;
    }

    // ===== Assignment: explicit template =====

    @Test
    void assigningAnExplicitTemplateDeepCopiesItsStagesInOrder() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);
        UUID templateId = createTemplateWithStages(owner, "Engineering Pipeline", "Applied", "Interview", "Offer");

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("templateId", templateId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTemplateId").value(templateId.toString()))
                .andExpect(jsonPath("$.sourceTemplateName").value("Engineering Pipeline"))
                .andExpect(jsonPath("$.stages.length()").value(3))
                .andExpect(jsonPath("$.stages[0].name").value("Applied"))
                .andExpect(jsonPath("$.stages[1].name").value("Interview"))
                .andExpect(jsonPath("$.stages[2].name").value("Offer"));
    }

    @Test
    void assigningTwiceToTheSameInternshipIsRejected() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotAssignAnArchivedTemplate() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);
        UUID templateId = createTemplateWithStages(owner, "Old Pipeline", "Applied");

        mockMvc.perform(post(TEMPLATES_BASE + "/" + templateId + "/archive").with(asUser(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("templateId", templateId.toString()))))
                .andExpect(status().isBadRequest());
    }

    // ===== Default / starter fallback =====

    @Test
    void assigningWithNoTemplateFallsBackToTheHardcodedStarterStages() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTemplateId").doesNotExist())
                .andExpect(jsonPath("$.stages.length()").value(PipelineTemplateService.starterStageNames().size()))
                .andExpect(jsonPath("$.stages[0].name").value("Applied"))
                .andExpect(jsonPath("$.stages[1].name").value("Screening"))
                .andExpect(jsonPath("$.stages[2].name").value("Interview"))
                .andExpect(jsonPath("$.stages[3].name").value("Offer"));
    }

    @Test
    void assigningWithNoTemplateUsesTheCompanysDefaultTemplateWhenOneExists() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);
        UUID defaultTemplateId = createTemplateWithStages(owner, "Custom Default", "Phone Screen", "Onsite");

        mockMvc.perform(patch(TEMPLATES_BASE + "/" + defaultTemplateId)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("isDefault", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTemplateId").value(defaultTemplateId.toString()))
                .andExpect(jsonPath("$.sourceTemplateName").value("Custom Default"))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].name").value("Phone Screen"))
                .andExpect(jsonPath("$.stages[1].name").value("Onsite"));
    }

    // ===== Template independence (the critical structural guarantee) =====

    @Test
    void editingTheSourceTemplateAfterAssignmentNeverAltersTheInternshipPipeline() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);
        UUID templateId = createTemplateWithStages(owner, "Engineering Pipeline", "Applied", "Interview");

        String pipelineJson = mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("templateId", templateId.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID pipelineStage1Id = UUID.fromString(
                objectMapper.readTree(pipelineJson).get("stages").get(0).get("id").asText());

        // Rename the template, add a new stage to it, and delete one of its original stages.
        mockMvc.perform(patch(TEMPLATES_BASE + "/" + templateId)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Renamed Template"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(TEMPLATES_BASE + "/" + templateId + "/stages")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Offer"))))
                .andExpect(status().isCreated());

        // The internship's own pipeline is completely unaffected: still 2 stages, same names,
        // same stage ids, and the snapshot of the template's ORIGINAL name is preserved.
        mockMvc.perform(get(pipelineUrl(internship.getId())).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceTemplateName").value("Engineering Pipeline"))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].id").value(pipelineStage1Id.toString()))
                .andExpect(jsonPath("$.stages[0].name").value("Applied"))
                .andExpect(jsonPath("$.stages[1].name").value("Interview"));

        // Renaming a stage ON THE PIPELINE must never reach back into the template either.
        mockMvc.perform(patch(pipelineUrl(internship.getId()) + "/stages/" + pipelineStage1Id)
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Application Received"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(TEMPLATES_BASE + "/" + templateId).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].name").value("Applied"));
    }

    // ===== Pipeline not yet assigned =====

    @Test
    void getPipelineBeforeAssignmentReturns404() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        mockMvc.perform(get(pipelineUrl(internship.getId())).with(asUser(owner)))
                .andExpect(status().isNotFound());
    }

    // ===== Stage CRUD on the pipeline =====

    @Test
    void cannotDeleteTheLastRemainingPipelineStage() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);
        UUID templateId = createTemplateWithStages(owner, "Minimal", "Applied");

        String pipelineJson = mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("templateId", templateId.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID onlyStage = UUID.fromString(objectMapper.readTree(pipelineJson).get("stages").get(0).get("id").asText());

        mockMvc.perform(delete(pipelineUrl(internship.getId()) + "/stages/" + onlyStage).with(asUser(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void pipelineStagesCanBeReorderedWithAnExactPermutation() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        String pipelineJson = mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var stagesNode = objectMapper.readTree(pipelineJson).get("stages");
        UUID stage0 = UUID.fromString(stagesNode.get(0).get("id").asText());
        UUID stage1 = UUID.fromString(stagesNode.get(1).get("id").asText());
        UUID stage2 = UUID.fromString(stagesNode.get(2).get("id").asText());
        UUID stage3 = UUID.fromString(stagesNode.get(3).get("id").asText());

        mockMvc.perform(put(pipelineUrl(internship.getId()) + "/stages/order")
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("stageIds", List.of(
                                stage3.toString(), stage2.toString(), stage1.toString(), stage0.toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stage3.toString()))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[3].id").value(stage0.toString()))
                .andExpect(jsonPath("$[3].position").value(3));
    }

    // ===== Authorization: owner-only mutation, member-level view =====

    @Test
    void recruiterCanViewPipelineButCannotAssignOrMutateIt() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        mockMvc.perform(post(pipelineUrl(internship.getId()))
                        .with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        User recruiter = createUser(Role.COMPANY);
        addMember(profile, recruiter, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(get(pipelineUrl(internship.getId())).with(asUser(recruiter)))
                .andExpect(status().isOk());

        mockMvc.perform(post(pipelineUrl(internship.getId()) + "/stages")
                        .with(asUser(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Extra Stage"))))
                .andExpect(status().isForbidden());

        User secondOwner = createUser(Role.COMPANY);
        CompanyProfile secondProfile = createCompanyProfile(secondOwner, ApprovalStatus.APPROVED);
        Internship secondInternship = createInternship(secondProfile);

        mockMvc.perform(post(pipelineUrl(secondInternship.getId()))
                        .with(asUser(recruiter))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ===== Cross-company isolation =====

    @Test
    void recruiterCannotAccessAnotherCompanysInternshipPipeline() throws Exception {
        User ownerA = createUser(Role.COMPANY);
        CompanyProfile companyA = createCompanyProfile(ownerA, ApprovalStatus.APPROVED);

        User ownerB = createUser(Role.COMPANY);
        CompanyProfile companyB = createCompanyProfile(ownerB, ApprovalStatus.APPROVED);
        Internship internshipB = createInternship(companyB);

        mockMvc.perform(post(pipelineUrl(internshipB.getId()))
                        .with(asUser(ownerB))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        User recruiterOfA = createUser(Role.COMPANY);
        addMember(companyA, recruiterOfA, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(get(pipelineUrl(internshipB.getId())).with(asUser(recruiterOfA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(pipelineUrl(internshipB.getId()))
                        .with(asUser(ownerA))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ===== Platform role separation =====

    @Test
    void internAndAdminCannotUseInternshipPipelineEndpoints() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        User internUser = createUser(Role.INTERN);
        mockMvc.perform(get(pipelineUrl(internship.getId())).with(asUser(internUser)))
                .andExpect(status().isForbidden());

        User adminUser = createUser(Role.ADMIN);
        mockMvc.perform(get(pipelineUrl(internship.getId())).with(asUser(adminUser)))
                .andExpect(status().isForbidden());
    }
}
