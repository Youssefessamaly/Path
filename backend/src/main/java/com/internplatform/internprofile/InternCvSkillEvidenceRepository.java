package com.internplatform.internprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InternCvSkillEvidenceRepository extends JpaRepository<InternCvSkillEvidence, UUID> {

    List<InternCvSkillEvidence> findAllByInternProfileId(UUID internProfileId);

    /** Used on every CV (re)upload so evidence is fully replaced, never accumulated. */
    void deleteAllByInternProfileId(UUID internProfileId);
}
