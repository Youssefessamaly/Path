-- Alternate names (abbreviations, punctuation variants) that resolve to an existing canonical
-- skill, e.g. "JS" -> JavaScript, "Postgres" -> PostgreSQL. An alias always means "same skill",
-- never a new skill in its own right — detection still only ever adds the canonical Skill.
CREATE TABLE skill_aliases (
    skill_id UUID NOT NULL,
    alias VARCHAR(100) NOT NULL,

    CONSTRAINT pk_skill_aliases
        PRIMARY KEY (skill_id, alias),

    CONSTRAINT fk_skill_aliases_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE CASCADE
);

-- An alias must resolve to exactly one skill, case-insensitively, regardless of which skill it
-- is attached to — otherwise "JS" matching both "JavaScript" and some other skill would be
-- ambiguous at detection time.
CREATE UNIQUE INDEX uk_skill_aliases_alias_lower
    ON skill_aliases (LOWER(alias));

CREATE INDEX idx_skill_aliases_skill_id
    ON skill_aliases (skill_id);
