package com.internplatform.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InternshipRubricRequirementRepository extends JpaRepository<InternshipRubricRequirement, UUID> {

    List<InternshipRubricRequirement> findAllByInternshipRubricIdOrderByDisplayOrderAscCreatedAtAsc(UUID internshipRubricId);

    Optional<InternshipRubricRequirement> findByIdAndInternshipRubricId(UUID id, UUID internshipRubricId);

    boolean existsByInternshipRubricIdAndSkillId(UUID internshipRubricId, UUID skillId);

    List<InternshipRubricRequirement> findAllByInternshipRubricId(UUID internshipRubricId);
}
