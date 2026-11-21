package com.internplatform.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningProfileRequirementRepository extends JpaRepository<ScreeningProfileRequirement, UUID> {

    List<ScreeningProfileRequirement> findAllByScreeningProfileIdOrderByDisplayOrderAscCreatedAtAsc(UUID screeningProfileId);

    Optional<ScreeningProfileRequirement> findByIdAndScreeningProfileId(UUID id, UUID screeningProfileId);

    boolean existsByScreeningProfileIdAndSkillId(UUID screeningProfileId, UUID skillId);
}
