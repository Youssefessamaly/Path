package com.internplatform.ats.access;

import java.util.UUID;

/**
 * The authenticated user's resolved company access, as determined by CompanyAccessResolver.
 * Every company-scoped service should resolve this instead of re-deriving company identity
 * from a raw userId via CompanyProfileRepository directly — see CompanyAccessResolver for why.
 *
 * memberId is nullable: it is null when access was resolved via the owner fast-path and no
 * corresponding ats_company_members row exists yet (e.g. a company profile created directly,
 * before this resolver existed, with no backfill applied) — the owner relationship itself is
 * still fully authoritative in that case.
 */
public record CompanyContext(
        UUID companyProfileId,
        UUID memberId,
        UUID userId,
        AtsMemberRole role
) {
    public boolean isOwner() {
        return role == AtsMemberRole.OWNER;
    }
}
