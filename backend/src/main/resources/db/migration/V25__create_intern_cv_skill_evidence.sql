-- One row per (intern_profile, skill): the strongest supporting evidence found for that skill
-- in the intern's most recently uploaded CV. Deliberately separate from intern_cv_skills
-- (which stays the plain "is this skill present" fact used by existing matching/recommendation
-- code, untouched) — this table only adds an explanation layer on top, so nothing that already
-- reads intern_cv_skills/cvDetectedSkills needs to change.
CREATE TABLE intern_cv_skill_evidence (
    id UUID PRIMARY KEY,

    intern_profile_id UUID NOT NULL,
    skill_id UUID NOT NULL,

    evidence_source VARCHAR(30) NOT NULL,
    evidence_strength VARCHAR(10) NOT NULL,

    -- A short snippet of the actual CV line the skill was found in — never the full document.
    -- Null only in the (currently theoretical) case detection ran without a text reference.
    text_reference VARCHAR(300),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_intern_cv_skill_evidence_profile
        FOREIGN KEY (intern_profile_id)
        REFERENCES intern_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_intern_cv_skill_evidence_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE CASCADE,

    -- Exactly one evidence record per skill per profile — a replaced CV deletes and
    -- re-inserts, it never accumulates duplicate rows for the same skill.
    CONSTRAINT uk_intern_cv_skill_evidence_profile_skill
        UNIQUE (intern_profile_id, skill_id),

    CONSTRAINT chk_intern_cv_skill_evidence_source
        CHECK (evidence_source IN (
            'SKILLS_LIST', 'PROJECT', 'WORK_EXPERIENCE', 'INTERNSHIP',
            'COURSEWORK', 'CERTIFICATION', 'RESEARCH', 'UNKNOWN'
        )),

    CONSTRAINT chk_intern_cv_skill_evidence_strength
        CHECK (evidence_strength IN ('STRONG', 'MODERATE', 'WEAK'))
);

CREATE INDEX idx_intern_cv_skill_evidence_profile_id
    ON intern_cv_skill_evidence (intern_profile_id);

CREATE INDEX idx_intern_cv_skill_evidence_skill_id
    ON intern_cv_skill_evidence (skill_id);
