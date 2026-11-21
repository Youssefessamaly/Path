package com.internplatform.screening;

import com.internplatform.internshipapplication.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row in the paginated screening summary list — see ScreeningService#listSummaries. Pure
 * read of already-stored reports; never triggers screening. latestReport fields are null when
 * the application has never been screened.
 */
public record ScreeningSummaryResponse(
        UUID applicationId,
        String applicantName,
        ApplicationStatus applicationStatus,
        UUID latestReportId,
        OffsetDateTime latestEvaluatedAt,
        Boolean needsReview,
        Integer weightedCoveragePercentage,
        Integer matchedRequiredCount,
        Integer totalRequiredCount
) {
}
