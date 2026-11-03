package com.internplatform.ats.candidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One append-only stage-transition record. fromStageName/toStageName are snapshotted alongside
 * the (nullable-on-from) stage ids so history stays readable even after a stage is later
 * renamed — the same snapshot-for-history-integrity pattern
 * internship_application_answers.question_text_snapshot already established.
 */
@Entity
@Table(name = "ats_stage_events")
public class AtsStageEvent {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "internship_id", nullable = false)
    private UUID internshipId;

    @Column(name = "company_profile_id", nullable = false)
    private UUID companyProfileId;

    @Column(name = "from_stage_id")
    private UUID fromStageId;

    @Column(name = "to_stage_id", nullable = false)
    private UUID toStageId;

    @Column(name = "from_stage_name", length = 80)
    private String fromStageName;

    @Column(name = "to_stage_name", nullable = false, length = 80)
    private String toStageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_kind", nullable = false, length = 20)
    private StageEventKind eventKind;

    /** The company member who performed the transition — always null in this phase (every
     *  event is system-initiated). Reserved for the future candidate-movement phase. */
    @Column(name = "actor_member_id")
    private UUID actorMemberId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AtsStageEvent() {
    }

    public AtsStageEvent(
            UUID applicationId,
            UUID internshipId,
            UUID companyProfileId,
            UUID fromStageId,
            UUID toStageId,
            String fromStageName,
            String toStageName,
            StageEventKind eventKind,
            UUID actorMemberId
    ) {
        this.applicationId = applicationId;
        this.internshipId = internshipId;
        this.companyProfileId = companyProfileId;
        this.fromStageId = fromStageId;
        this.toStageId = toStageId;
        this.fromStageName = fromStageName;
        this.toStageName = toStageName;
        this.eventKind = eventKind;
        this.actorMemberId = actorMemberId;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getId() {
        return id;
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

    public UUID getFromStageId() {
        return fromStageId;
    }

    public UUID getToStageId() {
        return toStageId;
    }

    public String getFromStageName() {
        return fromStageName;
    }

    public String getToStageName() {
        return toStageName;
    }

    public StageEventKind getEventKind() {
        return eventKind;
    }

    public UUID getActorMemberId() {
        return actorMemberId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
