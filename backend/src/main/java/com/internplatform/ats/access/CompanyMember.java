package com.internplatform.ats.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One user's authorization to act on behalf of one company. The OWNER row for a given company
 * is informational/complete-record only — see CompanyAccessResolver, which always resolves an
 * owner via company_profiles.user_id directly and never requires this row to exist. RECRUITER
 * and INTERVIEWER rows are the only path by which a non-owner is ever resolved.
 */
@Entity
@Table(name = "ats_company_members")
public class CompanyMember {

    @Id
    private UUID id;

    @Column(name = "company_profile_id", nullable = false)
    private UUID companyProfileId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AtsMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CompanyMember() {
    }

    public CompanyMember(UUID companyProfileId, UUID userId, AtsMemberRole role, MemberStatus status) {
        this.companyProfileId = companyProfileId;
        this.userId = userId;
        this.role = role;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (id == null) {
            id = UUID.randomUUID();
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyProfileId() {
        return companyProfileId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AtsMemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
