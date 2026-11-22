package com.internplatform.ats.candidate;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.ats.pipeline.AtsStage;
import com.internplatform.ats.pipeline.AtsStageRepository;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internshipapplication.ApplicationStatus;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The "future candidate-movement phase" AtsApplicationState/AtsStageEvent were explicitly built
 * for (see their own javadocs) — a human-initiated, authorization-checked, transactional move
 * of one application to the next stage in its internship's already-assigned pipeline. Never
 * decides anything itself: it only executes a move a recruiter explicitly requested, using the
 * pipeline's own persisted stage ordering. Rejecting an applicant uses the existing
 * CompanyApplicationService.decideApplication endpoint unchanged — there is deliberately no
 * "reject" method here.
 */
@Service
public class AtsCandidateMovementService {

    private final InternshipRepository internshipRepository;
    private final InternshipApplicationRepository applicationRepository;
    private final AtsApplicationStateRepository applicationStateRepository;
    private final AtsStageEventRepository stageEventRepository;
    private final AtsStageRepository stageRepository;
    private final AtsApplicationIntakeService intakeService;
    private final CompanyAccessResolver companyAccessResolver;
    private final CandidateService candidateService;

    public AtsCandidateMovementService(
            InternshipRepository internshipRepository,
            InternshipApplicationRepository applicationRepository,
            AtsApplicationStateRepository applicationStateRepository,
            AtsStageEventRepository stageEventRepository,
            AtsStageRepository stageRepository,
            AtsApplicationIntakeService intakeService,
            CompanyAccessResolver companyAccessResolver,
            CandidateService candidateService
    ) {
        this.internshipRepository = internshipRepository;
        this.applicationRepository = applicationRepository;
        this.applicationStateRepository = applicationStateRepository;
        this.stageEventRepository = stageEventRepository;
        this.stageRepository = stageRepository;
        this.intakeService = intakeService;
        this.companyAccessResolver = companyAccessResolver;
        this.candidateService = candidateService;
    }

    /**
     * Moves one application to the next stage in its internship's pipeline, in persisted
     * position order — never a hardcoded or guessed stage. Order of checks, each a distinct,
     * clear failure mode:
     * 1. Ownership (404 — this company doesn't own this application, or it doesn't exist).
     * 2. Already decided (409 — ACCEPTED/REJECTED applications can never advance; this is the
     *    entire enforcement of "rejected applicants cannot advance," reusing the application's
     *    own existing status field rather than a new one).
     * 3. Not tracked (409 — no pipeline assigned to this internship, or the pipeline has no
     *    stages; ensureTracked is called first so a never-yet-viewed application still gets
     *    its lazy backfill before this check, matching CandidateService's own pattern).
     * 4. Current stage record missing from the pipeline's own stage list (409 — a data
     *    integrity problem, handled safely rather than thrown as an unchecked NPE).
     * 5. Already at the final stage (409).
     * Steps 6-7 (the actual move) are protected by AtsApplicationState's existing @Version
     * optimistic lock — a losing concurrent request gets a clear 409 instead of silently
     * overwriting the winner's move or corrupting state.
     */
    @Transactional
    public CandidateStageResponse advance(UUID userId, UUID applicationId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        InternshipApplication application = findOwnedApplicationOrThrow(applicationId, context.companyProfileId());

        if (application.getStatus() != ApplicationStatus.APPLIED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This application has already been decided and can no longer advance");
        }

        AtsApplicationState state = intakeService.ensureTracked(application.getId(), application.getInternshipId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This application is not currently tracked in a recruitment pipeline"));

        List<AtsStage> stages = stageRepository.findAllByPipelineIdOrderByPositionAsc(state.getPipelineId());
        AtsStage currentStage = stages.stream()
                .filter(stage -> stage.getId().equals(state.getCurrentStageId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This application's current stage could not be resolved"));

        AtsStage nextStage = stages.stream()
                .filter(stage -> stage.getPosition() > currentStage.getPosition())
                .min(Comparator.comparingInt(AtsStage::getPosition))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This applicant is already at the final stage of the pipeline"));

        UUID fromStageId = state.getCurrentStageId();
        String fromStageName = currentStage.getName();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        state.moveToStage(nextStage.getId(), now);
        try {
            applicationStateRepository.saveAndFlush(state);
        } catch (OptimisticLockingFailureException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This applicant's stage was just changed by another request — please refresh and try again");
        }

        AtsStageEvent event = new AtsStageEvent(
                application.getId(), application.getInternshipId(), context.companyProfileId(),
                fromStageId, nextStage.getId(), fromStageName, nextStage.getName(),
                StageEventKind.MOVED, context.memberId());
        stageEventRepository.saveAndFlush(event);

        return candidateService.toStageResponse(application, state);
    }

    /** Same ownership-scoped lookup pattern as CandidateService: resolve the application, then
     *  confirm its own internship belongs to the caller's company — an application belonging
     *  to another company's internship simply doesn't match and collapses to 404, never
     *  leaking whether the application id exists at all. */
    private InternshipApplication findOwnedApplicationOrThrow(UUID applicationId, UUID companyProfileId) {
        InternshipApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(this::applicationNotFound);
        internshipRepository.findByIdAndCompanyProfileId(application.getInternshipId(), companyProfileId)
                .orElseThrow(this::applicationNotFound);
        return application;
    }

    private ResponseStatusException applicationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
    }
}
