package com.internplatform.screening;

import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Validation rules shared by ScreeningProfileService and InternshipRubricService, so a
 * requirement means exactly the same thing — and is validated exactly the same way —
 * regardless of which one it belongs to (see RequirementCreateRequest).
 */
final class RequirementValidationSupport {

    private RequirementValidationSupport() {
    }

    /** Exactly one of skillId/label identifies a requirement — enforced here and by the DB
     *  CHECK constraint as a second line of defense. */
    static void requireSkillOrLabel(UUID skillId, String label) {
        if (skillId == null && (label == null || label.isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Either skillId or label is required"
            );
        }
    }

    static Skill resolveSkillOrThrow(SkillRepository skillRepository, UUID skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown skill id: " + skillId
                ));
    }

    static void requireNonBlankLabelIfSkillless(UUID existingSkillId, String newLabel) {
        if (existingSkillId == null && newLabel != null && newLabel.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Label cannot be blank for a requirement with no skill"
            );
        }
    }
}
