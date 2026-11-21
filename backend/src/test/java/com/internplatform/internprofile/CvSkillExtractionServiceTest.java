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
import java.util.Optional;
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

    private List<SkillEvidence> evidence(String text, Skill... catalog) {
        return service.detectSkillEvidence(text, List.of(catalog));
    }

    private SkillEvidence evidenceFor(List<SkillEvidence> evidence, String skillName) {
        return evidence.stream()
                .filter(e -> e.skill().getName().equals(skillName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No evidence found for " + skillName));
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

    // --- Evidence-section classification (Stage 2) ---------------------------------------

    @Test
    void skillWithNoSectionHeadersAtAllIsUnknownAndWeak() {
        // A CV with no recognizable structure must never have evidence fabricated for it.
        List<SkillEvidence> found = evidence("Experienced with Java and SQL.", skill("Java"), skill("SQL"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.UNKNOWN);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.WEAK);
    }

    @Test
    void skillUnderSkillsListHeaderIsWeakEvidence() {
        List<SkillEvidence> found = evidence(
                "Skills\nJava, SQL, Excel", skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.SKILLS_LIST);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.WEAK);
    }

    @Test
    void skillUnderProjectsHeaderIsStrongEvidence() {
        List<SkillEvidence> found = evidence(
                "Projects\nBuilt a REST backend in Java for a course project.", skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.PROJECT);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void skillUnderWorkExperienceHeaderIsStrongEvidence() {
        List<SkillEvidence> found = evidence(
                "Work Experience\nWrote backend services in Java at Acme Corp.", skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.WORK_EXPERIENCE);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void skillUnderInternshipHeaderIsStrongEvidence() {
        List<SkillEvidence> found = evidence(
                "Internships\nSummer internship using Python for data pipelines.", skill("Python"));

        SkillEvidence python = evidenceFor(found, "Python");
        assertThat(python.source()).isEqualTo(EvidenceSource.INTERNSHIP);
        assertThat(python.strength()).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void skillUnderCertificationsHeaderIsModerateEvidence() {
        List<SkillEvidence> found = evidence(
                "Certifications\nAWS Certified Developer — Python track.", skill("Python"));

        SkillEvidence python = evidenceFor(found, "Python");
        assertThat(python.source()).isEqualTo(EvidenceSource.CERTIFICATION);
        assertThat(python.strength()).isEqualTo(EvidenceStrength.MODERATE);
    }

    @Test
    void skillUnderCourseworkHeaderIsModerateEvidence() {
        List<SkillEvidence> found = evidence(
                "Relevant Coursework\nData Structures in Java, Databases.", skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.COURSEWORK);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.MODERATE);
    }

    @Test
    void arabicSectionHeaderIsRecognized() {
        // "الخبرة" ("Experience") as a standalone header line.
        List<SkillEvidence> found = evidence(
                "الخبرة\nعملت على تطوير الواجهات الخلفية باستخدام Java.", skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.WORK_EXPERIENCE);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void sentenceMentioningExperienceIsNotMistakenForAHeader() {
        // "experience" appears inside a normal sentence, not as a standalone header line —
        // ambiguous-structure case: must stay UNKNOWN rather than guessing WORK_EXPERIENCE.
        List<SkillEvidence> found = evidence(
                "I have five years of experience with Java development.", skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.UNKNOWN);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.WEAK);
    }

    @Test
    void duplicateEvidenceAcrossSectionsResolvesToTheStrongestSingleRecord() {
        // Java is mentioned both in a Skills list AND in a Projects section — exactly one
        // evidence record must result, and it must be the stronger one.
        List<SkillEvidence> found = evidence(
                "Skills\nJava, SQL\n\nProjects\nBuilt a Java-based inventory system.",
                skill("Java"));

        assertThat(found).hasSize(1);
        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.source()).isEqualTo(EvidenceSource.PROJECT);
        assertThat(java.strength()).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void duplicateMentionsWithinTheSameSectionStillResolveToOneRecord() {
        List<SkillEvidence> found = evidence(
                "Projects\nBuilt a Java service.\nExtended the Java service with a new API.",
                skill("Java"));

        assertThat(found).hasSize(1);
        assertThat(evidenceFor(found, "Java").source()).isEqualTo(EvidenceSource.PROJECT);
    }

    @Test
    void textReferenceCapturesTheMatchingLineNotTheWholeDocument() {
        List<SkillEvidence> found = evidence(
                "Projects\nBuilt a Java-based inventory system for a retail client.",
                skill("Java"));

        SkillEvidence java = evidenceFor(found, "Java");
        assertThat(java.textReference()).isEqualTo("Built a Java-based inventory system for a retail client.");
    }

    @Test
    void detectSkillsAndDetectSkillEvidenceAgreeOnWhichSkillsAreDetected() {
        String text = "Projects\nBuilt a Java service.\n\nSkills\nSQL, Excel.";
        Skill java = skill("Java");
        Skill sql = skill("SQL");
        Skill excel = skill("Excel");
        Skill python = skill("Python");

        Set<Skill> viaDetectSkills = service.detectSkills(text, List.of(java, sql, excel, python));
        Set<Skill> viaEvidence = evidence(text, java, sql, excel, python).stream()
                .map(SkillEvidence::skill)
                .collect(Collectors.toSet());

        assertThat(viaDetectSkills).isEqualTo(viaEvidence);
        assertThat(viaDetectSkills).containsExactlyInAnyOrder(java, sql, excel);
    }

    @Test
    void aliasMatchIsAlsoClassifiedBySection() {
        List<SkillEvidence> found = evidence(
                "Projects\nDeployed services on Postgres.",
                skillWithAliases("PostgreSQL", "Postgres"));

        SkillEvidence postgres = evidenceFor(found, "PostgreSQL");
        assertThat(postgres.source()).isEqualTo(EvidenceSource.PROJECT);
        assertThat(postgres.strength()).isEqualTo(EvidenceStrength.STRONG);
    }

    // --- maximumSupportableStrength (Stage 5A: AI claim grounding support) ----------------

    @Test
    void maximumSupportableStrengthFindsTheExcerptsSection() {
        String text = "Projects\nBuilt a Java-based inventory system for a retail client.";

        Optional<EvidenceStrength> ceiling = service.maximumSupportableStrength(
                text, "Built a Java-based inventory system for a retail client.");

        assertThat(ceiling).contains(EvidenceStrength.STRONG);
    }

    @Test
    void maximumSupportableStrengthIsWeakForABareSkillsListMention() {
        String text = "Skills\nJava, SQL, Excel";

        Optional<EvidenceStrength> ceiling = service.maximumSupportableStrength(text, "Java");

        assertThat(ceiling).contains(EvidenceStrength.WEAK);
    }

    @Test
    void maximumSupportableStrengthIsEmptyWhenExcerptIsNotInTheText() {
        String text = "Projects\nBuilt a Java-based inventory system.";

        Optional<EvidenceStrength> ceiling = service.maximumSupportableStrength(
                text, "Led a team of ten engineers at a Fortune 500 company.");

        assertThat(ceiling).isEmpty();
    }

    @Test
    void maximumSupportableStrengthIsEmptyForBlankOrNullExcerpt() {
        String text = "Projects\nBuilt a Java-based inventory system.";

        assertThat(service.maximumSupportableStrength(text, "")).isEmpty();
        assertThat(service.maximumSupportableStrength(text, "   ")).isEmpty();
        assertThat(service.maximumSupportableStrength(text, null)).isEmpty();
    }

    @Test
    void maximumSupportableStrengthPrefersTheStrongestSectionWhenExcerptAppearsInMultiple() {
        String text = "Skills\nSame exact phrase appears here.\n\nWork Experience\nSame exact phrase appears here.";

        Optional<EvidenceStrength> ceiling = service.maximumSupportableStrength(text, "Same exact phrase appears here.");

        assertThat(ceiling).contains(EvidenceStrength.STRONG);
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
