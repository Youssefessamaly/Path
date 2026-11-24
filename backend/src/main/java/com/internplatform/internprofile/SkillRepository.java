package com.internplatform.internprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    /** All skills (standard and custom) — the detection dictionary for CV scanning, which must
     *  recognize a previously-added custom skill just as reliably as a catalog one. Not used
     *  for the general browse/suggestions list — see findAllByStandardTrueOrderByNameAsc. */
    List<Skill> findAllByOrderByNameAsc();

    /** The curated catalog only, for the general suggestions/browse list (GET /intern/skills)
     *  — a custom (is_standard=false) skill is deliberately excluded here so it's never
     *  presented as part of the standardized catalog, even though it remains fully usable
     *  everywhere else (see InternProfileService#getSkillCatalog/#resolveOrCreateSkill). */
    List<Skill> findAllByStandardTrueOrderByNameAsc();

    /** Exact (case-insensitive) name match only — used to resolve an AI-drafted rubric
     *  requirement's free-text label against the real catalog (see CompanyAiAssistService).
     *  Deliberately not a fuzzy/alias search: a precise match is trustworthy to surface as a
     *  resolved suggestion, a fuzzy one would not be. */
    Optional<Skill> findByNameIgnoreCase(String name);

    /** Exact (case-insensitive) match against a known alias (e.g. "JS" -> JavaScript) — see
     *  skill_aliases (V23/V24). Used by InternProfileService#resolveOrCreateSkill so a custom
     *  skill request for an existing alias resolves to the real canonical skill instead of
     *  creating a duplicate. uk_skill_aliases_alias_lower already guarantees at most one match. */
    @Query("SELECT s FROM Skill s JOIN s.aliases a WHERE LOWER(a) = LOWER(:alias)")
    Optional<Skill> findByAliasIgnoreCase(@Param("alias") String alias);
}
