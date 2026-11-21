package com.internplatform.internprofile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts text from uploaded CV PDFs and detects which Pathora catalog skills appear in
 * that text — entirely locally (Apache PDFBox), with no cloud APIs, OCR, or AI/embeddings.
 * Detection is a deterministic, word-boundary-aware substring match against the existing
 * Skill catalog; it never introduces a second, independent skill list.
 *
 * Never log the text extracted from a CV, or any per-line snippet derived from it — it is
 * personal candidate content. It may only be persisted (see InternCvSkillEvidence) and
 * returned through normal authenticated API responses, the same visibility CV-derived skills
 * already have.
 */
@Service
public class CvSkillExtractionService {

    private static final int MAX_TEXT_REFERENCE_LENGTH = 200;

    /**
     * Section header keywords (English + Arabic) that switch which {@link EvidenceSource} a
     * CV's following lines are attributed to. A line counts as a header only if it IS (after
     * trimming and stripping trailing punctuation), not merely contains, one of these phrases
     * — so an ordinary sentence like "I have experience in Java" is never mistaken for an
     * "Experience" section header.
     */
    private static final List<SectionHeaderRule> SECTION_HEADER_RULES = List.of(
            new SectionHeaderRule(EvidenceSource.WORK_EXPERIENCE,
                    "experience", "work experience", "professional experience",
                    "employment history", "work history",
                    "الخبرة", "الخبرة العملية", "الخبرات", "الخبرات العملية"),
            new SectionHeaderRule(EvidenceSource.INTERNSHIP,
                    "internship", "internships", "training", "field training", "practical training",
                    "التدريب", "التدريب العملي", "فترة التدريب"),
            new SectionHeaderRule(EvidenceSource.PROJECT,
                    "projects", "personal projects", "academic projects", "key projects", "selected projects",
                    "المشاريع", "مشاريع"),
            new SectionHeaderRule(EvidenceSource.RESEARCH,
                    "research", "research experience", "publications",
                    "الأبحاث", "البحث العلمي"),
            new SectionHeaderRule(EvidenceSource.CERTIFICATION,
                    "certifications", "certificates", "licenses", "licenses and certifications",
                    "الشهادات"),
            new SectionHeaderRule(EvidenceSource.COURSEWORK,
                    "coursework", "relevant coursework", "relevant courses", "courses",
                    "المقررات", "المقررات الدراسية"),
            new SectionHeaderRule(EvidenceSource.SKILLS_LIST,
                    "skills", "technical skills", "core skills", "key skills",
                    "core competencies", "competencies",
                    "المهارات", "المهارات التقنية")
    );

    /**
     * Which section an ambiguous skill's evidence is attributed to when it appears under more
     * than one — strongest, most-demonstrated evidence wins. UNKNOWN (text outside any
     * recognized section, or a CV with no recognizable structure at all) is deliberately last:
     * it is always checked, so detection never misses a skill, but never outranks a real
     * section.
     */
    private static final List<EvidenceSource> SOURCE_PRIORITY = List.of(
            EvidenceSource.WORK_EXPERIENCE,
            EvidenceSource.INTERNSHIP,
            EvidenceSource.PROJECT,
            EvidenceSource.RESEARCH,
            EvidenceSource.CERTIFICATION,
            EvidenceSource.COURSEWORK,
            EvidenceSource.SKILLS_LIST,
            EvidenceSource.UNKNOWN
    );

    /**
     * Loads the PDF and strips its text. A PDF that fails to parse at all (corrupt,
     * truncated, password-protected, or otherwise not a well-formed PDF document) is
     * rejected with a clear 400 rather than silently treated as "no skills found" — this
     * mirrors the existing signature-check failure mode in InternProfileService, so an
     * upload either fully succeeds (CV stored, skills detected) or fully fails (nothing
     * changed). A structurally valid PDF that simply has no extractable text (e.g. a
     * scanned image with no text layer — OCR is explicitly out of scope) is NOT a failure:
     * it legitimately yields empty text, which in turn detects zero skills.
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException exception) {
            // PDFBox can throw various runtime exceptions (e.g. for malformed cross-reference
            // tables) in addition to IOException depending on how the file is broken —
            // all of them mean the same thing here: this isn't a text-readable PDF.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CV must be a valid, text-readable PDF file"
            );
        }
    }

    /**
     * Matches catalog skill names against already-extracted text, ignoring where in the
     * document each match came from. Pure and Spring/PDF-independent on purpose, so the tricky
     * matching rules (case-insensitivity, whitespace normalization, Java-vs-JavaScript style
     * boundary safety, multi-word names, aliases) can be unit-tested directly against plain
     * strings without needing real PDF files.
     *
     * A thin wrapper over {@link #detectSkillEvidence} — kept as its own method (rather than
     * asking every caller to discard evidence detail) because most callers only ever needed
     * "is this skill present," and this is exactly the set of skills that answers that for.
     */
    public Set<Skill> detectSkills(String extractedText, Collection<Skill> catalog) {
        return detectSkillEvidence(extractedText, catalog).stream()
                .map(SkillEvidence::skill)
                .collect(Collectors.toSet());
    }

