-- Stage 5B: an explicit, per-application record of whether the applicant authorized
-- AI-assisted (third-party, Anthropic) analysis of THIS application's frozen CV snapshot.
--
-- Deliberately a field on internship_applications, not intern_profiles: consent is captured
-- once, at submission time, for the specific CV snapshot that application froze — matching
-- the same "frozen, never retroactively affected by later profile changes" design already
-- used for cv_storage_key/cv_file_name/etc. on this table. A later change to the intern's
-- live profile (or to some future global preference) must never alter what this specific,
-- already-submitted application recorded.
--
-- NOT NULL DEFAULT FALSE is the only safe default for every pre-existing application: never
-- assume consent for a row that predates this column. ai_analysis_consent_at is nullable and
-- only ever set when consent is TRUE, recording exactly when it was given.
ALTER TABLE internship_applications
    ADD COLUMN ai_analysis_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_analysis_consent_at TIMESTAMP WITH TIME ZONE NULL;
