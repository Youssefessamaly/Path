package com.internplatform.ats.access;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The authenticated user's own company membership — returned by GET /company/ats/members/me.
 * memberId is null only when no ats_company_members row exists yet for an owner resolved via
 * the company_profiles.user_id fast-path (see CompanyAccessResolver) — the membership itself
 * is still fully valid and ACTIVE in that case.
 */
public record CompanyMemberResponse(
        UUID memberId,
        UUID companyProfileId,
        AtsMemberRole role,
        MemberStatus status,
        OffsetDateTime createdAt
) {
}
