package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * One configurable requirement within a reusable ScreeningProfile template — see V26. Either
 * skillId (a canonical catalog skill) or label (a free-text qualification) is present; never
 * neither — enforced by both the DB CHECK constraint and RequirementValidationSupport.
 */
@Entity
@Table(name = "screening_profile_requirements")
public class ScreeningProfileRequirement {

    @Id
    private UUID id;

    @Column(name = "screening_profile_id", nullable = false)
    private UUID screeningProfileId;

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

    // EAGER is deliberate: this is a tiny (at most 8 values), always-needed-in-the-response
    // collection. Left at JPA's default LAZY, a requirement fetched fresh (e.g. GET, PATCH,
    // archive/activate — anything that doesn't construct or explicitly copy the entity within
    // the same transaction) would carry an uninitialized Hibernate proxy here, which then
    // throws LazyInitializationException when Jackson serializes the HTTP response AFTER the
    // transaction/session has already closed. A @Transactional-wrapped test (one long-lived
    // session spanning the whole test) cannot observe this; only a real HTTP round-trip can —
    // confirmed via live end-to-end verification, not a test.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "screening_profile_requirement_evidence_sources",
            joinColumns = @JoinColumn(name = "requirement_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", length = 30)
    private Set<EvidenceSource> acceptedEvidenceSources = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ScreeningProfileRequirement() {
    }

    public ScreeningProfileRequirement(UUID screeningProfileId, RequirementType requirementType) {
        this.screeningProfileId = screeningProfileId;
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

    public UUID getScreeningProfileId() {
        return screeningProfileId;
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
