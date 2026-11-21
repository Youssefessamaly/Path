-- The actual, versioned screening rubric attached to one internship — see V26's
-- screening_profiles for the reusable, non-versioned template these can optionally be seeded
-- from. Exactly one DRAFT and one ACTIVE rubric may exist per internship at a time (enforced
-- below with partial unique indexes, not just application code); activating a DRAFT archives
-- whatever was previously ACTIVE and never deletes it, so a past screening result can always
-- be traced back to the exact version of the rubric it was produced against.
CREATE TABLE internship_rubrics (
    id UUID PRIMARY KEY,

    internship_id UUID NOT NULL,
    -- Which template (if any) this rubric's requirements were originally copied from.
    -- Informational only — requirements are copied, never referenced live, so editing or
    -- deleting the source profile later never affects this rubric.
    screening_profile_id UUID,

    version INTEGER NOT NULL,
    status VARCHAR(10) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_internship_rubrics_internship
        FOREIGN KEY (internship_id)
        REFERENCES internships(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_internship_rubrics_screening_profile
        FOREIGN KEY (screening_profile_id)
        REFERENCES screening_profiles(id)
        ON DELETE SET NULL,

    CONSTRAINT uk_internship_rubrics_internship_version
        UNIQUE (internship_id, version),

    CONSTRAINT chk_internship_rubrics_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_internship_rubrics_internship_id
    ON internship_rubrics (internship_id);

CREATE UNIQUE INDEX uk_internship_rubrics_one_active_per_internship
    ON internship_rubrics (internship_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uk_internship_rubrics_one_draft_per_internship
    ON internship_rubrics (internship_id)
    WHERE status = 'DRAFT';

-- One configurable requirement within one internship rubric version. Mirrors
-- screening_profile_requirements exactly (see V26) — intentionally duplicated as a table
-- rather than shared, since a rubric version's requirements must stay immutable once the
-- rubric is ACTIVE/ARCHIVED, independent of whatever the source profile does afterward.
CREATE TABLE internship_rubric_requirements (
    id UUID PRIMARY KEY,

    internship_rubric_id UUID NOT NULL,

    requirement_type VARCHAR(10) NOT NULL,

    skill_id UUID,
    label VARCHAR(200),

    weight INTEGER NOT NULL,
    minimum_evidence_strength VARCHAR(10),
    recruiter_notes VARCHAR(1000),
    display_order INTEGER NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_internship_rubric_requirements_rubric
        FOREIGN KEY (internship_rubric_id)
        REFERENCES internship_rubrics(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_internship_rubric_requirements_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_internship_rubric_requirements_rubric_skill
        UNIQUE (internship_rubric_id, skill_id),

    CONSTRAINT chk_internship_rubric_requirements_type
        CHECK (requirement_type IN ('REQUIRED', 'PREFERRED', 'BONUS')),

    CONSTRAINT chk_internship_rubric_requirements_weight
        CHECK (weight BETWEEN 1 AND 100),

    CONSTRAINT chk_internship_rubric_requirements_strength
        CHECK (minimum_evidence_strength IS NULL
               OR minimum_evidence_strength IN ('STRONG', 'MODERATE', 'WEAK')),

    CONSTRAINT chk_internship_rubric_requirements_identity
        CHECK (skill_id IS NOT NULL OR label IS NOT NULL)
);

CREATE INDEX idx_internship_rubric_requirements_rubric_id
    ON internship_rubric_requirements (internship_rubric_id);

CREATE TABLE internship_rubric_requirement_evidence_sources (
    requirement_id UUID NOT NULL,
    evidence_source VARCHAR(30) NOT NULL,

    CONSTRAINT pk_internship_rubric_requirement_evidence_sources
        PRIMARY KEY (requirement_id, evidence_source),

    CONSTRAINT fk_internship_rubric_requirement_evidence_sources_requirement
        FOREIGN KEY (requirement_id)
        REFERENCES internship_rubric_requirements(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_internship_rubric_requirement_evidence_sources_value
        CHECK (evidence_source IN (
            'SKILLS_LIST', 'PROJECT', 'WORK_EXPERIENCE', 'INTERNSHIP',
            'COURSEWORK', 'CERTIFICATION', 'RESEARCH', 'UNKNOWN'
        ))
);
