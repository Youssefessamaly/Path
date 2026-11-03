package com.internplatform.ats.access;

/**
 * A company member's role within the ATS. OWNER is always derived from the pre-existing
 * company_profiles.user_id relationship (see CompanyAccessResolver) — it is never granted via
 * an ats_company_members row alone. RECRUITER and INTERVIEWER exist only as explicit
 * ats_company_members rows. INTERVIEWER is not yet granted any access by any endpoint in this
 * phase; the value exists now so a future phase needs no migration to introduce it.
 */
public enum AtsMemberRole {
    OWNER,
    RECRUITER,
    INTERVIEWER
}
