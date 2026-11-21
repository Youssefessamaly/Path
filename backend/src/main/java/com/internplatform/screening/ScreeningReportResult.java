package com.internplatform.screening;

import com.internplatform.internprofile.EvidenceSource;
import com.internplatform.internprofile.EvidenceStrength;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** One requirement's result within one ScreeningReport — see V28. */
@Entity
@Table(name = "screening_report_results")
public class ScreeningReportResult {

    @Id
    private UUID id;

    @Column(name = "screening_report_id", nullable = false)
    private UUID screeningReportId;

    @Column(name = "internship_rubric_requirement_id", nullable = false)
    private UUID internshipRubricRequirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 25)
    private ScreeningResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", length = 30)
    private EvidenceSource evidenceSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_strength", length = 10)
    private EvidenceStrength evidenceStrength;

    @Column(name = "text_reference", length = 300)
    private String textReference;

    @Column(name = "reasoning", nullable = false, length = 300)
    private String reasoning;

    protected ScreeningReportResult() {
    }

    public ScreeningReportResult(UUID screeningReportId, UUID internshipRubricRequirementId, ScreeningResult result) {
        this.screeningReportId = screeningReportId;
        this.internshipRubricRequirementId = internshipRubricRequirementId;
        this.result = result;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getScreeningReportId() {
        return screeningReportId;
    }

    public UUID getInternshipRubricRequirementId() {
        return internshipRubricRequirementId;
    }

    public ScreeningResult getResult() {
        return result;
    }

    public EvidenceSource getEvidenceSource() {
        return evidenceSource;
    }

    public void setEvidenceSource(EvidenceSource evidenceSource) {
        this.evidenceSource = evidenceSource;
    }

    public EvidenceStrength getEvidenceStrength() {
        return evidenceStrength;
    }

    public void setEvidenceStrength(EvidenceStrength evidenceStrength) {
        this.evidenceStrength = evidenceStrength;
    }

    public String getTextReference() {
        return textReference;
    }

    public void setTextReference(String textReference) {
        this.textReference = textReference;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
