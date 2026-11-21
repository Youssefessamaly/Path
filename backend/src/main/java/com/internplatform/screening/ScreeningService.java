package com.internplatform.screening;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.internprofile.CvSkillExtractionService;
import com.internplatform.internprofile.CvStorageService;
import com.internplatform.internprofile.EvidenceStrength;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillEvidence;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic, explainable screening: evaluates one application's frozen CV snapshot (never
 * the intern profile's possibly-since-replaced current CV — see evaluate()) against an
 * internship's frozen ACTIVE InternshipRubric version. No AI/ML, no external calls; the same
 * inputs always produce the same result (ALGORITHM_VERSION identifies which rules were used,
 * for when that ever changes). Never writes to InternshipApplication.status — screening is
 * purely informational, entirely separate from accept/reject decisions.
 */
@Service
public class ScreeningService {

    /** Bumped whenever the matching rules below change, so a stored report always identifies
     *  exactly which rules produced it. */
    static final String ALGORITHM_VERSION = "cv-rubric-matching-v1";

    static final int MAX_BATCH_SIZE = 25;

    private static final List<EvidenceStrength> STRENGTH_ORDER =
            List.of(EvidenceStrength.WEAK, EvidenceStrength.MODERATE, EvidenceStrength.STRONG);

    private final ScreeningReportRepository reportRepository;
    private final ScreeningReportResultRepository resultRepository;
    private final InternshipRubricRepository rubricRepository;
    private final InternshipRubricRequirementRepository requirementRepository;
    private final InternshipRepository internshipRepository;
    private final InternshipApplicationRepository applicationRepository;
    private final InternProfileRepository internProfileRepository;
    private final SkillRepository skillRepository;
    private final CvStorageService cvStorageService;
    private final CvSkillExtractionService cvSkillExtractionService;
    private final CompanyAccessResolver companyAccessResolver;

    public ScreeningService(
            ScreeningReportRepository reportRepository,
            ScreeningReportResultRepository resultRepository,
            InternshipRubricRepository rubricRepository,
            InternshipRubricRequirementRepository requirementRepository,
            InternshipRepository internshipRepository,
            InternshipApplicationRepository applicationRepository,
            InternProfileRepository internProfileRepository,
            SkillRepository skillRepository,
            CvStorageService cvStorageService,
            CvSkillExtractionService cvSkillExtractionService,
            CompanyAccessResolver companyAccessResolver
    ) {
        this.reportRepository = reportRepository;
        this.resultRepository = resultRepository;
        this.rubricRepository = rubricRepository;
        this.requirementRepository = requirementRepository;
        this.internshipRepository = internshipRepository;
        this.applicationRepository = applicationRepository;
        this.internProfileRepository = internProfileRepository;
        this.skillRepository = skillRepository;
        this.cvStorageService = cvStorageService;
        this.cvSkillExtractionService = cvSkillExtractionService;
        this.companyAccessResolver = companyAccessResolver;
    }

    @Transactional
    public ScreeningReportResponse screenApplication(UUID userId, UUID internshipId, UUID applicationId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipApplication application = findOwnedApplicationOrThrow(applicationId, internship.getId());
        InternshipRubric activeRubric = requireActiveRubric(internship.getId());

        ScreeningReport report = evaluateAndSave(internship, application, activeRubric);
        return toResponse(report, requirementRepository.findAllByInternshipRubricIdOrderByDisplayOrderAscCreatedAtAsc(activeRubric.getId()));
    }

