package com.internplatform.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScreeningReportResultRepository extends JpaRepository<ScreeningReportResult, UUID> {

    List<ScreeningReportResult> findAllByScreeningReportId(UUID screeningReportId);
}
