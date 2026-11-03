-- An internship's own, independent recruitment pipeline — materialized (deep-copied) from a
-- company pipeline template, or from a hardcoded starter when none is chosen; see
-- com.internplatform.ats.pipeline.InternshipPipelineService. Purely additive.
--
-- Exactly one pipeline per internship (internship_id UNIQUE). source_template_id is a
-- best-effort reference only (ON DELETE SET NULL, since templates are archived rather than
-- deleted in practice, but never assumed present) — source_template_name is the real,
-- immutable record of what this pipeline was assigned from, snapshotted at assignment time so
-- it keeps meaning even if the template is later renamed or archived.
CREATE TABLE ats_internship_pipelines (
    id UUID PRIMARY KEY,

    internship_id UUID NOT NULL UNIQUE,
    company_profile_id UUID NOT NULL,

    source_template_id UUID,
    source_template_name VARCHAR(120),

    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_internship_pipelines_internship
        FOREIGN KEY (internship_id)
        REFERENCES internships(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_internship_pipelines_company
        FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ats_internship_pipelines_template
        FOREIGN KEY (source_template_id)
        REFERENCES ats_pipeline_templates(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_ats_internship_pipelines_company_id
    ON ats_internship_pipelines (company_profile_id);

-- This internship's own stages — deep-copied at assignment time (see ats_pipeline_templates
-- above), never a live reference to ats_template_stages. Deliberately mirrors that table's
-- shape (name, position) with no stage_kind column: ACCEPTED/REJECTED/WITHDRAWN are never
-- modeled as a stage here — they remain exclusively internship_applications.status values,
-- entirely untouched by this migration or any ATS table.
CREATE TABLE ats_stages (
    id UUID PRIMARY KEY,

    pipeline_id UUID NOT NULL,

    name VARCHAR(80) NOT NULL,
    position INTEGER NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ats_stages_pipeline
        FOREIGN KEY (pipeline_id)
        REFERENCES ats_internship_pipelines(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_ats_stages_position
        CHECK (position >= 0)
);

ALTER TABLE ats_stages
    ADD CONSTRAINT uk_ats_stages_pipeline_position
        UNIQUE (pipeline_id, position)
        DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_ats_stages_pipeline_position
    ON ats_stages (pipeline_id, position);
