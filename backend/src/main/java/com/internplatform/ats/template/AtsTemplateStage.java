package com.internplatform.ats.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** One ordered stage within a pipeline template. Deliberately carries no "kind"/outcome
 *  marker — ACCEPTED/REJECTED/WITHDRAWN are never modeled as a stage; they remain exclusively
 *  internship_applications.status values. */
@Entity
@Table(name = "ats_template_stages")
public class AtsTemplateStage {

    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AtsTemplateStage() {
    }

    public AtsTemplateStage(UUID templateId, String name, int position) {
        this.templateId = templateId;
        this.name = name;
        this.position = position;
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

    public UUID getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
