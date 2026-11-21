package com.internplatform.internprofile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests — no Spring context — for the deterministic PDF text extraction and
 * catalog skill matching rules. Deliberately isolated from the full upload flow so the
 * tricky matching cases (word-boundary safety, multi-word names, case-insensitivity) are
 * fast and precise to verify.
 */
class CvSkillExtractionServiceTest {

    private final CvSkillExtractionService service = new CvSkillExtractionService();

    private Skill skill(String name) {
        return new Skill(name);
    }

    private Skill skillWithAliases(String name, String... aliases) {
        Skill skill = new Skill(name);
        skill.getAliases().addAll(List.of(aliases));
        return skill;
    }

    private Set<String> detectedNames(String text, Skill... catalog) {
        return service.detectSkills(text, List.of(catalog)).stream()
                .map(Skill::getName)
                .collect(Collectors.toSet());
    }

    @Test
    void detectsExactSkillMention() {
        Set<String> detected = detectedNames("Experienced Java developer.", skill("Java"), skill("SQL"));

        assertThat(detected).containsExactly("Java");
    }

    @Test
    void javaScriptMentionDoesNotFalselyDetectJava() {
        Set<String> detected = detectedNames(
                "Proficient in JavaScript development.", skill("Java"), skill("JavaScript"));

        assertThat(detected).containsExactly("JavaScript");
    }

    @Test
    void javaAndJavaScriptBothMatchWhenBothIndependentlyPresent() {
        Set<String> detected = detectedNames(
                "Wrote Java backends and JavaScript frontends.", skill("Java"), skill("JavaScript"));

        assertThat(detected).containsExactlyInAnyOrder("Java", "JavaScript");
    }

    @Test
    void multiWordSkillNameMatches() {
        Set<String> detected = detectedNames(
                "Built services with Spring Boot and visualized data in Power BI.",
                skill("Spring Boot"), skill("Power BI"), skill("Project Management"));

        assertThat(detected).containsExactlyInAnyOrder("Spring Boot", "Power BI");
    }

    @Test
    void multiWordSkillNameMatchesAcrossNormalizedWhitespace() {
        // Simulates text extracted from a PDF where the phrase wrapped across lines.
        Set<String> detected = detectedNames("Spring\n   Boot\tframework", skill("Spring Boot"));

        assertThat(detected).containsExactly("Spring Boot");
    }

    @Test
    void detectionIsCaseInsensitive() {
        Set<String> detected = detectedNames("STRONG PYTHON AND excel SKILLS", skill("Python"), skill("Excel"));

        assertThat(detected).containsExactlyInAnyOrder("Python", "Excel");
    }

    @Test
    void noRecognizedSkillsYieldsEmptySet() {
        Set<String> detected = detectedNames(
                "Looking forward to a great opportunity.", skill("Java"), skill("SQL"));

        assertThat(detected).isEmpty();
    }

    @Test
    void emptyCatalogYieldsEmptySet() {
        Set<Skill> detected = service.detectSkills("Java SQL Python", List.of());

        assertThat(detected).isEmpty();
    }

    @Test
    void extractTextReturnsContentFromAGenuinePdf() throws IOException {
        byte[] pdf = createPdfWithText("Experienced Java and SQL developer.");

        String text = service.extractText(pdf);

        assertThat(text).contains("Java").contains("SQL");
    }

    @Test
    void extractTextRejectsStructurallyInvalidPdf() {
        // Has the right magic bytes but no real PDF structure behind them.
        byte[] fake = "%PDF-1.4\nnot a real pdf body\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> service.extractText(fake))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("valid, text-readable PDF");
    }

    @Test
    void aliasResolvesToCanonicalSkill() {
        // "JS" must resolve to the canonical "JavaScript" skill, not a separate skill.
        Skill javaScript = skillWithAliases("JavaScript", "JS");

        Set<String> detected = service.detectSkills("5 years of JS experience.", List.of(javaScript)).stream()
                .map(Skill::getName)
                .collect(Collectors.toSet());

        assertThat(detected).containsExactly("JavaScript");
    }

    @Test
    void multipleAliasesAllResolveToTheSameCanonicalSkill() {
        Skill postgres = skillWithAliases("PostgreSQL", "Postgres", "Postgre SQL");

        assertThat(service.detectSkills("Database: Postgres.", List.of(postgres))).containsExactly(postgres);
        assertThat(service.detectSkills("Database: PostgreSQL.", List.of(postgres))).containsExactly(postgres);
    }

    @Test
    void aliasMatchDoesNotAlsoRequireTheCanonicalNameToBePresent() {
        Skill react = skillWithAliases("React", "React.js");

        Set<Skill> detected = service.detectSkills("Built UIs with React.js.", List.of(react));

        assertThat(detected).containsExactly(react);
    }

    @Test
    void reactMentionDoesNotFalselyDetectReactNative() {
        Set<String> detected = detectedNames(
                "Frontend experience with React.", skill("React"), skill("React Native"));

        assertThat(detected).containsExactly("React");
    }

    @Test
    void reactNativeMentionAlsoCountsAsReactEvidence() {
        // Mentioning React Native genuinely implies some React exposure — the reverse of the
        // previous case, where a bare "React" mention must NOT imply React Native.
        Set<String> detected = detectedNames(
                "Built mobile apps with React Native.", skill("React"), skill("React Native"));

        assertThat(detected).containsExactlyInAnyOrder("React", "React Native");
    }

    @Test
    void cPlusPlusMentionDoesNotFalselyDetectBareC() {
        Set<String> detected = detectedNames(
                "Systems programming in C++ and Python.", skill("C"), skill("C++"), skill("Python"));

        assertThat(detected).containsExactlyInAnyOrder("C++", "Python");
    }

    @Test
    void cSharpMentionDoesNotFalselyDetectBareC() {
        Set<String> detected = detectedNames(
                "Desktop apps in C# using .NET.", skill("C"), skill("C#"));

        assertThat(detected).containsExactly("C#");
    }

    @Test
    void bareCIsStillDetectedWhenGenuinelyStandalone() {
        Set<String> detected = detectedNames(
                "Embedded firmware written in C.", skill("C"), skill("C++"));

        assertThat(detected).containsExactly("C");
    }

    @Test
    void detectSkillsReturnsTheActualCatalogSkillInstance() {
        // Detection must hand back the real catalog Skill object (so callers can read its
        // id), not just a name match.
        Skill javaSkill = skill("Java");

        Set<Skill> detected = service.detectSkills("Java developer", List.of(javaSkill));

        assertThat(detected).containsExactly(javaSkill);
    }

    private static byte[] createPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
