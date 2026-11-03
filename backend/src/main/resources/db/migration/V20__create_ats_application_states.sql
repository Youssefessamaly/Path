-- Connects an internship application to its recruitment pipeline — the first operational ATS
-- feature (see com.internplatform.ats.candidate.AtsApplicationIntakeService). Purely additive:
-- no existing table, column, row, or constraint is altered.
--
-- application_id is the PRIMARY KEY (not a separate generated id) — this is the actual
-- duplicate-prevention mechanism: at most one tracking row can ever exist per application, by
-- construction, not by convention. A concurrent or retried intake attempt simply hits a
-- primary-key conflict, which the service treats as "already tracked" and no-ops.
--
-- Deliberately carries NO reference to ACCEPTED/REJECTED/WITHDRAWN — internship_applications
-- .status remains the sole, untouched source of truth for the public outcome. A row existing
-- here only ever means "this application is being tracked through a pipeline's ACTIVE
-- stages"; it says nothing about, and never overrides, the application's own status.
CREATE TABLE ats_application_states (
    application_id UUID PRIMARY KEY,

    internship_id UUID NOT NULL,
    company_profile_id UUID NOT NULL,
    pipeline_id UUID NOT NULL,
    current_stage_id UUID NOT NULL,

    entered_current_stage_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Optimistic lock — protects a future stage-move endpoint (not implemented in this phase)
    -- from two concurrent moves silently clobbering each other.
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_application_states_application
        FOREIGN KEY (application_id)
        REFERENCES internship_applications(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_application_states_internship
        FOREIGN KEY (internship_id)
        REFERENCES internships(id)
        ON DELETE CASCADE,

    -- Denormalized so every company-isolation check is a single-column predicate, matching the
    -- existing ats_internship_pipelines/ats_company_members convention.
    CONSTRAINT fk_ats_application_states_company
        FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_application_states_pipeline
        FOREIGN KEY (pipeline_id)
        REFERENCES ats_internship_pipelines(id)
        ON DELETE CASCADE,

    -- RESTRICT: a stage currently occupied by a tracked application can never be deleted out
    -- from under it. (No stage-deletion safety check exists for THIS reason yet in
    -- InternshipPipelineService — that enforcement is deferred to the candidate-movement
    -- phase; this constraint is the backstop that makes it impossible to corrupt data even
    -- before that check exists.)
    CONSTRAINT fk_ats_application_states_stage
        FOREIGN KEY (current_stage_id)
        REFERENCES ats_stages(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_ats_application_states_internship_id
    ON ats_application_states (internship_id);

CREATE INDEX idx_ats_application_states_company_id
    ON ats_application_states (company_profile_id);

CREATE INDEX idx_ats_application_states_stage_id
    ON ats_application_states (current_stage_id);

-- Append-only stage transition history. ENTERED is this phase's only event kind (automatic
-- intake on submission, or lazy backfill of a pre-existing APPLIED application — see
-- AtsApplicationIntakeService); MOVED is reserved for the future candidate-movement phase and
-- is not produced by any code in this phase.
--
-- from/to stage NAME is snapshotted alongside the (nullable-on-from) id so history stays
-- readable even after a stage is later renamed — the same snapshot-for-history-integrity
-- pattern internship_application_answers already established for question text.
CREATE TABLE ats_stage_events (
    id UUID PRIMARY KEY,

    application_id UUID NOT NULL,
    internship_id UUID NOT NULL,
    company_profile_id UUID NOT NULL,

    from_stage_id UUID,
    to_stage_id UUID NOT NULL,
    from_stage_name VARCHAR(80),
    to_stage_name VARCHAR(80) NOT NULL,

    event_kind VARCHAR(20) NOT NULL,

    -- The company member who performed the transition — always NULL in this phase (every
    -- event is system-initiated: automatic intake or lazy backfill, never a human action).
    -- Reserved for the future candidate-movement phase, where a recruiter's own move sets this.
    actor_member_id UUID,

    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_stage_events_application
        FOREIGN KEY (application_id)
        REFERENCES internship_applications(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_stage_events_internship
        FOREIGN KEY (internship_id)
        REFERENCES internships(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_stage_events_company
        FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles(id)
        ON DELETE CASCADE,

    -- RESTRICT, matching ats_application_states above: a stage with history pointing at it can
    -- never be deleted.
    CONSTRAINT fk_ats_stage_events_to_stage
        FOREIGN KEY (to_stage_id)
        REFERENCES ats_stages(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ats_stage_events_from_stage
        FOREIGN KEY (from_stage_id)
        REFERENCES ats_stages(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ats_stage_events_actor
        FOREIGN KEY (actor_member_id)
        REFERENCES ats_company_members(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ats_stage_events_kind
        CHECK (event_kind IN ('ENTERED', 'MOVED'))
);

CREATE INDEX idx_ats_stage_events_application_id
    ON ats_stage_events (application_id, occurred_at);

CREATE INDEX idx_ats_stage_events_internship_id
    ON ats_stage_events (internship_id, occurred_at);
