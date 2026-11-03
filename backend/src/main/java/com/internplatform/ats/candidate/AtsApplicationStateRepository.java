package com.internplatform.ats.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AtsApplicationStateRepository extends JpaRepository<AtsApplicationState, UUID> {

    /**
     * Idempotent insert of the first-stage tracking row — {@code ON CONFLICT DO NOTHING} rather
     * than a plain INSERT relying on catching a primary-key violation. A concurrent caller
     * racing for the same application_id simply gets 0 affected rows back, not an exception:
     * Postgres aborts an entire transaction on any failed statement, so letting a race surface
     * as a constraint-violation exception would poison whatever transaction this call is
     * running in (the caller's own, since this joins it rather than opening a separate one).
     * The caller uses the returned row count to decide whether it — not a racing competitor —
     * is the one that actually created the row, and so whether it should also record the
     * corresponding ENTERED stage event.
     */
    @Modifying
    @Query(value = """
            INSERT INTO ats_application_states
                (application_id, internship_id, company_profile_id, pipeline_id,
                 current_stage_id, entered_current_stage_at, created_at, updated_at)
            VALUES
                (:applicationId, :internshipId, :companyProfileId, :pipelineId,
                 :currentStageId, :enteredAt, :enteredAt, :enteredAt)
            ON CONFLICT (application_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("applicationId") UUID applicationId,
            @Param("internshipId") UUID internshipId,
            @Param("companyProfileId") UUID companyProfileId,
            @Param("pipelineId") UUID pipelineId,
            @Param("currentStageId") UUID currentStageId,
            @Param("enteredAt") OffsetDateTime enteredAt);
}
