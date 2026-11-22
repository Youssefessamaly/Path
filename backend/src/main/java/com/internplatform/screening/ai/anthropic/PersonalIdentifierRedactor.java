package com.internplatform.screening.ai.anthropic;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Best-effort minimization of personal identifiers in CV text before it is sent to an
 * external AI provider (never applied to the text used for deterministic screening or for
 * EvidenceClaimValidator, which always checks claims against the ORIGINAL, unredacted
 * authorized CV text — see AnthropicEvidenceAnalysisService).
 *
 * Scope and limits, by design:
 * - Targets structured, reliably-pattern-matchable contact identifiers only: email addresses,
 *   Egyptian mobile numbers (the same pattern already used for company/intern profile
 *   validation elsewhere in this codebase), and URLs (LinkedIn/portfolio links commonly
 *   placed in a CV header). Deliberately does NOT attempt a generic "any digit sequence looks
 *   like a phone number" heuristic — that would false-positive on ordinary CV content such as
 *   date ranges ("2020-2024") or GPAs, corrupting legitimate body text. A narrower, precise
 *   pattern is preferred over a broader, unreliable one.
 * - Deliberately does NOT attempt to detect or redact the candidate's name — reliable name
 *   detection in free-form bilingual (Arabic/English) text without a trained NER model would
 *   itself require sending text to a model, defeating the purpose, and a wrong heuristic
 *   could corrupt real body content. This is documented here as a known, accepted limitation,
 *   not an oversight.
 * - Only replaces exact regex matches with a short bracketed token; it never rewrites,
 *   paraphrases, or removes any other text, so legitimate skill/project/work-experience
 *   excerpts the model quotes back remain byte-identical substrings of the original CV text
 *   outside of contact-info spans, and pass EvidenceClaimValidator's verbatim-excerpt check
 *   when validated against the original text.
 */
@Component
public class PersonalIdentifierRedactor {

    private static final Pattern EMAIL = Pattern.compile(
            "[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}");

    private static final Pattern EGYPTIAN_PHONE = Pattern.compile(
            "(?:\\+20|0)1[0125]\\d{8}");

    private static final Pattern URL = Pattern.compile(
            "(?:https?://|www\\.)[\\w./?#%=&-]+", Pattern.CASE_INSENSITIVE);

    public String redact(String cvText) {
        if (cvText == null || cvText.isBlank()) {
            return cvText;
        }
        String redacted = EMAIL.matcher(cvText).replaceAll("[REDACTED_EMAIL]");
        redacted = URL.matcher(redacted).replaceAll("[REDACTED_URL]");
        redacted = EGYPTIAN_PHONE.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        return redacted;
    }
}
