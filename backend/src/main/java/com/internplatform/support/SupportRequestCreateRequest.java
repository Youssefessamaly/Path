package com.internplatform.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A message submitted through the public support page (legal-web/support.html) — never
 * authenticated, never tied to a real account. {@code website} is a honeypot: the real form
 * never shows or lets a person fill this field (visually hidden, excluded from tab order), so
 * a non-blank value means an automated submission — see SupportRequestService, which accepts
 * it with a normal-looking response but never actually sends it anywhere.
 */
public record SupportRequestCreateRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotNull(message = "Account type is required")
        SupportAccountType accountType,

        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must be at most 200 characters")
        String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message must be at most 5000 characters")
        String message,

        /** Honeypot — must always be blank from a real submission. Optional/omittable so a
         *  request that never sends the field at all (e.g. a future non-browser client) isn't
         *  penalized; only an actual non-blank value is treated as spam. */
        String website

) {
}
