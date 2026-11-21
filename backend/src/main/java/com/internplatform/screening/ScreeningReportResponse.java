package com.internplatform.screening;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ScreeningReportResponse(
        UUID id,
        UUID internshipId,
        UUID applicationId,
        UUID internshipRubricId,
        int rubricVersion,
        OffsetDateTime evaluatedAt,
        String algorithmVersion,
        boolean cvTextUnavailable,
        boolean needsReview,

        /** Eligibility criteria — REQUIRED requirements only. Kept separate from the
         *  weighted figure below; never used to automatically reject an application. */
        int totalRequiredCount,
        int matchedRequiredCount,

        int totalPreferredCount,
        int matchedPreferredCount,

        int totalBonusCount,
        int matchedBonusCount,

        /**
         * Weighted coverage across every requirement (REQUIRED + PREFERRED + BONUS), 0-100.
         * A transparent, deterministic ratio of matched weight to total weight — NOT a
         * probability or scientifically validated prediction of candidate success, and never
         * a basis for automatic rejection.
         */
        int weightedCoveragePercentage,

        List<ScreeningResultResponse> results
) {
}
