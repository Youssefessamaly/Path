package com.internplatform.internprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findAllByOrderByNameAsc();

    /** Exact (case-insensitive) name match only — used to resolve an AI-drafted rubric
     *  requirement's free-text label against the real catalog (see CompanyAiAssistService).
     *  Deliberately not a fuzzy/alias search: a precise match is trustworthy to surface as a
     *  resolved suggestion, a fuzzy one would not be. */
    Optional<Skill> findByNameIgnoreCase(String name);
}
