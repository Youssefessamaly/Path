package com.internplatform.screening;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InternshipRubricRepository extends JpaRepository<InternshipRubric, UUID> {

    List<InternshipRubric> findAllByInternshipIdOrderByVersionDesc(UUID internshipId);

    Optional<InternshipRubric> findByIdAndInternshipId(UUID id, UUID internshipId);

    Optional<InternshipRubric> findByInternshipIdAndStatus(UUID internshipId, RubricStatus status);

    /** Highest version ever created for this internship, including any later-deleted draft —
     *  never reused, so a version number always identifies one unique rubric history point. */
    @Query("select max(r.version) from InternshipRubric r where r.internshipId = :internshipId")
    Optional<Integer> findMaxVersion(@Param("internshipId") UUID internshipId);
}
