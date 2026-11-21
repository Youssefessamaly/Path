package com.internplatform.screening.ai;

import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.internprofile.SkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests — no Spring context. SkillRepository is mocked; CvSkillExtractionService
 * is real (pure, same as CvSkillExtractionServiceTest) since the whole point is to verify the
 * validator actually enforces grounding against real section/strength logic, not a stub.
 */
class EvidenceClaimValidatorTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final CvSkillExtractionService cvSkillExtractionService = new CvSkillExtractionService();
    private final EvidenceClaimValidator validator = new EvidenceClaimValidator(skillRepository, cvSkillExtractionService);

    private static final String CV_TEXT = "Projects\nBuilt a Java-based inventory system.\n\nSkills\nJava, SQL";

    private AiSuggestedEvidence claim(UUID requirementId, UUID skillId, String excerpt, EvidenceStrength strength, boolean uncertain) {
        return new AiSuggestedEvidence(requirementId, skillId, excerpt, strength, uncertain, "because the CV mentions it");
    }

    @Test
    void acceptsAFullyGroundedClaimWhoseStrengthMatchesItsSection() {
        UUID requirementId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        when(skillRepository.existsById(skillId)).thenReturn(true);

        AiSuggestedEvidence suggestion = claim(requirementId, skillId,
                "Built a Java-based inventory system.", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.ACCEPTED);
        assertThat(result.effectiveStrength()).isEqualTo(EvidenceStrength.STRONG);
    }

    @Test
    void acceptsAClaimWithNoCanonicalSkillWhenOtherwiseGrounded() {
        UUID requirementId = UUID.randomUUID();

        AiSuggestedEvidence suggestion = claim(requirementId, null,
                "Built a Java-based inventory system.", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.ACCEPTED);
    }

    @Test
    void rejectsAClaimWhoseRequirementDoesNotBelongToThisRubric() {
        UUID requirementId = UUID.randomUUID();
        UUID someOtherRequirementId = UUID.randomUUID();

        AiSuggestedEvidence suggestion = claim(requirementId, null,
                "Built a Java-based inventory system.", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(someOtherRequirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.REJECTED);
        assertThat(result.effectiveStrength()).isNull();
        assertThat(result.note()).contains("does not belong");
    }

    @Test
    void rejectsAClaimReferencingAnUnknownCanonicalSkill() {
        UUID requirementId = UUID.randomUUID();
        UUID unknownSkillId = UUID.randomUUID();
        when(skillRepository.existsById(unknownSkillId)).thenReturn(false);

        AiSuggestedEvidence suggestion = claim(requirementId, unknownSkillId,
                "Built a Java-based inventory system.", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.REJECTED);
        assertThat(result.note()).contains("unknown canonical skill");
    }

    @Test
    void rejectsAClaimWithNoExcerpt() {
        UUID requirementId = UUID.randomUUID();

        AiSuggestedEvidence suggestion = claim(requirementId, null, "   ", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.REJECTED);
        assertThat(result.note()).contains("No supporting excerpt");
    }

    @Test
    void rejectsAFabricatedQuoteNotPresentInTheAuthorizedCvText() {
        UUID requirementId = UUID.randomUUID();

        AiSuggestedEvidence suggestion = claim(requirementId, null,
                "Led a team of twenty engineers at a Fortune 500 company.", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.REJECTED);
        assertThat(result.note()).contains("could not be found");
    }

    @Test
    void downgradesAnOverclaimedStrengthAndFlagsItForRecruiterReview() {
        UUID requirementId = UUID.randomUUID();

        // "SQL" is real in CV_TEXT, but only as a bare Skills-list mention (WEAK ceiling) —
        // it never appears in the Projects section. The model is not allowed to treat a
        // matching quote alone as proof of STRONG proficiency.
        AiSuggestedEvidence suggestion = claim(requirementId, null, "SQL", EvidenceStrength.STRONG, false);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.ACCEPTED_NEEDS_REVIEW);
        assertThat(result.effectiveStrength()).isEqualTo(EvidenceStrength.WEAK);
        assertThat(result.note()).contains("exceeds what the cited excerpt supports");
    }

    @Test
    void flagsAModelReportedUncertainClaimForReviewEvenWhenGrounded() {
        UUID requirementId = UUID.randomUUID();

        AiSuggestedEvidence suggestion = claim(requirementId, null,
                "Built a Java-based inventory system.", EvidenceStrength.STRONG, true);

        ValidatedAiEvidenceClaim result = validator.validate(suggestion, CV_TEXT, Set.of(requirementId));

        assertThat(result.status()).isEqualTo(EvidenceClaimStatus.ACCEPTED_NEEDS_REVIEW);
        assertThat(result.note()).contains("uncertain");
    }

    @Test
    void validateAllPreservesOrderAndHandlesAMixOfOutcomes() {
        UUID requirementId = UUID.randomUUID();
        AiSuggestedEvidence grounded = claim(requirementId, null, "Built a Java-based inventory system.", EvidenceStrength.STRONG, false);
        AiSuggestedEvidence fabricated = claim(requirementId, null, "Invented a time machine.", EvidenceStrength.STRONG, false);

        List<ValidatedAiEvidenceClaim> results = validator.validateAll(
                List.of(grounded, fabricated), CV_TEXT, Set.of(requirementId));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(EvidenceClaimStatus.ACCEPTED);
        assertThat(results.get(1).status()).isEqualTo(EvidenceClaimStatus.REJECTED);
    }
}
