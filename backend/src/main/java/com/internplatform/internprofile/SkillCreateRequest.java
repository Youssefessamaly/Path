package com.internplatform.internprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A user-typed skill name to resolve against the catalog (including aliases) or, failing
 *  that, create as a new, explicitly non-standard skill — see
 *  InternProfileService#resolveOrCreateSkill. */
public record SkillCreateRequest(

        @NotBlank(message = "Skill name is required")
        @Size(min = 2, max = 100, message = "Skill name must be between 2 and 100 characters")
        String name

) {
}
