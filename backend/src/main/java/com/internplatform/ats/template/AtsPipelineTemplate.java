package com.internplatform.ats.template;

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
 * A company-owned, reusable recruitment pipeline template. Its stages (AtsTemplateStage) are
 * deep-copied — never referenced live — into an independent AtsStage set whenever this template
 * is assigned to an internship (see InternshipPipelineService), so editing a template after
 * that point can never alter an already-materialized internship pipeline.
 */
@Entity
@Table(name = "ats_pipeline_templates")
public class AtsPipelineTemplate {

    @Id
    private UUID id;

    @Column(name = "company_profile_id", nullable = false)
    private UUID companyProfileId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    /** Optimistic lock — protects two concurrent rename/archive/default-toggle requests from
     *  silently clobbering each other. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AtsPipelineTemplate() {
    }

    public AtsPipelineTemplate(UUID companyProfileId, String name) {
        this.companyProfileId = companyProfileId;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDefaultTemplate() {
        return defaultTemplate;
    }

    public void setDefaultTemplate(boolean defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Archiving also clears defaultTemplate — an archived template must never remain "the"
     *  default a new internship falls back to. */
    public void archive(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
        this.defaultTemplate = false;
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
