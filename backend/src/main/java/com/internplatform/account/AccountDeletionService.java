package com.internplatform.account;

import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Deletes a user's complete account and its dependent DB rows in a single transaction.
 * CV file cleanup tasks are persisted IN the same transaction and executed only AFTER COMMIT.
 * No global skill-catalog rows or other users' profile/CV files are removed.
 */
@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final InternProfileRepository interns;
    private final CompanyProfileRepository companies;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher publisher;

    public AccountDeletionService(UserRepository users,
                                  InternProfileRepository interns,
                                  CompanyProfileRepository companies,
                                  JdbcTemplate jdbc,
                                  PasswordEncoder passwords,
                                  ApplicationEventPublisher publisher) {
        this.users = users;
        this.interns = interns;
        this.companies = companies;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.publisher = publisher;
    }

    @Transactional
    public void deleteAccount(UUID userId, String currentPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session no longer valid"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account disabled");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be self-deleted");
        }
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect current password");
        }

        if (user.getRole() == Role.INTERN) {
            // All profile CVs and application-time copies for this intern live under this
            // single server-generated profile directory, including any past orphan files.
            interns.findByUserId(userId).ifPresent(profile -> {
                jdbc.update("INSERT INTO pending_cv_deletions(task_key) VALUES (?) ON CONFLICT DO NOTHING",
                        "profile:" + profile.getId());
                jdbc.update("INSERT INTO pending_cv_deletions(task_key) VALUES (?) ON CONFLICT DO NOTHING",
                        "photo-profile:" + profile.getId());
            });
        } else if (user.getRole() == Role.COMPANY) {
            companies.findByUserId(userId).ifPresent(company -> {
                UUID companyId = company.getId();
                jdbc.update("INSERT INTO pending_cv_deletions(task_key) VALUES (?) ON CONFLICT DO NOTHING",
                        "photo-profile:" + companyId);
                // Snapshot files belong to OTHER users' profile directories; remove ONLY the
                // copies submitted to this company's postings, never those users' own CVs.
                jdbc.update("""
                        INSERT INTO pending_cv_deletions(task_key)
                        SELECT DISTINCT 'file:' || a.cv_storage_key
                          FROM internship_applications a
                          JOIN internships i ON i.id = a.internship_id
                         WHERE i.company_profile_id = ?
                        ON CONFLICT DO NOTHING
                        """, companyId);

                // These RESTRICT relationships must be cleared before deleting the company
                // and cascading through stages, applications, and listing questions.
                jdbc.update("DELETE FROM ats_stage_events WHERE company_profile_id = ?", companyId);
                jdbc.update("DELETE FROM ats_application_states WHERE company_profile_id = ?", companyId);
                jdbc.update("""
                        DELETE FROM internship_application_answers a
                        USING internship_application_questions q, internships i
                        WHERE a.question_id = q.id
                          AND q.internship_id = i.id
                          AND i.company_profile_id = ?
                        """, companyId);
            });
        }

        // Database FKs ON DELETE CASCADE remove the user's profile, account memberships,
        // applications, saved internships, posted internships and pipeline records. This is
        // atomic with the cleanup queue inserts: SQL errors roll back EVERYTHING.
        users.delete(user);
        users.flush();
        publisher.publishEvent(new AccountDeletedEvent());
    }
}
