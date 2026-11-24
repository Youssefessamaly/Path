-- Distinguishes PATH's curated, reviewed skill catalog from a skill an intern or company
-- added themselves via "Add custom skill" when nothing in the catalog (by name or alias)
-- matched what they typed. A custom skill is a fully real, usable Skill row the moment it's
-- created — selectable, CV-detectable, eligible as a rubric requirement, exactly like any
-- other skill (see InternshipService.resolveSkills, CvSkillExtractionService,
-- RequirementCreateRequest — none of them filter by this flag). It exists purely so a
-- user-submitted entry is never mistaken for part of the standardized catalog without a
-- separate, explicit decision to promote it; there is no such promotion path yet.
--
-- Every existing skill (the original V4 seed plus V24's expansion) is unambiguously part of
-- the curated catalog, so the default of TRUE is correct for all of them with no backfill
-- needed. The default stays in place going forward too: any future admin-curated catalog
-- addition (inserted the same way V24 did) is standard unless a row explicitly says otherwise.
ALTER TABLE skills
    ADD COLUMN is_standard BOOLEAN NOT NULL DEFAULT TRUE;

-- uk_skills_name (V4) is case-sensitive, so "python" and "Python" could otherwise coexist as
-- two rows. InternProfileService#resolveOrCreateSkill already checks case-insensitively before
-- creating a custom skill, but two concurrent requests for the same (or differently-cased) new
-- name could both pass that check before either commits. This index is what actually makes
-- that race detectable — the service's catch of the resulting constraint violation re-resolves
-- to whichever row won, rather than ever allowing both to exist.
CREATE UNIQUE INDEX uk_skills_name_lower
    ON skills (LOWER(name));
