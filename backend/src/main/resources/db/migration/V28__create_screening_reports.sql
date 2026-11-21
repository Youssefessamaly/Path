-- One deterministic, explainable screening evaluation of one application against one frozen
-- InternshipRubric version. Reports are append-only: screening the same application again
-- (even against the same rubric) always inserts a NEW row rather than overwriting an earlier
-- one, so a report produced before a rubric changed stays exactly as it was computed —
-- see InternshipRubric's own immutability guarantees (V27).
CREATE TABLE screening_reports (
    id UUID PRIMARY KEY,

    internship_id UUID NOT NULL,
    application_id UUID NOT NULL,
    internship_rubric_id UUID NOT NULL,
    -- Denormalized from internship_rubrics.version at evaluation time, so a report displays
    -- its rubric version without a join even if the rubric row were ever removed.
    rubric_version INTEGER NOT NULL,

    -- The application's own cv_storage_key at evaluation time — applications never change
    -- their CV after submission (see InternshipApplication), so this is purely informational,
    -- not a second source of truth.
    cv_snapshot_storage_key VARCHAR(255) NOT NULL,
    -- True when the CV snapshot's text could not be extracted, or extraction yielded no
    -- usable text (e.g. a scanned, image-only PDF) — every CV-evidenced requirement result in
    -- this report is then NEEDS_REVIEW rather than a potentially-false NOT_EVIDENCED.
    cv_text_unavailable BOOLEAN NOT NULL,

    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Identifies which version of the matching algorithm produced this report — see
    -- ScreeningService.ALGORITHM_VERSION. Bumped whenever the matching rules change, so a
    -- historical report is never misread as having used rules it didn't.
    algorithm_version VARCHAR(50) NOT NULL,

    total_required_count INTEGER NOT NULL,
    matched_required_count INTEGER NOT NULL,
    total_preferred_count INTEGER NOT NULL,
    matched_preferred_count INTEGER NOT NULL,
    total_bonus_count INTEGER NOT NULL,
    matched_bonus_count INTEGER NOT NULL,

    -- Transparent weighted coverage across every requirement (see ScreeningService) — a
    -- deterministic ratio, deliberately never framed or named as a probability/likelihood of
    -- candidate success.
    weighted_coverage_percentage INTEGER NOT NULL,

    -- True when cv_text_unavailable, or any requirement result below is NEEDS_REVIEW (e.g. a
    -- free-text requirement with no canonical skill) — signals a human must look regardless of
    -- the coverage numbers.
    needs_review BOOLEAN NOT NULL,

    CONSTRAINT fk_screening_reports_internship
        FOREIGN KEY (internship_id) REFERENCES internships(id) ON DELETE CASCADE,
    CONSTRAINT fk_screening_reports_application
        FOREIGN KEY (application_id) REFERENCES internship_applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_screening_reports_rubric
        FOREIGN KEY (internship_rubric_id) REFERENCES internship_rubrics(id) ON DELETE CASCADE,

    CONSTRAINT chk_screening_reports_coverage
        CHECK (weighted_coverage_percentage BETWEEN 0 AND 100)
);

CREATE INDEX idx_screening_reports_application_id
    ON screening_reports (application_id);

CREATE INDEX idx_screening_reports_internship_id
    ON screening_reports (internship_id);

-- One requirement's result within one report.
CREATE TABLE screening_report_results (
    id UUID PRIMARY KEY,

    screening_report_id UUID NOT NULL,
    -- Safe to reference directly (never copied) because a rubric's requirements are
    -- immutable once the rubric is ACTIVE/ARCHIVED — see InternshipRubricService.
    internship_rubric_requirement_id UUID NOT NULL,

    result VARCHAR(25) NOT NULL,
    evidence_source VARCHAR(30),
    evidence_strength VARCHAR(10),
    -- The specific CV line the evidence came from, already length-capped by
    -- CvSkillExtractionService — never the full CV text. Null when there is no CV evidence
    -- (manual-only, not evidenced, or a free-text requirement).
    text_reference VARCHAR(300),
    reasoning VARCHAR(300) NOT NULL,

    CONSTRAINT fk_screening_report_results_report
        FOREIGN KEY (screening_report_id) REFERENCES screening_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_screening_report_results_requirement
        FOREIGN KEY (internship_rubric_requirement_id) REFERENCES internship_rubric_requirements(id) ON DELETE CASCADE,

    CONSTRAINT chk_screening_report_results_result
        CHECK (result IN ('MATCHED', 'NOT_EVIDENCED', 'INSUFFICIENT_EVIDENCE', 'NEEDS_REVIEW')),

    CONSTRAINT chk_screening_report_results_source
        CHECK (evidence_source IS NULL OR evidence_source IN (
            'SKILLS_LIST', 'PROJECT', 'WORK_EXPERIENCE', 'INTERNSHIP',
            'COURSEWORK', 'CERTIFICATION', 'RESEARCH', 'UNKNOWN'
        )),

    CONSTRAINT chk_screening_report_results_strength
        CHECK (evidence_strength IS NULL OR evidence_strength IN ('STRONG', 'MODERATE', 'WEAK'))
);

CREATE INDEX idx_screening_report_results_report_id
    ON screening_report_results (screening_report_id);
