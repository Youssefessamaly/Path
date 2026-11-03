package com.internplatform.ats.template;

import jakarta.validation.constraints.Size;

/** Partial update — matching the codebase's existing PATCH convention (e.g.
 *  CompanyProfileRequest, ApplicationQuestionUpdateRequest): an omitted (null) field is left
 *  unchanged. There is no way to clear name back to blank (it is always required); description
 *  can be cleared by sending an empty string. */
public record PipelineTemplateUpdateRequest(

        @Size(max = 120, message = "Template name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        /** null = omitted (unchanged); true/false = explicit set. Setting true clears any
         *  other default template this company currently has (see PipelineTemplateService). */
        Boolean isDefault

) {
}
