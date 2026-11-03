package com.internplatform.ats.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AtsInternshipPipelineRepository extends JpaRepository<AtsInternshipPipeline, UUID> {

    Optional<AtsInternshipPipeline> findByInternshipId(UUID internshipId);

    boolean existsByInternshipId(UUID internshipId);
}
