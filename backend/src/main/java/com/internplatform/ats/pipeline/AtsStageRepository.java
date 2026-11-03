package com.internplatform.ats.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtsStageRepository extends JpaRepository<AtsStage, UUID> {

    List<AtsStage> findAllByPipelineIdOrderByPositionAsc(UUID pipelineId);

    Optional<AtsStage> findByIdAndPipelineId(UUID id, UUID pipelineId);

    long countByPipelineId(UUID pipelineId);
}