    /**
     * Detects catalog skills the same way {@link #detectSkills} does, but additionally
     * classifies WHERE each one was found — a CV section such as PROJECT or WORK_EXPERIENCE —
     * and how strongly that location supports actual practical use (see EvidenceStrength).
     * When a skill appears under more than one section, the strongest (per SOURCE_PRIORITY)
     * wins; a skill that only ever appears in unstructured text, or in a CV with no
     * recognizable section headers at all, is reported as UNKNOWN/WEAK rather than guessed —
     * it is still detected, just without a confident evidence source.
     */
    public List<SkillEvidence> detectSkillEvidence(String extractedText, Collection<Skill> catalog) {
        Sections sections = splitIntoSections(extractedText);
        List<SkillEvidence> evidence = new ArrayList<>();

        for (Skill skill : catalog) {
            for (EvidenceSource source : SOURCE_PRIORITY) {
                String sectionText = sections.textFor(source);
                if (sectionText.isEmpty() || !matchesNameOrAlias(skill, sectionText)) {
                    continue;
                }

                evidence.add(new SkillEvidence(
                        skill,
                        source,
                        strengthFor(source),
                        sections.textReferenceFor(source, skill, this::matchesNameOrAlias)
                ));
                break;
            }
        }

        return evidence;
    }

