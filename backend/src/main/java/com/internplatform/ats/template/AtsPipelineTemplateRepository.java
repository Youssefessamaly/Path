package com.internplatform.ats.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtsPipelineTemplateRepository extends JpaRepository<AtsPipelineTemplate, UUID> {

    List<AtsPipelineTemplate> findAllByCompanyProfileIdOrderByCreatedAtAsc(UUID companyProfileId);

    /** Ownership-scoped lookup: another company's template id simply doesn't match, collapsing
     *  to 404 — same not-found-not-forbidden pattern used throughout the codebase. */
    Optional<AtsPipelineTemplate> findByIdAndCompanyProfileId(UUID id, UUID companyProfileId);

    /** The template a new internship pipeline falls back to when assigned with no explicit
     *  templateId. At most one can exist per company (see the DB partial unique index). */
    Optional<AtsPipelineTemplate> findByCompanyProfileIdAndDefaultTemplateTrueAndArchivedAtIsNull(
            UUID companyProfileId);
}
