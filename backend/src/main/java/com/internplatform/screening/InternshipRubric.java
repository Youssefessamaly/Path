package com.internplatform.screening;

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
 * One versioned screening rubric attached to a single internship — see V27. DRAFT versions
 * are freely editable; ACTIVE/ARCHIVED versions are immutable (see InternshipRubricService),
 * so a screening result can always cite exactly which rubric version it was produced against.
 */
@Entity
@Table(name = "internship_rubrics")
public class InternshipRubric {

    @Id
    private UUID id;

    @Column(name = "internship_id", nullable = false)
    private UUID internshipId;

    /** Which template this rubric's requirements were copied from, if any — informational
     *  only, never a live reference (see ScreeningProfile javadoc). */
    @Column(name = "screening_profile_id")
    private UUID screeningProfileId;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private RubricStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    protected InternshipRubric() {
    }

    public InternshipRubric(UUID internshipId, UUID screeningProfileId, int version) {
        this.internshipId = internshipId;
        this.screeningProfileId = screeningProfileId;
        this.version = version;
        this.status = RubricStatus.DRAFT;
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

    public UUID getInternshipId() {
        return internshipId;
    }

    public UUID getScreeningProfileId() {
        return screeningProfileId;
    }

    public int getVersion() {
        return version;
    }

    public RubricStatus getStatus() {
        return status;
    }

    public void setStatus(RubricStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(OffsetDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }
}
