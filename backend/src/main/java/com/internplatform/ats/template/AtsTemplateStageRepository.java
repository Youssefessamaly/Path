package com.internplatform.ats.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtsTemplateStageRepository extends JpaRepository<AtsTemplateStage, UUID> {

    List<AtsTemplateStage> findAllByTemplateIdOrderByPositionAsc(UUID templateId);

    Optional<AtsTemplateStage> findByIdAndTemplateId(UUID id, UUID templateId);

    long countByTemplateId(UUID templateId);
}
