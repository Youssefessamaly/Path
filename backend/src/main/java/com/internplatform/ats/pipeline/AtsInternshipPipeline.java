package com.internplatform.ats.pipeline;

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
 * One internship's own, independent recruitment pipeline. Its stages (AtsStage) are deep-copied
 * at assignment time from a company AtsPipelineTemplate (or from a hardcoded starter — see
 * InternshipPipelineService) — never a live reference. sourceTemplateId is a best-effort
 * pointer only (nulled if the template is ever removed); sourceTemplateName is the immutable
 * record of what this pipeline was actually assigned from.
 */
@Entity
@Table(name = "ats_internship_pipelines")
public class AtsInternshipPipeline {

    @Id
    private UUID id;

    @Column(name = "internship_id", nullable = false, unique = true)
    private UUID internshipId;

    @Column(name = "company_profile_id", nullable = false)
    private UUID companyProfileId;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @Column(name = "source_template_name", length = 120)
    private String sourceTemplateName;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AtsInternshipPipeline() {
    }

    public AtsInternshipPipeline(UUID internshipId, UUID companyProfileId) {
        this.internshipId = internshipId;
        this.companyProfileId = companyProfileId;
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

    public UUID getCompanyProfileId() {
        return companyProfileId;
    }

    public UUID getSourceTemplateId() {
        return sourceTemplateId;
    }

    public void setSourceTemplateId(UUID sourceTemplateId) {
        this.sourceTemplateId = sourceTemplateId;
    }

    public String getSourceTemplateName() {
        return sourceTemplateName;
    }

    public void setSourceTemplateName(String sourceTemplateName) {
        this.sourceTemplateName = sourceTemplateName;
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
