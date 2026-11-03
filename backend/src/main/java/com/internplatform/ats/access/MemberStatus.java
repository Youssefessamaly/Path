package com.internplatform.ats.access;

/**
 * Lifecycle status of an ats_company_members row. Only ACTIVE grants any access — see
 * CompanyAccessResolver. INVITED (not yet accepted) and REVOKED are both rejected identically
 * to having no membership at all. No invitation flow exists yet in this phase; the INVITED
 * value exists now so a future invitation phase needs no migration to introduce it.
 */
public enum MemberStatus {
    ACTIVE,
    INVITED,
    REVOKED
}
