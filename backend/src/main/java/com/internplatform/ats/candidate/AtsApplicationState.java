package com.internplatform.ats.candidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Tracks one internship_applications row's position in its internship's recruitment pipeline.
 * applicationId is this entity's own primary key (not a separately generated id) — a single
 * row can therefore ever exist per application, by construction; see
 * AtsApplicationIntakeService for why this is the actual duplicate-prevention mechanism.
 *
 * Deliberately independent of internship_applications.status: a row existing here only ever
 * means "this application is being tracked through the pipeline's stages" and never overrides
 * or duplicates the application's own public ACCEPTED/REJECTED/APPLIED outcome.
 */
@Entity
@Table(name = "ats_application_states")
public class AtsApplicationState {

    @Id
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "internship_id", nullable = false)
    private UUID internshipId;

    @Column(name = "company_profile_id", nullable = false)
    private UUID companyProfileId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "current_stage_id", nullable = false)
    private UUID currentStageId;

    @Column(name = "entered_current_stage_at", nullable = false)
    private OffsetDateTime enteredCurrentStageAt;

    /** Optimistic lock — protects a future stage-move endpoint (not implemented in this
     *  phase) from two concurrent moves silently clobbering each other. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AtsApplicationState() {
    }

    public AtsApplicationState(
            UUID applicationId,
            UUID internshipId,
            UUID companyProfileId,
            UUID pipelineId,
            UUID currentStageId,
            OffsetDateTime enteredCurrentStageAt
    ) {
        this.applicationId = applicationId;
        this.internshipId = internshipId;
        this.companyProfileId = companyProfileId;
        this.pipelineId = pipelineId;
        this.currentStageId = currentStageId;
        this.enteredCurrentStageAt = enteredCurrentStageAt;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getInternshipId() {
        return internshipId;
    }

    public UUID getCompanyProfileId() {
        return companyProfileId;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public UUID getCurrentStageId() {
        return currentStageId;
    }

    public OffsetDateTime getEnteredCurrentStageAt() {
        return enteredCurrentStageAt;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
