package com.internplatform.internprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The strongest supporting evidence found for one skill in an intern's most recently uploaded
 * CV — see V25. Fully replaced (deleted and re-inserted), never merged, on every CV upload,
 * mirroring the existing intern_cv_skills replace-not-merge semantics. Plain UUID foreign keys
 * rather than managed associations, matching InternshipFavorite's style for this kind of
 * profile-owned link entity.
 */
@Entity
@Table(
        name = "intern_cv_skill_evidence",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_intern_cv_skill_evidence_profile_skill",
                columnNames = {"intern_profile_id", "skill_id"}
        )
)
public class InternCvSkillEvidence {

    @Id
    private UUID id;

    @Column(name = "intern_profile_id", nullable = false)
    private UUID internProfileId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", nullable = false, length = 30)
    private EvidenceSource evidenceSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_strength", nullable = false, length = 10)
    private EvidenceStrength evidenceStrength;

    @Column(name = "text_reference", length = 300)
    private String textReference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InternCvSkillEvidence() {
    }

    public InternCvSkillEvidence(
            UUID internProfileId,
            UUID skillId,
            EvidenceSource evidenceSource,
            EvidenceStrength evidenceStrength,
            String textReference
    ) {
        this.internProfileId = internProfileId;
        this.skillId = skillId;
        this.evidenceSource = evidenceSource;
        this.evidenceStrength = evidenceStrength;
        this.textReference = textReference;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getInternProfileId() {
        return internProfileId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public EvidenceSource getEvidenceSource() {
        return evidenceSource;
    }

    public EvidenceStrength getEvidenceStrength() {
        return evidenceStrength;
    }

    public String getTextReference() {
        return textReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
