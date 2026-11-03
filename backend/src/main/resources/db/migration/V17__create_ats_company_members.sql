-- Introduces company membership so more than one authenticated user can be authorized to act
-- on behalf of a company (OWNER, RECRUITER, INTERVIEWER) — the ATS access foundation; see
-- com.internplatform.ats.access.CompanyAccessResolver. Purely additive: no existing table,
-- column, row, or constraint is altered.
--
-- The pre-existing 1:1 relationship (company_profiles.user_id UNIQUE) remains the
-- authoritative, untouched owner relationship — CompanyAccessResolver always resolves a
-- company's owner through it directly, never through this table. This table is instead the
-- ONLY path by which a RECRUITER or INTERVIEWER (who has no company_profiles row of their own)
-- is ever resolved, and it additionally records every owner (backfilled below) for
-- completeness and future team-management listing. A user may hold separate membership rows
-- across different companies (future multi-company support) — nothing here constrains a user
-- to a single company.
CREATE TABLE ats_company_members (
    id UUID PRIMARY KEY,

    company_profile_id UUID NOT NULL,
    user_id UUID NOT NULL,

    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_company_members_company
        FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_company_members_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_ats_company_members_role
        CHECK (role IN ('OWNER', 'RECRUITER', 'INTERVIEWER')),

    CONSTRAINT chk_ats_company_members_status
        CHECK (status IN ('ACTIVE', 'INVITED', 'REVOKED')),

    -- One membership row per (company, user) pair. A future re-invite after REVOKED updates
    -- this same row back to ACTIVE/INVITED rather than inserting a second one. A user may
    -- still hold separate rows across DIFFERENT companies.
    CONSTRAINT uk_ats_company_members_company_user
        UNIQUE (company_profile_id, user_id)
);

CREATE INDEX idx_ats_company_members_company_id
    ON ats_company_members (company_profile_id);

CREATE INDEX idx_ats_company_members_user_id
    ON ats_company_members (user_id);

-- Backfill: every company that exists today already has a working, previously-trusted owner —
-- give that owner an ACTIVE OWNER membership row so GET /company/ats/members/me and future
-- team-management features see a complete record for them. CompanyAccessResolver does NOT
-- depend on this row for owner resolution (see above) — this INSERT exists for completeness
-- and future-phase readiness, not correctness. ON CONFLICT DO NOTHING makes it safe even if a
-- membership row already exists for a given (company, user) pair.
INSERT INTO ats_company_members (id, company_profile_id, user_id, role, status, created_at, updated_at)
SELECT gen_random_uuid(), cp.id, cp.user_id, 'OWNER', 'ACTIVE', now(), now()
FROM company_profiles cp
ON CONFLICT (company_profile_id, user_id) DO NOTHING;
