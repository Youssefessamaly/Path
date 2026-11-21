package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One configurable requirement within one InternshipRubric version — see V27. Mirrors
 * ScreeningProfileRequirement field-for-field but is a separate table/entity on purpose: a
 * rubric version's requirements must stay frozen once ACTIVE/ARCHIVED (see
 * InternshipRubricService), independent of whatever the source profile does afterward.
 */
@Entity
@Table(name = "internship_rubric_requirements")
public class InternshipRubricRequirement {

    @Id
    private UUID id;

    @Column(name = "internship_rubric_id", nullable = false)
    private UUID internshipRubricId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 10)
    private RequirementType requirementType;

    @Column(name = "skill_id")
    private UUID skillId;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_evidence_strength", length = 10)
    private EvidenceStrength minimumEvidenceStrength;

    @Column(name = "recruiter_notes", length = 1000)
    private String recruiterNotes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @ElementCollection
    @CollectionTable(
            name = "internship_rubric_requirement_evidence_sources",
            joinColumns = @JoinColumn(name = "requirement_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", length = 30)
    private Set<EvidenceSource> acceptedEvidenceSources = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InternshipRubricRequirement() {
    }

    public InternshipRubricRequirement(UUID internshipRubricId, RequirementType requirementType) {
        this.internshipRubricId = internshipRubricId;
        this.requirementType = requirementType;
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

    public UUID getInternshipRubricId() {
        return internshipRubricId;
    }

    public RequirementType getRequirementType() {
        return requirementType;
    }

    public void setRequirementType(RequirementType requirementType) {
        this.requirementType = requirementType;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public void setSkillId(UUID skillId) {
        this.skillId = skillId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public EvidenceStrength getMinimumEvidenceStrength() {
        return minimumEvidenceStrength;
    }

    public void setMinimumEvidenceStrength(EvidenceStrength minimumEvidenceStrength) {
        this.minimumEvidenceStrength = minimumEvidenceStrength;
    }

    public String getRecruiterNotes() {
        return recruiterNotes;
    }

    public void setRecruiterNotes(String recruiterNotes) {
        this.recruiterNotes = recruiterNotes;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Set<EvidenceSource> getAcceptedEvidenceSources() {
        return acceptedEvidenceSources;
    }

    public void setAcceptedEvidenceSources(Set<EvidenceSource> acceptedEvidenceSources) {
        this.acceptedEvidenceSources = acceptedEvidenceSources;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
