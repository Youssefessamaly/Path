package com.internplatform.ats.access;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1 — company membership foundation. Covers: existing-owner backfill/compatibility, an
 * active recruiter resolving to the correct company, revoked/non-member denial, unapproved-
 * company restrictions, cross-company isolation, OWNER-only operations, platform role
 * separation (INTERN/ADMIN), and continued operation of the pre-existing company application
 * endpoints — see the Phase 1 spec for the full enumerated list this file satisfies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompanyMemberControllerTest {

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
    private CompanyAccessResolver companyAccessResolver;

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private SkillRepository skillRepository;

    private User createUser(Role role) {
        return userRepository.saveAndFlush(
                new User(
                        role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                        passwordEncoder.encode("Password123!"),
                        role
                )
        );
    }

    /** Created directly via the repository — exactly how every pre-existing test fixture in
     *  this codebase builds a company, and exactly how every company that existed before this
     *  resolver was introduced came to exist. Deliberately never inserts an ats_company_members
     *  row here, so owner resolution is proven independent of that table. */
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

    private CompanyMember addMember(CompanyProfile profile, User user, AtsMemberRole role, MemberStatus status) {
        return companyMemberRepository.saveAndFlush(new CompanyMember(profile.getId(), user.getId(), role, status));
    }

    /** Created directly via the repository (bypassing the gated service), same pattern as
     *  CompanyApprovalEnforcementTest, so a test can set up an internship for a company
     *  regardless of that company's approval status. */
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

    private Map<String, Object> validInternshipCreateBody() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", "Backend Intern");
        body.put("description", "Work on our platform.");
        body.put("location", "Cairo, Egypt");
        body.put("workMode", "HYBRID");
        body.put("applicationDeadline", LocalDate.now().plusDays(30).toString());
        body.put("skillIds", List.of(skillRepository.findAllByOrderByNameAsc().get(0).getId().toString()));
        body.put("applicationMethod", "INTERNAL");
        return body;
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

    // ===== 1. Existing company owner membership backfill (resilience property) =====

    @Test
    void ownerProfileCreatedDirectlyWithoutMembershipRowStillResolvesAsActiveOwner() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);

        assertThat(companyMemberRepository.findByCompanyProfileIdAndUserId(profile.getId(), owner.getId()))
                .isEmpty();

        CompanyContext context = companyAccessResolver.resolveActiveMember(owner.getId());

        assertThat(context.companyProfileId()).isEqualTo(profile.getId());
        assertThat(context.role()).isEqualTo(AtsMemberRole.OWNER);
        assertThat(context.isOwner()).isTrue();
    }

    @Test
    void newCompanyRegistrationGetsAnOwnerMembershipRowImmediately() throws Exception {
        Map<String, Object> body = Map.of(
                "email", "newcompany-" + UUID.randomUUID() + "@example.test",
                "password", "Password123!"
        );

        String json = mockMvc.perform(post("/api/v1/auth/register/company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID userId = UUID.fromString(objectMapper.readTree(json).get("id").asText());
        CompanyProfile profile = companyProfileRepository.findByUserId(userId).orElseThrow();

        CompanyMember member = companyMemberRepository
                .findByCompanyProfileIdAndUserId(profile.getId(), userId)
                .orElseThrow();

        assertThat(member.getRole()).isEqualTo(AtsMemberRole.OWNER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // ===== 2. Existing owner access remains unchanged =====

    @Test
    void existingOwnerCanStillUseCompanyEndpointsUnchanged() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);

        mockMvc.perform(get("/api/v1/company/profile").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Pathora Technologies"));

        mockMvc.perform(get("/api/v1/company/internships").with(asUser(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.companyProfileId").value(profile.getId().toString()));
    }

    // ===== 3. Active recruiter resolves to the correct company =====

    @Test
    void activeRecruiterResolvesToOwnersCompanyAndCanListInternships() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        createInternship(profile);

        User recruiterUser = createUser(Role.COMPANY);
        addMember(profile, recruiterUser, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(recruiterUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"))
                .andExpect(jsonPath("$.companyProfileId").value(profile.getId().toString()));

        mockMvc.perform(get("/api/v1/company/internships").with(asUser(recruiterUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ===== 4. Revoked member is denied access =====

    @Test
    void revokedMemberIsDeniedAccess() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);

        User revokedUser = createUser(Role.COMPANY);
        addMember(profile, revokedUser, AtsMemberRole.RECRUITER, MemberStatus.REVOKED);

        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(revokedUser)))
                .andExpect(status().isNotFound());

        // listOwnInternships degrades to an empty list for a non-member rather than erroring —
        // preserved pre-existing behavior for "no company profile at all".
        mockMvc.perform(get("/api/v1/company/internships").with(asUser(revokedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ===== 5. Non-member is denied access =====

    @Test
    void nonMemberIsDeniedAccess() throws Exception {
        User standaloneCompanyUser = createUser(Role.COMPANY);

        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(standaloneCompanyUser)))
                .andExpect(status().isNotFound());
    }

    // ===== 6. Unapproved company restrictions =====

    @Test
    void recruiterOfUnapprovedCompanyCanCheckMembershipButNotUseMarketplaceFeatures() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.PENDING);

        User recruiterUser = createUser(Role.COMPANY);
        addMember(profile, recruiterUser, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        // Self-check is always reachable, mirroring getApprovalStatus's existing convention
        // that a PENDING/REJECTED company must still be able to see its own status.
        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(recruiterUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"));

        mockMvc.perform(post("/api/v1/company/internships")
                        .with(asUser(recruiterUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(validInternshipCreateBody())))
                .andExpect(status().isForbidden());
    }

    // ===== 7. Cross-company data isolation =====

    @Test
    void recruiterCannotAccessAnotherCompanysInternship() throws Exception {
        User ownerA = createUser(Role.COMPANY);
        CompanyProfile companyA = createCompanyProfile(ownerA, ApprovalStatus.APPROVED);

        User ownerB = createUser(Role.COMPANY);
        CompanyProfile companyB = createCompanyProfile(ownerB, ApprovalStatus.APPROVED);
        Internship internshipB = createInternship(companyB);

        User recruiterOfA = createUser(Role.COMPANY);
        addMember(companyA, recruiterOfA, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/company/internships/" + internshipB.getId()).with(asUser(recruiterOfA)))
                .andExpect(status().isNotFound());
    }

    // ===== 8. OWNER-only operations =====

    @Test
    void recruiterCannotEditCompanyProfileOrCompleteOnboardingButCanRead() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);

        User recruiterUser = createUser(Role.COMPANY);
        addMember(profile, recruiterUser, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/company/profile")
                        .with(asUser(recruiterUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("companyName", "Hijacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/company/profile/complete").with(asUser(recruiterUser)))
                .andExpect(status().isForbidden());

        // Reading remains allowed at member level — distinguishing OWNER-only from
        // member-level permissions, not blanket-blocking recruiters from the profile.
        mockMvc.perform(get("/api/v1/company/profile").with(asUser(recruiterUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Pathora Technologies"));

        // The profile itself was never touched by the rejected PATCH attempt.
        CompanyProfile reloaded = companyProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(reloaded.getCompanyName()).isEqualTo("Pathora Technologies");
    }

    // ===== 9. INTERN and platform ADMIN cannot use company-only ATS endpoints =====

    @Test
    void internCannotUseCompanyMembershipEndpoint() throws Exception {
        User internUser = createUser(Role.INTERN);

        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(internUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminCannotUseCompanyMembershipEndpoint() throws Exception {
        User adminUser = createUser(Role.ADMIN);

        mockMvc.perform(get("/api/v1/company/ats/members/me").with(asUser(adminUser)))
                .andExpect(status().isForbidden());
    }

    // ===== 10. Existing company application endpoints continue working =====

    @Test
    void existingOwnerApplicationAndQuestionEndpointsStillWork() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/applications")
                        .with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/application-questions")
                        .with(asUser(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void activeRecruiterCanAlsoUseApplicationAndQuestionEndpoints() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile profile = createCompanyProfile(owner, ApprovalStatus.APPROVED);
        Internship internship = createInternship(profile);

        User recruiterUser = createUser(Role.COMPANY);
        addMember(profile, recruiterUser, AtsMemberRole.RECRUITER, MemberStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/applications")
                        .with(asUser(recruiterUser)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company/internships/" + internship.getId() + "/application-questions")
                        .with(asUser(recruiterUser)))
                .andExpect(status().isOk());
    }
}
