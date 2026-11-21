package com.internplatform.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningReportRepository extends JpaRepository<ScreeningReport, UUID> {

    Optional<ScreeningReport> findByIdAndInternshipId(UUID id, UUID internshipId);

    Optional<ScreeningReport> findFirstByApplicationIdOrderByEvaluatedAtDesc(UUID applicationId);

    /** Batched lookup for the summary list endpoint — one query for a whole page of
     *  applications' latest reports, never one query per application. */
    List<ScreeningReport> findAllByApplicationIdInOrderByEvaluatedAtDesc(List<UUID> applicationIds);
}
