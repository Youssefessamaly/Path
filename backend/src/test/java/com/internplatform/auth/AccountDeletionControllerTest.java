package com.internplatform.auth;

import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.security.JwtService;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Database-backed account deletion tests. Run against the configured test PostgreSQL DB. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountDeletionControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired InternProfileRepository interns;
    @Autowired CompanyProfileRepository companies;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService tokens;
    @Autowired JdbcTemplate jdbc;

    private User newUser(Role role) {
        return users.saveAndFlush(new User(
                role.name().toLowerCase() + '-' + UUID.randomUUID() + "@example.test",
                encoder.encode("CorrectPassword123!"), role));
    }

    private RequestPostProcessor asUser(User user) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(user.getId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void anonymousDeletionDenied() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"CorrectPassword123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordDoesNotDeleteAnything() throws Exception {
        User user = newUser(Role.INTERN);
        mockMvc.perform(delete("/api/v1/auth/me").with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Nope123!\"}"))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId())).isPresent();
    }

    @Test
    void internDeletionCascadesAndQueuesEntireCvDirectory() throws Exception {
        User user = newUser(Role.INTERN);
        InternProfile profile = interns.saveAndFlush(new InternProfile(user.getId()));
        mockMvc.perform(delete("/api/v1/auth/me").with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"CorrectPassword123!\"}"))
                .andExpect(status().isNoContent());
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(interns.findByUserId(user.getId())).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM pending_cv_deletions WHERE task_key = ?",
                "profile:" + profile.getId())).isEqualTo(1);
    }

    @Test
    void previouslyIssuedJwtIsDeniedImmediatelyAfterDeletion() throws Exception {
        User user = newUser(Role.INTERN);
        String realSignedToken = tokens.generateToken(user);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + realSignedToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/auth/me").with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"CorrectPassword123!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + realSignedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotSelfDeleteThroughPublicEndpoint() throws Exception {
        User admin = newUser(Role.ADMIN);
        mockMvc.perform(delete("/api/v1/auth/me").with(asUser(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"CorrectPassword123!\"}"))
                .andExpect(status().isForbidden());
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void companyDeletionClearsRestrictConstraintsAndOnlyCompanyApplicationSnapshots() throws Exception {
        User owner = newUser(Role.COMPANY);
        CompanyProfile company = companies.saveAndFlush(new CompanyProfile(owner.getId()));
        User student = newUser(Role.INTERN);
        InternProfile candidate = interns.saveAndFlush(new InternProfile(student.getId()));
        UUID internshipId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        String snapshotKey = candidate.getId() + "/" + UUID.randomUUID() + ".pdf";

        jdbc.update("""
                INSERT INTO internships(id, company_profile_id, title, description, location,
                    work_mode, application_deadline, status, created_at, updated_at, application_method)
                VALUES (?, ?, 'Test', 'Description', 'Remote', 'REMOTE', CURRENT_DATE + 30,
                    'OPEN', now(), now(), 'INTERNAL')
                """, internshipId, company.getId());
        jdbc.update("""
                INSERT INTO internship_applications(id, internship_id, intern_profile_id, status,
                    cv_file_name, cv_storage_key, cv_content_type, cv_size_bytes, cv_uploaded_at,
                    applied_at, updated_at)
                VALUES (?, ?, ?, 'APPLIED', 'cv.pdf', ?, 'application/pdf', 10, now(), now(), now())
                """, applicationId, internshipId, candidate.getId(), snapshotKey);
        jdbc.update("""
                INSERT INTO internship_application_questions(id, internship_id, question_text,
                    question_type, required, display_order, created_at, updated_at)
                VALUES (?, ?, 'Why?', 'SHORT_TEXT', true, 1, now(), now())
                """, questionId, internshipId);
        jdbc.update("""
                INSERT INTO internship_application_answers(id, application_id, question_id,
                    question_text_snapshot, question_type_snapshot, required_snapshot,
                    display_order_snapshot, answer_text, created_at)
                VALUES (?, ?, ?, 'Why?', 'SHORT_TEXT', true, 1, 'An answer', now())
                """, UUID.randomUUID(), applicationId, questionId);
        jdbc.update("""
                INSERT INTO ats_internship_pipelines(id, internship_id, company_profile_id,
                    created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, pipelineId, internshipId, company.getId());
        jdbc.update("""
                INSERT INTO ats_stages(id, pipeline_id, name, position, created_at, updated_at)
                VALUES (?, ?, 'Applied', 0, now(), now())
                """, stageId, pipelineId);
        jdbc.update("""
                INSERT INTO ats_application_states(application_id, internship_id,
                    company_profile_id, pipeline_id, current_stage_id,
                    entered_current_stage_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now(), now())
                """, applicationId, internshipId, company.getId(), pipelineId, stageId);
        jdbc.update("""
                INSERT INTO ats_stage_events(id, application_id, internship_id, company_profile_id,
                    to_stage_id, to_stage_name, event_kind, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'Applied', 'ENTERED', now())
                """, UUID.randomUUID(), applicationId, internshipId, company.getId(), stageId);

        mockMvc.perform(delete("/api/v1/auth/me").with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"CorrectPassword123!\"}"))
                .andExpect(status().isNoContent());

        assertThat(users.findById(owner.getId())).isEmpty();
        assertThat(users.findById(student.getId())).isPresent();
        assertThat(interns.findById(candidate.getId())).isPresent();
        assertThat(count("SELECT COUNT(*) FROM internships WHERE id = ?", internshipId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM internship_applications WHERE id = ?", applicationId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM pending_cv_deletions WHERE task_key = ?",
                "file:" + snapshotKey)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM pending_cv_deletions WHERE task_key = ?",
                "profile:" + candidate.getId())).isZero();
    }
}
