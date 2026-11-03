package com.internplatform.ats.candidate;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.ats.pipeline.AtsStage;
import com.internplatform.ats.pipeline.AtsStageRepository;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, company-scoped access to ATS candidate tracking — current stage and stage
 * history. Lazily backfills a pre-existing, still-APPLIED application into its pipeline's
 * first stage the first time it is read here (see AtsApplicationIntakeService.ensureTracked) —
 * no bulk migration against existing data is ever required. A decided (ACCEPTED/REJECTED)
 * application that predates pipeline assignment is deliberately left untracked (every stage
 * field null) rather than guessed into any stage.
 *
 * Deliberately does not implement stage movement — this phase is read-only tracking, the
 * foundation for the future candidate-movement phase.
 */
@Service
public class CandidateService {

    private final InternshipRepository internshipRepository;
    private final InternshipApplicationRepository applicationRepository;
    private final AtsStageEventRepository stageEventRepository;
    private final AtsStageRepository stageRepository;
    private final AtsApplicationIntakeService intakeService;
    private final CompanyAccessResolver companyAccessResolver;

    public CandidateService(
            InternshipRepository internshipRepository,
            InternshipApplicationRepository applicationRepository,
            AtsStageEventRepository stageEventRepository,
            AtsStageRepository stageRepository,
            AtsApplicationIntakeService intakeService,
            CompanyAccessResolver companyAccessResolver
    ) {
        this.internshipRepository = internshipRepository;
        this.applicationRepository = applicationRepository;
        this.stageEventRepository = stageEventRepository;
        this.stageRepository = stageRepository;
        this.intakeService = intakeService;
        this.companyAccessResolver = companyAccessResolver;
    }

    /** Every application for this internship, newest first (matching
     *  InternshipApplicationRepository's own ordering contract), each annotated with its
     *  current ATS stage — null when untracked. */
    @Transactional
    public List<CandidateStageResponse> listCandidates(UUID userId, UUID internshipId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, context.companyProfileId());

        return applicationRepository.findAllByInternshipIdOrderByAppliedAtDesc(internship.getId()).stream()
                .map(application -> toStageResponse(
                        application.getId(),
                        intakeService.ensureTracked(application.getId(), internship.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public CandidateStageResponse getCandidateStage(UUID userId, UUID applicationId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        InternshipApplication application = findOwnedApplicationOrThrow(applicationId, context.companyProfileId());

        return toStageResponse(
                application.getId(),
                intakeService.ensureTracked(application.getId(), application.getInternshipId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<StageEventResponse> getCandidateHistory(UUID userId, UUID applicationId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        InternshipApplication application = findOwnedApplicationOrThrow(applicationId, context.companyProfileId());

        return stageEventRepository.findAllByApplicationIdOrderByOccurredAtAsc(application.getId()).stream()
                .map(this::toEventResponse)
                .toList();
    }

    /** Ownership-scoped lookup: resolves the application's own internship under the caller's
     *  company, so an application belonging to another company's internship simply doesn't
     *  match and collapses to 404 — the same not-found-not-forbidden pattern
     *  CompanyApplicationService already uses. */
    private InternshipApplication findOwnedApplicationOrThrow(UUID applicationId, UUID companyProfileId) {
        InternshipApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(this::applicationNotFound);
        internshipRepository.findByIdAndCompanyProfileId(application.getInternshipId(), companyProfileId)
                .orElseThrow(this::applicationNotFound);
        return application;
    }

    private Internship findOwnedInternshipOrThrow(UUID internshipId, UUID companyProfileId) {
        return internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));
    }

    private ResponseStatusException applicationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
    }

    private CandidateStageResponse toStageResponse(UUID applicationId, AtsApplicationState state) {
        if (state == null) {
            return new CandidateStageResponse(applicationId, null, null, null);
        }
        String stageName = stageRepository.findById(state.getCurrentStageId()).map(AtsStage::getName).orElse(null);
        return new CandidateStageResponse(
                applicationId, state.getCurrentStageId(), stageName, state.getEnteredCurrentStageAt());
    }

    private StageEventResponse toEventResponse(AtsStageEvent event) {
        return new StageEventResponse(
                event.getId(),
                event.getFromStageId(),
                event.getFromStageName(),
                event.getToStageId(),
                event.getToStageName(),
                event.getEventKind(),
                event.getActorMemberId(),
                event.getOccurredAt());
    }
}
