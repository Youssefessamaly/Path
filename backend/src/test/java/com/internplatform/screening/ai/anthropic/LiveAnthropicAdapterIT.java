package com.internplatform.screening.ai.anthropic;

import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.screening.InternshipRubricRequirement;
import com.internplatform.screening.InternshipRubricRequirementRepository;
import com.internplatform.screening.RequirementType;
import com.internplatform.screening.ai.AiEvidenceAnalysisRequest;
import com.internplatform.screening.ai.AiEvidenceAnalysisResult;
import com.internplatform.screening.ai.AiRubricDraftRequest;
import com.internplatform.screening.ai.AiRubricDraftResult;
import com.internplatform.screening.ai.AiSuggestedEvidence;
import com.internplatform.screening.ai.EvidenceClaimStatus;
import com.internplatform.screening.ai.EvidenceClaimValidator;
import com.internplatform.screening.ai.ValidatedAiEvidenceClaim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LIVE integration test against the real Anthropic Messages API — makes real, billed network
 * calls. Deliberately NOT run by a plain `mvn test`: both conditions below must hold, so this
 * class is silently SKIPPED (not failed) in every normal run, every CI run, and for every
 * developer who has not explicitly opted in. This is how "production AI stays disabled by
 * default" is upheld even while this file exists on disk.
 *
 * To run deliberately (as was done for the Stage 5B Phase B report):
 *   RUN_LIVE_ANTHROPIC_TESTS=true ANTHROPIC_API_KEY=... ./mvnw test -Dtest=LiveAnthropicAdapterIT
 *
 * Only synthetic, fictional candidate data is used — no real applicant CV, no production
 * database connection (SkillRepository/InternshipRubricRequirementRepository are mocked, not
 * real repositories), and no ScreeningReport/InternshipApplication is touched. The API key is
 * read directly from the environment and passed straight into HttpAnthropicMessagesClient; it
 * is never assigned to a variable that is printed, asserted on, or logged.
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_ANTHROPIC_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LiveAnthropicAdapterIT {

    private static final String ENGLISH_CV = """
            JOHN SAMPLE (fictional test candidate)

            WORK EXPERIENCE
            Backend Developer Intern, Fictional Tech Co, 2024-2025
            Built REST APIs using Java and Spring Boot. Collaborated on a microservices migration.

            SKILLS
            Java, Python, SQL

            PROJECTS
            Developed a Python script to automate data cleaning for a university course.
            """;

    private static final String ARABIC_CV = """
            سارة نموذجية (مرشحة تجريبية خيالية)

            الخبرة العملية
            متدربة في تطوير الواجهات الخلفية في شركة تقنية افتراضية، 2024-2025
            قامت ببناء واجهات برمجية باستخدام جافا وسبرينج بوت.

            المهارات
            جافا، بايثون، SQL
            """;

    private String apiKey() {
        return System.getenv("ANTHROPIC_API_KEY");
    }

    private String model() {
        String configured = System.getenv("ANTHROPIC_MODEL");
        return configured != null && !configured.isBlank() ? configured : "claude-haiku-5-5";
    }

    private HttpAnthropicMessagesClient realClient() {
        return new HttpAnthropicMessagesClient(apiKey(), "https://api.anthropic.com", "2023-06-01", 15000, 1);
    }

    private InternshipRubricRequirement requirement(UUID id, UUID rubricId, RequirementType type, UUID skillId, String label) {
        InternshipRubricRequirement requirement = new InternshipRubricRequirement(rubricId, type);
        ReflectionTestUtils.setField(requirement, "id", id);
        ReflectionTestUtils.setField(requirement, "skillId", skillId);
        ReflectionTestUtils.setField(requirement, "label", label);
        ReflectionTestUtils.setField(requirement, "acceptedEvidenceSources", Set.of());
        return requirement;
    }

    @Test
    void evidenceAnalysisWorksEndToEndWithASyntheticEnglishCv() {
        UUID javaSkillId = UUID.randomUUID();
        UUID pythonSkillId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID javaReqId = UUID.randomUUID();
        UUID pythonReqId = UUID.randomUUID();
        UUID kubernetesReqId = UUID.randomUUID();

        SkillRepository skillRepository = mock(SkillRepository.class);
        Skill java = new Skill("Java");
        ReflectionTestUtils.setField(java, "id", javaSkillId);
        Skill python = new Skill("Python");
        ReflectionTestUtils.setField(python, "id", pythonSkillId);
        when(skillRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
                .thenReturn(List.of(java, python));

        InternshipRubricRequirementRepository requirementRepository = mock(InternshipRubricRequirementRepository.class);
        when(requirementRepository.findAllByInternshipRubricId(rubricId)).thenReturn(List.of(
                requirement(javaReqId, rubricId, RequirementType.REQUIRED, javaSkillId, null),
                requirement(pythonReqId, rubricId, RequirementType.PREFERRED, pythonSkillId, null),
                requirement(kubernetesReqId, rubricId, RequirementType.BONUS, null, "Kubernetes experience")
        ));

        EvidenceClaimValidator validator = new EvidenceClaimValidator(skillRepository, new CvSkillExtractionService());
        AnthropicEvidenceAnalysisService service = new AnthropicEvidenceAnalysisService(
                realClient(), new AiPromptBuilder(), new AiResponseParser(), validator, new AiRateLimiter(),
                new PersonalIdentifierRedactor(), requirementRepository, skillRepository,
                model(), 1024, 50, true);

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), rubricId, ENGLISH_CV,
                List.of(javaReqId, pythonReqId, kubernetesReqId));

        long startedAt = System.currentTimeMillis();
        AiEvidenceAnalysisResult result = service.analyzeEvidence(request);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        System.out.println("[LIVE] English CV analysis: model=" + model() + " completed=" + result.completed()
                + " suggestionCount=" + result.suggestions().size() + " elapsedMs=" + elapsedMs);
        result.suggestions().forEach(s -> System.out.println("[LIVE]   requirementId=" + s.requirementId()
                + " strength=" + s.suggestedStrength() + " uncertain=" + s.uncertain()));

        assertThat(result.completed()).isTrue();
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions()).allSatisfy(s ->
                assertThat(List.of(javaReqId, pythonReqId, kubernetesReqId)).contains(s.requirementId()));
        // Every returned suggestion has already passed EvidenceClaimValidator inside the
        // service, so its excerpt must genuinely appear in the authorized CV text.
        result.suggestions().forEach(s -> assertThat(ENGLISH_CV).contains(s.excerptQuote()));
    }

    @Test
    void evidenceAnalysisWorksEndToEndWithASyntheticArabicCv() {
        UUID javaSkillId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        UUID javaReqId = UUID.randomUUID();

        SkillRepository skillRepository = mock(SkillRepository.class);
        Skill java = new Skill("Java");
        ReflectionTestUtils.setField(java, "id", javaSkillId);
        when(skillRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
                .thenReturn(List.of(java));

        InternshipRubricRequirementRepository requirementRepository = mock(InternshipRubricRequirementRepository.class);
        when(requirementRepository.findAllByInternshipRubricId(rubricId)).thenReturn(List.of(
                requirement(javaReqId, rubricId, RequirementType.REQUIRED, javaSkillId, null)
        ));

        EvidenceClaimValidator validator = new EvidenceClaimValidator(skillRepository, new CvSkillExtractionService());
        AnthropicEvidenceAnalysisService service = new AnthropicEvidenceAnalysisService(
                realClient(), new AiPromptBuilder(), new AiResponseParser(), validator, new AiRateLimiter(),
                new PersonalIdentifierRedactor(), requirementRepository, skillRepository,
                model(), 1024, 50, true);

        AiEvidenceAnalysisRequest request = new AiEvidenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), rubricId, ARABIC_CV, List.of(javaReqId));

        long startedAt = System.currentTimeMillis();
        AiEvidenceAnalysisResult result = service.analyzeEvidence(request);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        System.out.println("[LIVE] Arabic CV analysis: model=" + model() + " completed=" + result.completed()
                + " suggestionCount=" + result.suggestions().size() + " elapsedMs=" + elapsedMs);
        result.suggestions().forEach(s -> System.out.println("[LIVE]   requirementId=" + s.requirementId()
                + " strength=" + s.suggestedStrength() + " uncertain=" + s.uncertain()
                + " excerptLength=" + s.excerptQuote().length()));

        assertThat(result.completed()).isTrue();
        // Not asserting non-empty here: unlike the English case, this is a genuine capability
        // probe (can Haiku connect "جافا" to the Java requirement in Arabic CV text?) rather
        // than a guaranteed-pass assertion — the report records what actually happened either
        // way, see the printed suggestion count above.
        result.suggestions().forEach(s -> assertThat(ARABIC_CV).contains(s.excerptQuote()));
    }

    @Test
    void rubricDraftWorksEndToEndWithEnglishInstructions() {
        SkillRepository skillRepository = mock(SkillRepository.class);

        AnthropicRubricDraftService service = new AnthropicRubricDraftService(
                realClient(), new AiPromptBuilder(), new AiResponseParser(), new AiRateLimiter(), skillRepository,
                model(), 1024, 50);

        AiRubricDraftRequest request = new AiRubricDraftRequest(
                UUID.randomUUID(), "We are hiring a backend engineering intern who should know Java and SQL.");

        long startedAt = System.currentTimeMillis();
        AiRubricDraftResult result = service.draftFromInstructions(request);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        System.out.println("[LIVE] Rubric draft: model=" + model() + " completed=" + result.completed()
                + " requirementCount=" + result.suggestedRequirements().size() + " elapsedMs=" + elapsedMs);
        result.suggestedRequirements().forEach(r -> System.out.println("[LIVE]   type=" + r.requirementType()
                + " label=" + r.label() + " weight=" + r.suggestedWeight()));

        assertThat(result.completed()).isTrue();
        assertThat(result.suggestedRequirements()).isNotEmpty();
    }

    /**
     * NOT a live call — makes no network request. Proves that even in this live-wired test
     * context, a fabricated claim (an excerpt that does not exist anywhere in the authorized
     * CV text) is rejected by the real, unmodified EvidenceClaimValidator, exactly as it would
     * be if a real model response had contained one.
     */
    @Test
    void fabricatedClaimIsRejectedByTheRealValidatorEvenWithoutANetworkCall() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        EvidenceClaimValidator validator = new EvidenceClaimValidator(skillRepository, new CvSkillExtractionService());
        UUID requirementId = UUID.randomUUID();

        AiSuggestedEvidence fabricated = new AiSuggestedEvidence(
                requirementId, null, "This exact sentence does not appear anywhere in the CV.",
                EvidenceStrength.STRONG, false, "fabricated for this test");

        ValidatedAiEvidenceClaim validated = validator.validate(fabricated, ENGLISH_CV, Set.of(requirementId));

        System.out.println("[CHECK] Fabricated claim status=" + validated.status());
        assertThat(validated.status()).isEqualTo(EvidenceClaimStatus.REJECTED);
    }
}
