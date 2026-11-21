package com.internplatform.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One deterministic, explainable evaluation of an application against a frozen
 * InternshipRubric version — see V28. Append-only: never updated once created, so a past
 * report always reflects exactly what was true when it was produced (see ScreeningService).
 */
@Entity
@Table(name = "screening_reports")
public class ScreeningReport {

    @Id
    private UUID id;

    @Column(name = "internship_id", nullable = false)
    private UUID internshipId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "internship_rubric_id", nullable = false)
    private UUID internshipRubricId;

    @Column(name = "rubric_version", nullable = false)
    private int rubricVersion;

    @Column(name = "cv_snapshot_storage_key", nullable = false, length = 255)
    private String cvSnapshotStorageKey;

    @Column(name = "cv_text_unavailable", nullable = false)
    private boolean cvTextUnavailable;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @Column(name = "algorithm_version", nullable = false, length = 50)
    private String algorithmVersion;

    @Column(name = "total_required_count", nullable = false)
    private int totalRequiredCount;

    @Column(name = "matched_required_count", nullable = false)
    private int matchedRequiredCount;

    @Column(name = "total_preferred_count", nullable = false)
    private int totalPreferredCount;

    @Column(name = "matched_preferred_count", nullable = false)
    private int matchedPreferredCount;

    @Column(name = "total_bonus_count", nullable = false)
    private int totalBonusCount;

    @Column(name = "matched_bonus_count", nullable = false)
    private int matchedBonusCount;

    @Column(name = "weighted_coverage_percentage", nullable = false)
    private int weightedCoveragePercentage;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    protected ScreeningReport() {
    }

    public ScreeningReport(
            UUID internshipId,
            UUID applicationId,
            UUID internshipRubricId,
            int rubricVersion,
            String cvSnapshotStorageKey,
            String algorithmVersion
    ) {
        this.internshipId = internshipId;
        this.applicationId = applicationId;
        this.internshipRubricId = internshipRubricId;
        this.rubricVersion = rubricVersion;
        this.cvSnapshotStorageKey = cvSnapshotStorageKey;
        this.algorithmVersion = algorithmVersion;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        evaluatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getInternshipId() {
        return internshipId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getInternshipRubricId() {
        return internshipRubricId;
    }

    public int getRubricVersion() {
        return rubricVersion;
    }

    public String getCvSnapshotStorageKey() {
        return cvSnapshotStorageKey;
    }

    public boolean isCvTextUnavailable() {
        return cvTextUnavailable;
    }

    public void setCvTextUnavailable(boolean cvTextUnavailable) {
        this.cvTextUnavailable = cvTextUnavailable;
    }

    public OffsetDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public int getTotalRequiredCount() {
        return totalRequiredCount;
    }

    public void setTotalRequiredCount(int totalRequiredCount) {
        this.totalRequiredCount = totalRequiredCount;
    }

    public int getMatchedRequiredCount() {
        return matchedRequiredCount;
    }

    public void setMatchedRequiredCount(int matchedRequiredCount) {
        this.matchedRequiredCount = matchedRequiredCount;
    }

    public int getTotalPreferredCount() {
        return totalPreferredCount;
    }

    public void setTotalPreferredCount(int totalPreferredCount) {
        this.totalPreferredCount = totalPreferredCount;
    }

    public int getMatchedPreferredCount() {
        return matchedPreferredCount;
    }

    public void setMatchedPreferredCount(int matchedPreferredCount) {
        this.matchedPreferredCount = matchedPreferredCount;
    }

    public int getTotalBonusCount() {
        return totalBonusCount;
    }

    public void setTotalBonusCount(int totalBonusCount) {
        this.totalBonusCount = totalBonusCount;
    }

    public int getMatchedBonusCount() {
        return matchedBonusCount;
    }

    public void setMatchedBonusCount(int matchedBonusCount) {
        this.matchedBonusCount = matchedBonusCount;
    }

    public int getWeightedCoveragePercentage() {
        return weightedCoveragePercentage;
    }

    public void setWeightedCoveragePercentage(int weightedCoveragePercentage) {
        this.weightedCoveragePercentage = weightedCoveragePercentage;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }
}