    @Transactional(readOnly = true)
    public ScreeningReportResponse getLatestReport(UUID userId, UUID internshipId, UUID applicationId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipApplication application = findOwnedApplicationOrThrow(applicationId, internship.getId());

        ScreeningReport report = reportRepository.findFirstByApplicationIdOrderByEvaluatedAtDesc(application.getId())
                .filter(found -> found.getInternshipId().equals(internship.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This application has not been screened yet"));

        return toResponse(report, requirementRepository.findAllByInternshipRubricIdOrderByDisplayOrderAscCreatedAtAsc(report.getInternshipRubricId()));
    }

    /** Bounded, explicit, recruiter-initiated — never triggered automatically. */
    @Transactional
    public List<ScreeningReportResponse> screenBatch(UUID userId, UUID internshipId, ScreeningBatchRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipRubric activeRubric = requireActiveRubric(internship.getId());

        List<UUID> distinctIds = request.applicationIds().stream().distinct().toList();
        if (distinctIds.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most " + MAX_BATCH_SIZE + " applications can be screened in a single batch");
        }

        List<InternshipApplication> applications = applicationRepository.findAllById(distinctIds).stream()
                .filter(app -> app.getInternshipId().equals(internship.getId()))
                .toList();
        if (applications.size() != distinctIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more application ids are invalid for this internship");
        }

        List<InternshipRubricRequirement> requirements =
                requirementRepository.findAllByInternshipRubricIdOrderByDisplayOrderAscCreatedAtAsc(activeRubric.getId());

        return applications.stream()
                .map(application -> toResponse(evaluateAndSave(internship, application, activeRubric), requirements))
                .toList();
    }

    /** Pure read of already-stored reports — never computes a screening result as a side
     *  effect of listing, so loading this page is always cheap regardless of applicant count. */
    @Transactional(readOnly = true)
    public Page<ScreeningSummaryResponse> listSummaries(UUID userId, UUID internshipId, Pageable pageable) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        Page<InternshipApplication> applications =
                applicationRepository.findByInternshipIdOrderByAppliedAtDesc(internship.getId(), pageable);

        List<UUID> applicationIds = applications.getContent().stream().map(InternshipApplication::getId).toList();
        List<UUID> profileIds = applications.getContent().stream().map(InternshipApplication::getInternProfileId).toList();

        Map<UUID, InternProfile> profilesById = new HashMap<>();
        for (InternProfile profile : internProfileRepository.findAllById(profileIds)) {
            profilesById.put(profile.getId(), profile);
        }

        // One query for the whole page; first occurrence per application is its latest report
        // because the query is already ordered newest-first.
        Map<UUID, ScreeningReport> latestByApplication = new LinkedHashMap<>();
        for (ScreeningReport report : reportRepository.findAllByApplicationIdInOrderByEvaluatedAtDesc(applicationIds)) {
            latestByApplication.putIfAbsent(report.getApplicationId(), report);
        }

        List<ScreeningSummaryResponse> content = applications.getContent().stream()
                .map(application -> toSummary(application, profilesById.get(application.getInternProfileId()),
                        latestByApplication.get(application.getId())))
                .toList();

        return new PageImpl<>(content, pageable, applications.getTotalElements());
    }

    /**
     * Evaluates and persists a new, append-only report. Always re-extracts evidence fresh
     * from the APPLICATION's own frozen CV snapshot bytes (cvStorageService.read on
     * application.getCvStorageKey()) — never the intern profile's live cvDetectedSkills/
     * evidence, which may reflect a CV uploaded long after this application was submitted.
     * Manually-selected skills ARE read live from the profile (there is no application-time
     * snapshot of those today) — a deliberate, documented scope limit, not an oversight.
     */
    private ScreeningReport evaluateAndSave(Internship internship, InternshipApplication application, InternshipRubric rubric) {
        List<InternshipRubricRequirement> requirements =
                requirementRepository.findAllByInternshipRubricIdOrderByDisplayOrderAscCreatedAtAsc(rubric.getId());

        Set<UUID> neededSkillIds = new HashSet<>();
        for (InternshipRubricRequirement requirement : requirements) {
            if (requirement.getSkillId() != null) {
                neededSkillIds.add(requirement.getSkillId());
            }
        }
        Map<UUID, Skill> skillsById = new HashMap<>();
        for (Skill skill : skillRepository.findAllById(neededSkillIds)) {
            skillsById.put(skill.getId(), skill);
        }

        InternProfile profile = internProfileRepository.findById(application.getInternProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found"));
        Set<UUID> manualSkillIds = new HashSet<>();
        for (Skill skill : profile.getSkills()) {
            manualSkillIds.add(skill.getId());
        }

        EvidenceExtraction extraction = extractEvidence(application, skillsById.values());

        ScreeningReport report = new ScreeningReport(
                internship.getId(), application.getId(), rubric.getId(), rubric.getVersion(),
                application.getCvStorageKey(), ALGORITHM_VERSION);
        report.setCvTextUnavailable(extraction.textUnavailable());

        List<ScreeningReportResult> results = new java.util.ArrayList<>();
        int totalRequired = 0, matchedRequired = 0;
        int totalPreferred = 0, matchedPreferred = 0;
        int totalBonus = 0, matchedBonus = 0;
        int totalWeight = 0, matchedWeight = 0;

        ScreeningReport savedReport = reportRepository.saveAndFlush(report);

        for (InternshipRubricRequirement requirement : requirements) {
            Evaluation evaluation = evaluateRequirement(
                    requirement, extraction.evidenceBySkillId(), manualSkillIds, extraction.textUnavailable());

            ScreeningReportResult result = new ScreeningReportResult(savedReport.getId(), requirement.getId(), evaluation.result());
            result.setEvidenceSource(evaluation.source());
            result.setEvidenceStrength(evaluation.strength());
            result.setTextReference(evaluation.textReference());
            result.setReasoning(evaluation.reasoning());
            results.add(result);

            totalWeight += requirement.getWeight();
            boolean matched = evaluation.result() == ScreeningResult.MATCHED;
            if (matched) {
                matchedWeight += requirement.getWeight();
            }

            switch (requirement.getRequirementType()) {
                case REQUIRED -> {
                    totalRequired++;
                    if (matched) matchedRequired++;
                }
                case PREFERRED -> {
                    totalPreferred++;
                    if (matched) matchedPreferred++;
                }
                case BONUS -> {
                    totalBonus++;
                    if (matched) matchedBonus++;
                }
            }
        }
        resultRepository.saveAll(results);

        boolean anyNeedsReview = results.stream().anyMatch(r -> r.getResult() == ScreeningResult.NEEDS_REVIEW);

        savedReport.setTotalRequiredCount(totalRequired);
        savedReport.setMatchedRequiredCount(matchedRequired);
        savedReport.setTotalPreferredCount(totalPreferred);
        savedReport.setMatchedPreferredCount(matchedPreferred);
        savedReport.setTotalBonusCount(totalBonus);
        savedReport.setMatchedBonusCount(matchedBonus);
        savedReport.setWeightedCoveragePercentage(totalWeight == 0 ? 0 : (int) Math.round(100.0 * matchedWeight / totalWeight));
        savedReport.setNeedsReview(extraction.textUnavailable() || anyNeedsReview);

        return reportRepository.saveAndFlush(savedReport);
    }

    private record EvidenceExtraction(Map<UUID, SkillEvidence> evidenceBySkillId, boolean textUnavailable) {
    }

    private EvidenceExtraction extractEvidence(InternshipApplication application, java.util.Collection<Skill> catalog) {
        byte[] cvBytes;
        try {
            cvBytes = cvStorageService.read(application.getCvStorageKey());
        } catch (ResponseStatusException ex) {
            // Storage unavailable/not found is treated the same as unparseable text — the
            // candidate's evidence is simply unknown, not proof of absence.
            return new EvidenceExtraction(Map.of(), true);
        }

        String text;
        try {
            text = cvSkillExtractionService.extractText(cvBytes);
        } catch (ResponseStatusException ex) {
            return new EvidenceExtraction(Map.of(), true);
        }

        if (text == null || text.isBlank()) {
            // A structurally valid PDF with no extractable text (e.g. a scanned image) — see
            // CvSkillExtractionService's own documented behavior. Legitimately empty, but
            // screening must not read that as "confirmed no skills."
            return new EvidenceExtraction(Map.of(), true);
        }

        Map<UUID, SkillEvidence> bySkillId = new HashMap<>();
        for (SkillEvidence evidence : cvSkillExtractionService.detectSkillEvidence(text, catalog)) {
            bySkillId.put(evidence.skill().getId(), evidence);
        }
        return new EvidenceExtraction(bySkillId, false);
    }

    private record Evaluation(
            ScreeningResult result,
            com.internplatform.internprofile.EvidenceSource source,
            EvidenceStrength strength,
            String textReference,
            String reasoning
    ) {
    }

    /**
     * The actual matching rule, per requirement:
     * 1. A free-text (label-only) requirement has no canonical skill to check — always
     *    NEEDS_REVIEW; this stage introduces no NLP to guess at it.
     * 2. CV evidence (freshly extracted from the application's frozen snapshot) wins when
     *    present: MATCHED if it meets BOTH the requirement's acceptedEvidenceSources (empty
     *    = unrestricted) and minimumEvidenceStrength (null = no minimum); otherwise
     *    INSUFFICIENT_EVIDENCE, still citing the actual evidence found.
     * 3. A manually-selected skill with no CV evidence can only ever satisfy an UNRESTRICTED
     *    requirement with no minimum strength above WEAK — self-declaration is never allowed
     *    to satisfy a requirement that explicitly demands demonstrated project/work/etc.
     *    evidence. Otherwise INSUFFICIENT_EVIDENCE.
     * 4. No evidence of any kind: NEEDS_REVIEW if the CV text itself was unavailable (absence
     *    of evidence proves nothing then); otherwise a confident NOT_EVIDENCED.
     */
    private Evaluation evaluateRequirement(
            InternshipRubricRequirement requirement,
            Map<UUID, SkillEvidence> cvEvidenceBySkillId,
            Set<UUID> manualSkillIds,
            boolean cvTextUnavailable
    ) {
        UUID skillId = requirement.getSkillId();
        if (skillId == null) {
            return new Evaluation(ScreeningResult.NEEDS_REVIEW, null, null, null,
                    "Free-text requirement with no canonical skill — requires manual review");
        }

        SkillEvidence cvEvidence = cvEvidenceBySkillId.get(skillId);
        Set<com.internplatform.internprofile.EvidenceSource> acceptedSources = requirement.getAcceptedEvidenceSources();
        EvidenceStrength minimumStrength = requirement.getMinimumEvidenceStrength();

        if (cvEvidence != null) {
            boolean sourceOk = acceptedSources.isEmpty() || acceptedSources.contains(cvEvidence.source());
            boolean strengthOk = minimumStrength == null || meetsMinimum(cvEvidence.strength(), minimumStrength);

            if (sourceOk && strengthOk) {
                return new Evaluation(ScreeningResult.MATCHED, cvEvidence.source(), cvEvidence.strength(), cvEvidence.textReference(),
                        "Found in " + cvEvidence.source() + " with " + cvEvidence.strength() + " evidence");
            }
            String reason = !sourceOk
                    ? "Found only in " + cvEvidence.source() + "; this requirement needs evidence from " + acceptedSources
                    : "Evidence strength " + cvEvidence.strength() + " does not meet the required minimum " + minimumStrength;
            return new Evaluation(ScreeningResult.INSUFFICIENT_EVIDENCE, cvEvidence.source(), cvEvidence.strength(),
                    cvEvidence.textReference(), reason);
        }

        if (manualSkillIds.contains(skillId)) {
            boolean manualOk = acceptedSources.isEmpty()
                    && (minimumStrength == null || minimumStrength == EvidenceStrength.WEAK);
            if (manualOk) {
                return new Evaluation(ScreeningResult.MATCHED, null, EvidenceStrength.WEAK, null,
                        "Self-declared in the candidate's profile; not verified against CV text");
            }
            return new Evaluation(ScreeningResult.INSUFFICIENT_EVIDENCE, null, null, null,
                    "Only self-declared in profile; this requirement requires CV-verified evidence");
        }

        if (cvTextUnavailable) {
            return new Evaluation(ScreeningResult.NEEDS_REVIEW, null, null, null,
                    "CV text could not be read — absence of evidence cannot be confirmed");
        }

        return new Evaluation(ScreeningResult.NOT_EVIDENCED, null, null, null,
                "Not found in CV text or the candidate's manually selected skills");
    }

    private boolean meetsMinimum(EvidenceStrength actual, EvidenceStrength minimum) {
        return STRENGTH_ORDER.indexOf(actual) >= STRENGTH_ORDER.indexOf(minimum);
    }

    private InternshipRubric requireActiveRubric(UUID internshipId) {
        return rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This internship has no active screening rubric — activate one before screening"));
    }

    private CompanyProfile findCompanyProfileOrThrow(UUID userId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        return companyAccessResolver.findCompanyProfile(context)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private Internship findOwnedInternshipOrThrow(UUID internshipId, UUID companyProfileId) {
        return internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));
    }

    private InternshipApplication findOwnedApplicationOrThrow(UUID applicationId, UUID internshipId) {
        return applicationRepository.findByIdAndInternshipId(applicationId, internshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private ScreeningReportResponse toResponse(ScreeningReport report, List<InternshipRubricRequirement> requirements) {
        Map<UUID, InternshipRubricRequirement> requirementsById = new HashMap<>();
        Set<UUID> skillIds = new HashSet<>();
        for (InternshipRubricRequirement requirement : requirements) {
            requirementsById.put(requirement.getId(), requirement);
            if (requirement.getSkillId() != null) {
                skillIds.add(requirement.getSkillId());
            }
        }
        Map<UUID, Skill> skillsById = new HashMap<>();
        for (Skill skill : skillRepository.findAllById(skillIds)) {
            skillsById.put(skill.getId(), skill);
        }

        List<ScreeningReportResult> results = resultRepository.findAllByScreeningReportId(report.getId());
        List<ScreeningResultResponse> resultResponses = results.stream()
                .sorted(Comparator.comparing(r -> {
                    InternshipRubricRequirement req = requirementsById.get(r.getInternshipRubricRequirementId());
                    return req == null ? Integer.MAX_VALUE : req.getDisplayOrder();
                }))
                .map(result -> {
                    InternshipRubricRequirement requirement = requirementsById.get(result.getInternshipRubricRequirementId());
                    Skill skill = requirement != null && requirement.getSkillId() != null
                            ? skillsById.get(requirement.getSkillId()) : null;
                    return new ScreeningResultResponse(
                            result.getInternshipRubricRequirementId(),
                            requirement != null ? requirement.getRequirementType() : null,
                            requirement != null ? requirement.getSkillId() : null,
                            skill != null ? skill.getName() : null,
                            requirement != null ? requirement.getLabel() : null,
                            requirement != null ? requirement.getWeight() : 0,
                            result.getResult(),
                            result.getEvidenceSource(),
                            result.getEvidenceStrength(),
                            result.getTextReference(),
                            result.getReasoning()
                    );
                })
                .toList();

        return new ScreeningReportResponse(
                report.getId(),
                report.getInternshipId(),
                report.getApplicationId(),
                report.getInternshipRubricId(),
                report.getRubricVersion(),
                report.getEvaluatedAt(),
                report.getAlgorithmVersion(),
                report.isCvTextUnavailable(),
                report.isNeedsReview(),
                report.getTotalRequiredCount(),
                report.getMatchedRequiredCount(),
                report.getTotalPreferredCount(),
                report.getMatchedPreferredCount(),
                report.getTotalBonusCount(),
                report.getMatchedBonusCount(),
                report.getWeightedCoveragePercentage(),
                resultResponses
        );
    }

    private ScreeningSummaryResponse toSummary(InternshipApplication application, InternProfile profile, ScreeningReport latestReport) {
        String applicantName = applicantName(profile);
        if (latestReport == null) {
            return new ScreeningSummaryResponse(
                    application.getId(), applicantName, application.getStatus(),
                    null, null, null, null, null, null);
        }
        return new ScreeningSummaryResponse(
                application.getId(), applicantName, application.getStatus(),
                latestReport.getId(), latestReport.getEvaluatedAt(), latestReport.isNeedsReview(),
                latestReport.getWeightedCoveragePercentage(), latestReport.getMatchedRequiredCount(), latestReport.getTotalRequiredCount());
    }

    private String applicantName(InternProfile profile) {
        if (profile == null) {
            return null;
        }
        String firstName = profile.getFirstName() != null ? profile.getFirstName() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }
}
