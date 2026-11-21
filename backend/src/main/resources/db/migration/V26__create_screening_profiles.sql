-- Reusable, company-level ATS screening templates. Not tied to any internship — see V27's
-- internship_rubrics for the per-internship, versioned rubric that is actually used for
-- screening. A profile's requirements can be copied into a new rubric, but are never
-- referenced live by one, so editing a profile later never changes an already-configured
-- internship rubric.
CREATE TABLE screening_profiles (
    id UUID PRIMARY KEY,

    company_profile_id UUID NOT NULL,

    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),

    status VARCHAR(20) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_screening_profiles_company
        FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_screening_profiles_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_screening_profiles_company_profile_id
    ON screening_profiles (company_profile_id);

-- One configurable requirement within a screening profile template.
CREATE TABLE screening_profile_requirements (
    id UUID PRIMARY KEY,

    screening_profile_id UUID NOT NULL,

    requirement_type VARCHAR(10) NOT NULL,

    -- Either skill_id (a canonical catalog skill) or label (a free-text, non-catalog
    -- qualification, e.g. "Valid driving license") must be present — see
    -- chk_screening_profile_requirements_identity.
    skill_id UUID,
    label VARCHAR(200),

    weight INTEGER NOT NULL,
    minimum_evidence_strength VARCHAR(10),
    recruiter_notes VARCHAR(1000),
    display_order INTEGER NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_screening_profile_requirements_profile
        FOREIGN KEY (screening_profile_id)
        REFERENCES screening_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_screening_profile_requirements_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE CASCADE,

    -- A skill can only be configured once per profile. Multiple NULL skill_id (label-only)
    -- rows are unaffected — Postgres never treats two NULLs as duplicates under UNIQUE.
    CONSTRAINT uk_screening_profile_requirements_profile_skill
        UNIQUE (screening_profile_id, skill_id),

    CONSTRAINT chk_screening_profile_requirements_type
        CHECK (requirement_type IN ('REQUIRED', 'PREFERRED', 'BONUS')),

    CONSTRAINT chk_screening_profile_requirements_weight
        CHECK (weight BETWEEN 1 AND 100),

    CONSTRAINT chk_screening_profile_requirements_strength
        CHECK (minimum_evidence_strength IS NULL
               OR minimum_evidence_strength IN ('STRONG', 'MODERATE', 'WEAK')),

    CONSTRAINT chk_screening_profile_requirements_identity
        CHECK (skill_id IS NOT NULL OR label IS NOT NULL)
);

CREATE INDEX idx_screening_profile_requirements_profile_id
    ON screening_profile_requirements (screening_profile_id);

-- Which CV evidence sections are acceptable evidence for one requirement. An empty set (no
-- rows) means "no restriction — any source accepted," so this table starts empty for a
-- requirement by default rather than requiring every source to be listed explicitly.
CREATE TABLE screening_profile_requirement_evidence_sources (
    requirement_id UUID NOT NULL,
    evidence_source VARCHAR(30) NOT NULL,

    CONSTRAINT pk_screening_profile_requirement_evidence_sources
        PRIMARY KEY (requirement_id, evidence_source),

    CONSTRAINT fk_screening_profile_requirement_evidence_sources_requirement
        FOREIGN KEY (requirement_id)
        REFERENCES screening_profile_requirements(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_screening_profile_requirement_evidence_sources_value
        CHECK (evidence_source IN (
            'SKILLS_LIST', 'PROJECT', 'WORK_EXPERIENCE', 'INTERNSHIP',
            'COURSEWORK', 'CERTIFICATION', 'RESEARCH', 'UNKNOWN'
        ))
);
