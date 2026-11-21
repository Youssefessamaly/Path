package com.internplatform.screening.ai;

import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.internprofile.SkillRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The mandatory gate every AiSuggestedEvidence claim must pass before it is persisted or
 * shown to a recruiter — regardless of which AiEvidenceAnalysisService implementation
 * produced it, including a future real provider. A matching quote alone is never treated as
 * proof of proficiency: strength claims are capped by what the cited excerpt's own CV section
 * deterministically supports (see CvSkillExtractionService#maximumSupportableStrength), reusing
 * the exact same rules CV-derived evidence already follows rather than trusting the model's
 * self-reported confidence.
 *
 * Pure and Spring-independent in its core logic (constructor-injected collaborators only) so
 * it can be unit-tested directly against synthetic claims without a full application context.
 */
@Component
public class EvidenceClaimValidator {

    private static final List<EvidenceStrength> STRENGTH_RANK =
            List.of(EvidenceStrength.WEAK, EvidenceStrength.MODERATE, EvidenceStrength.STRONG);

    private final SkillRepository skillRepository;
    private final CvSkillExtractionService cvSkillExtractionService;

    public EvidenceClaimValidator(SkillRepository skillRepository, CvSkillExtractionService cvSkillExtractionService) {
        this.skillRepository = skillRepository;
        this.cvSkillExtractionService = cvSkillExtractionService;
    }

    public List<ValidatedAiEvidenceClaim> validateAll(
            List<AiSuggestedEvidence> claims, String authorizedCvText, Set<UUID> validRequirementIds
    ) {
        return claims.stream()
                .map(claim -> validate(claim, authorizedCvText, validRequirementIds))
                .toList();
    }

    /**
     * Validates one claim against, in order:
     * 1. The requirement it cites must belong to the rubric actually being screened —
     *    otherwise it could point a recruiter at criteria from an unrelated internship.
     * 2. A cited canonical skill id must exist in the real skills catalog — AI can never
     *    introduce a skill that isn't one PATH actually recognizes.
     * 3. An excerpt must be provided at all.
     * 4. The excerpt must be found, verbatim (case/whitespace-normalized), inside the
     *    SPECIFIC application's authorized, already-extracted frozen CV text — never trusted
     *    as a "model-created quote," and never checked against any other candidate's CV.
     * 5. The claimed evidence strength can never exceed what the excerpt's own CV section
     *    would deterministically support; an over-claim is downgraded and flagged for
     *    recruiter review rather than silently accepted at face value.
     */
    public ValidatedAiEvidenceClaim validate(
            AiSuggestedEvidence claim, String authorizedCvText, Set<UUID> validRequirementIds
    ) {
        if (claim.requirementId() == null || !validRequirementIds.contains(claim.requirementId())) {
            return rejected(claim, "Requirement does not belong to the rubric being screened");
        }

        if (claim.canonicalSkillId() != null && !skillRepository.existsById(claim.canonicalSkillId())) {
            return rejected(claim, "References an unknown canonical skill id");
        }

        if (claim.excerptQuote() == null || claim.excerptQuote().isBlank()) {
            return rejected(claim, "No supporting excerpt was provided");
        }

        Optional<EvidenceStrength> ceiling =
                cvSkillExtractionService.maximumSupportableStrength(authorizedCvText, claim.excerptQuote());
        if (ceiling.isEmpty()) {
            return rejected(claim, "The cited excerpt could not be found in the candidate's authorized CV text");
        }

        boolean overclaimed = claim.suggestedStrength() != null
                && rank(claim.suggestedStrength()) > rank(ceiling.get());

        if (overclaimed) {
            return new ValidatedAiEvidenceClaim(
                    claim,
                    EvidenceClaimStatus.ACCEPTED_NEEDS_REVIEW,
                    ceiling.get(),
                    "Claimed strength " + claim.suggestedStrength() + " exceeds what the cited excerpt supports ("
                            + ceiling.get() + "); downgraded and flagged for recruiter review"
            );
        }

        if (claim.uncertain()) {
            return new ValidatedAiEvidenceClaim(
                    claim,
                    EvidenceClaimStatus.ACCEPTED_NEEDS_REVIEW,
                    ceiling.get(),
                    "Excerpt verified, but the model flagged this suggestion as uncertain"
            );
        }

        return new ValidatedAiEvidenceClaim(
                claim,
                EvidenceClaimStatus.ACCEPTED,
                ceiling.get(),
                "Excerpt verified against the authorized CV text; claimed strength is consistent with its source section"
        );
    }

    private int rank(EvidenceStrength strength) {
        return STRENGTH_RANK.indexOf(strength);
    }

    private ValidatedAiEvidenceClaim rejected(AiSuggestedEvidence claim, String reason) {
        return new ValidatedAiEvidenceClaim(claim, EvidenceClaimStatus.REJECTED, null, reason);
    }
}