    /**
     * For an excerpt of text claimed to appear in a CV (e.g. an AI-suggested quote — see
     * EvidenceClaimValidator in com.internplatform.screening.ai), determines whether that
     * excerpt is actually present in the given already-extracted CV text and, if so, the
     * strongest CV section it appears under. Returns empty when the excerpt cannot be found
     * anywhere in the text at all — callers must treat that as "not grounded," never guess a
     * strength for ungrounded text. This reuses the exact same section/strength rules
     * CV-derived evidence already follows (see strengthFor), so a model-suggested claim is
     * held to no more than what the source text itself would deterministically support.
     */
    public Optional<EvidenceStrength> maximumSupportableStrength(String extractedText, String excerpt) {
        if (excerpt == null || excerpt.isBlank()) {
            return Optional.empty();
        }

        // Boundary-aware, not a naive substring check, for the same reason buildSkillPattern
        // is: a short excerpt like "Java" must not be treated as merely "contained in" an
        // unrelated longer word. (It is still correctly found inside "Java-based" — a hyphen
        // is a genuine word boundary by this class's own established rules, same as any other
        // skill/alias match — this guards against accidental substrings, not hyphenation.)
        Pattern excerptPattern = Pattern.compile(
                "(?<![\\w+#])" + Pattern.quote(normalize(excerpt)) + "(?![\\w+#])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Sections sections = splitIntoSections(extractedText);

        // SOURCE_PRIORITY is already strength-descending, so the first containing section
        // found is the strongest one — no separate ranking step needed.
        for (EvidenceSource source : SOURCE_PRIORITY) {
            String sectionText = sections.textFor(source);
            if (!sectionText.isEmpty() && excerptPattern.matcher(sectionText).find()) {
                return Optional.of(strengthFor(source));
            }
        }
        return Optional.empty();
    }

    /**
     * Deterministic, documented mapping — never inferred by AI/ML. Work experience,
     * internships, projects, and research all demonstrate the skill was actually used;
     * certifications/coursework show formal exposure without demonstrated hands-on use; a
     * bare skills-list mention or unstructured text shows neither.
     */
    private EvidenceStrength strengthFor(EvidenceSource source) {
        return switch (source) {
            case WORK_EXPERIENCE, INTERNSHIP, PROJECT, RESEARCH -> EvidenceStrength.STRONG;
            case CERTIFICATION, COURSEWORK -> EvidenceStrength.MODERATE;
            case SKILLS_LIST, UNKNOWN -> EvidenceStrength.WEAK;
        };
    }

    /**
     * Splits extracted CV text into per-section line groups by scanning for recognized
     * section-header lines (see SECTION_HEADER_RULES), attributing every line to whichever
     * section header most recently preceded it. Lines before the first recognized header
     * (or in a CV with no recognized headers at all) are UNKNOWN.
     */
    private Sections splitIntoSections(String extractedText) {
        Map<EvidenceSource, List<String>> linesBySection = new EnumMap<>(EvidenceSource.class);
        for (EvidenceSource source : EvidenceSource.values()) {
            linesBySection.put(source, new ArrayList<>());
        }

        EvidenceSource current = EvidenceSource.UNKNOWN;
        for (String rawLine : (extractedText == null ? "" : extractedText).split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            EvidenceSource header = matchHeader(line);
            if (header != null) {
                current = header;
                continue;
            }

            linesBySection.get(current).add(line);
        }

        return new Sections(linesBySection);
    }

    /** A line without trailing punctuation that exactly equals one of a section's keywords. */
    private EvidenceSource matchHeader(String line) {
        String stripped = line.replaceAll("[:\\-–—\\s]+$", "").trim();
        for (SectionHeaderRule rule : SECTION_HEADER_RULES) {
            if (rule.matches(stripped)) {
                return rule.source();
            }
        }
        return null;
    }

    /**
     * A skill counts as present if its canonical name matches, OR any of its aliases does
     * (e.g. "JS" for JavaScript, "Postgres" for PostgreSQL — see V23/V24). An alias never
     * creates a second skill: it always resolves back to the same catalog Skill instance.
     */
    private boolean matchesNameOrAlias(Skill skill, String normalizedText) {
        if (buildSkillPattern(skill.getName()).matcher(normalizedText).find()) {
            return true;
        }
        for (String alias : skill.getAliases()) {
            if (buildSkillPattern(alias).matcher(normalizedText).find()) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Builds a case-insensitive, boundary-anchored pattern for a (possibly multi-word) skill
     * name or alias. `\s+` between words tolerates line-wrapped multi-word names like
     * "Spring Boot" even though normalize() already collapsed whitespace to single spaces.
     *
     * The boundary is a hardened variant of `\b`: plain `\b` only distinguishes word chars
     * (`\w`) from everything else, so `\bC\b` still matches the "C" inside "C++" or "C#" —
     * `+` and `#` are non-word characters, so there IS a `\w`/`\W` transition right where "C"
     * ends, even though "C++" and "C#" are different skills entirely. Excluding `+`/`#` from
     * both sides (in addition to the usual `\w`) closes that gap while leaving every other
     * `\b` case (spaces, punctuation, line start/end, "Java" vs "JavaScript") unchanged.
     */
    private Pattern buildSkillPattern(String skillName) {
        String joined = Arrays.stream(skillName.trim().split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));

        return Pattern.compile(
                "(?<![\\w+#])" + joined + "(?![\\w+#])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    /** One section keyword/header rule: an EvidenceSource plus its recognized header phrases. */
    private record SectionHeaderRule(EvidenceSource source, List<String> keywords) {
        SectionHeaderRule(EvidenceSource source, String... keywords) {
            this(source, List.of(keywords));
        }

        boolean matches(String strippedLine) {
            if (strippedLine.length() > 60) {
                // A real header is a short label, never a full sentence — guards against a
                // long line that happens to end in a keyword-like phrase.
                return false;
            }
            for (String keyword : keywords) {
                if (strippedLine.equalsIgnoreCase(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A line-matching function: (skill, normalizedText) -> whether it matches. */
    private interface SkillTextMatcher {
        boolean matches(Skill skill, String normalizedText);
    }

    /** Per-section CV lines, with lazily-normalized full text per section for pattern matching. */
    private final class Sections {
        private final Map<EvidenceSource, List<String>> linesBySection;
        private final Map<EvidenceSource, String> normalizedTextBySection = new EnumMap<>(EvidenceSource.class);

        Sections(Map<EvidenceSource, List<String>> linesBySection) {
            this.linesBySection = linesBySection;
        }

        String textFor(EvidenceSource source) {
            return normalizedTextBySection.computeIfAbsent(
                    source, s -> normalize(String.join(" ", linesBySection.get(s))));
        }

        /**
         * The specific CV line a skill's match came from, trimmed and length-capped — never
         * the full CV. Falls back to the section's own (already-capped) joined text only for
         * the rare case where a multi-word skill name itself spans two lines, so no single
         * line contains the whole match.
         */
        String textReferenceFor(EvidenceSource source, Skill skill, SkillTextMatcher matcher) {
            for (String line : linesBySection.get(source)) {
                if (matcher.matches(skill, normalize(line))) {
                    return cap(line);
                }
            }
            return cap(textFor(source));
        }

        private String cap(String text) {
            if (text.length() <= MAX_TEXT_REFERENCE_LENGTH) {
                return text;
            }
            return text.substring(0, MAX_TEXT_REFERENCE_LENGTH) + "…";
        }
    }
}
