package com.internplatform.internshipapplication;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InternshipApplicationResponse(
        UUID applicationId,
        UUID internshipId,
        String internshipTitle,
        String companyName,
        String companyLogoUrl,
        ApplicationStatus status,
        String coverLetter,
        String linkedinUrl,
        String portfolioUrl,
        String cvFileName,
        OffsetDateTime appliedAt,
        OffsetDateTime updatedAt,
        /** Empty for the compact list view; populated with the submitted snapshot (in display
         *  order) for a single application's detail — see InternshipApplicationService. */
        List<InternshipApplicationAnswerResponse> answers,
        /** The applicant's own current choice for this application — never the recruiter's.
         *  false after submission with no consent, or after withdrawal via DELETE
         *  /api/v1/intern/applications/{id}/ai-analysis-consent. */
        boolean aiAnalysisConsent
) {
}
