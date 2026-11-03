package com.internplatform.ats.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AtsStageEventRepository extends JpaRepository<AtsStageEvent, UUID> {

    List<AtsStageEvent> findAllByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}
