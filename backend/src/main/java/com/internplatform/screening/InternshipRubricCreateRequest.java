package com.internplatform.screening;

import java.util.UUID;

/** Creates a new DRAFT rubric for an internship. When screeningProfileId is present, the new
 *  draft starts with a COPY of that profile's requirements (see InternshipRubricService) —
 *  editing the draft afterward never affects the source profile, and vice versa. */
public record InternshipRubricCreateRequest(
        UUID screeningProfileId
) {
}
