-- Company-owned, reusable recruitment pipeline templates — the ATS pipeline foundation; see
-- com.internplatform.ats.template.PipelineTemplateService. Purely additive: no existing table,
-- column, row, or constraint is altered.
--
-- A template's stages (ats_template_stages) are deep-copied into a separate, independent set
-- of rows (ats_stages, see V19) whenever a template is assigned to an internship — never
-- referenced live. Editing a template after that point can therefore never alter an internship
-- pipeline that was already materialized from it, by construction (no FK path between the two
-- stage tables).
CREATE TABLE ats_pipeline_templates (
    id UUID PRIMARY KEY,

    company_profile_id UUID NOT NULL,

    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000),

    -- The template used when an internship's pipeline is assigned with no explicit
    -- templateId. At most one non-archived default per company (see the partial unique index
    -- below). Not required to exist at all — see InternshipPipelineService's hardcoded starter
    -- fallback (Applied -> Screening -> Interview -> Offer) for a company with none.
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    -- Archived (not deleted) templates stop being assignable to new internships and are
    -- excluded from the default listing, but remain readable so past assignments that
    -- snapshot this template's name (ats_internship_pipelines.source_template_name) still
    -- make sense in history.
    archived_at TIMESTAMP WITH TIME ZONE,

    -- Optimistic lock for concurrent rename/archive/default-toggle requests.
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_pipeline_templates_company
        FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ats_pipeline_templates_company_id
    ON ats_pipeline_templates (company_profile_id);

CREATE UNIQUE INDEX uk_ats_pipeline_templates_one_default_per_company
    ON ats_pipeline_templates (company_profile_id)
    WHERE is_default AND archived_at IS NULL;

CREATE TABLE ats_template_stages (
    id UUID PRIMARY KEY,

    template_id UUID NOT NULL,

    name VARCHAR(80) NOT NULL,
    position INTEGER NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_template_stages_template
        FOREIGN KEY (template_id)
        REFERENCES ats_pipeline_templates(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_ats_template_stages_position
        CHECK (position >= 0)
);

-- DEFERRABLE so a whole-template reorder can rewrite every stage's position within one
-- transaction without tripping uniqueness mid-statement (Postgres checks DEFERRABLE
-- INITIALLY DEFERRED constraints at COMMIT, after every row in the batch has its final value).
ALTER TABLE ats_template_stages
    ADD CONSTRAINT uk_ats_template_stages_template_position
        UNIQUE (template_id, position)
        DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_ats_template_stages_template_position
    ON ats_template_stages (template_id, position);
