package com.internplatform.ats.candidate;

import com.internplatform.ats.pipeline.AtsInternshipPipeline;
import com.internplatform.ats.pipeline.AtsInternshipPipelineRepository;
import com.internplatform.ats.pipeline.AtsStage;
import com.internplatform.ats.pipeline.AtsStageRepository;
import com.internplatform.internshipapplication.ApplicationStatus;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The first operational ATS feature: connecting a submitted application to its internship's
 * recruitment pipeline and placing it in the first stage.
 *
 * {@link #onApplicationSubmitted} is a {@code @TransactionalEventListener(AFTER_COMMIT)} — it
 * only ever runs once the triggering application's own transaction has already fully committed,
 * and by then that transaction no longer exists at all. This is not a style preference:
 * Postgres aborts an entire transaction on any failed statement within it, so a try/catch
 * around a same-transaction intake attempt would NOT actually protect the student's application
 * from an unexpected ATS failure — the whole transaction, application included, would still
 * roll back. Running strictly after commit is the only way intake can fail for any reason
 * without ever being able to lose or roll back an application the student-facing API has
 * already accepted. {@code @Transactional(REQUIRES_NEW)} is declared directly on the listener
 * method itself (not inherited from calling {@link #ensureTracked}) because the listener
 * invokes it via plain self-invocation, which bypasses Spring's proxy-based transaction
 * interception entirely — without its own annotation, the listener's work would run with no
 * transaction at all. REQUIRES_NEW specifically (rather than plain REQUIRED) is what Spring's
 * {@code @TransactionalEventListener} requires here — AFTER_COMMIT means nothing is ever active
 * to join, so Spring insists on an explicit REQUIRES_NEW or NOT_SUPPORTED rather than silently
 * allowing REQUIRED to behave identically by accident.
 *
 * {@link #ensureTracked} is idempotent by construction: ats_application_states.application_id
 * is a PRIMARY KEY, so a retried or concurrent intake attempt for the same application can
 * never create a second row. It joins whatever transaction its caller is already running in
 * (the listener's own REQUIRES_NEW transaction above, CandidateService's transaction, or a
 * test's) — plain REQUIRED propagation, same as any ordinary read. The same method also powers
 * the lazy backfill of pre-existing applications (see CandidateService) — there is exactly one
 * code path that ever creates a tracking row.
 *
 * The actual insert uses {@code INSERT ... ON CONFLICT (application_id) DO NOTHING} (see
 * {@link AtsApplicationStateRepository#insertIfAbsent}) rather than a plain JPA save caught for
 * a primary-key violation. That distinction matters specifically because this method joins its
 * caller's own transaction: Postgres aborts an entire transaction on any failed statement, so a
 * caught constraint-violation exception here would still leave the caller's whole transaction
 * unusable for anything afterward (including the fallback lookup below) — exactly the failure
 * mode this design avoids. ON CONFLICT DO NOTHING never raises an error for a losing race; it
 * simply reports zero affected rows, so the transaction stays perfectly healthy. The affected-
 * row count is also how a winner is distinguished from a loser, which decides whether this call
 * is the one that should additionally record the corresponding ENTERED stage event.
 */
@Service
public class AtsApplicationIntakeService {

    private static final Logger log = LoggerFactory.getLogger(AtsApplicationIntakeService.class);

    private final AtsApplicationStateRepository applicationStateRepository;
    private final AtsStageEventRepository stageEventRepository;
    private final AtsInternshipPipelineRepository pipelineRepository;
    private final AtsStageRepository stageRepository;
    private final InternshipApplicationRepository applicationRepository;

    public AtsApplicationIntakeService(
            AtsApplicationStateRepository applicationStateRepository,
            AtsStageEventRepository stageEventRepository,
            AtsInternshipPipelineRepository pipelineRepository,
            AtsStageRepository stageRepository,
            InternshipApplicationRepository applicationRepository
    ) {
        this.applicationStateRepository = applicationStateRepository;
        this.stageEventRepository = stageEventRepository;
        this.pipelineRepository = pipelineRepository;
        this.stageRepository = stageRepository;
        this.applicationRepository = applicationRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        try {
            ensureTracked(event.applicationId(), event.internshipId());
        } catch (Exception exception) {
            // Never allow an ATS intake failure to surface to the student — the application
            // itself already committed successfully before this listener ever ran. A missing
            // or invalid pipeline configuration is an expected, silent no-op (see below); this
            // catch is only a backstop against a genuinely unexpected failure.
            log.warn(
                    "ATS intake failed for application {} (internship {}): {}",
                    event.applicationId(), event.internshipId(), exception.getMessage());
        }
    }

    /**
     * Idempotently ensures the given application is tracked in its internship's pipeline, if
     * one is assigned. A no-op (never an error) when: the application is already tracked (its
     * existing state is returned unchanged, regardless of what its status has since become),
     * the internship has no pipeline assigned, the pipeline has no stages (defensive only —
     * every materialized pipeline always has at least one), or the application is not — and
     * was never previously — in APPLIED status. That last rule is what makes this safe to call
     * for a pre-existing, already-decided (ACCEPTED/REJECTED) application: it is deliberately
     * left untracked rather than guessed into the first (or any) stage, since a decided
     * application has already moved past whatever the pipeline's stages represent.
     */
    @Transactional
    public Optional<AtsApplicationState> ensureTracked(UUID applicationId, UUID internshipId) {
        Optional<AtsApplicationState> existing = applicationStateRepository.findById(applicationId);
        if (existing.isPresent()) {
            return existing;
        }

        InternshipApplication application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null || application.getStatus() != ApplicationStatus.APPLIED) {
            return Optional.empty();
        }

        Optional<AtsInternshipPipeline> pipelineOpt = pipelineRepository.findByInternshipId(internshipId);
        if (pipelineOpt.isEmpty()) {
            return Optional.empty();
        }
        AtsInternshipPipeline pipeline = pipelineOpt.get();

        List<AtsStage> stages = stageRepository.findAllByPipelineIdOrderByPositionAsc(pipeline.getId());
        if (stages.isEmpty()) {
            log.warn("Pipeline {} for internship {} has no stages — skipping intake for application {}",
                    pipeline.getId(), internshipId, applicationId);
            return Optional.empty();
        }
        AtsStage firstStage = stages.stream().min(Comparator.comparingInt(AtsStage::getPosition)).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        int inserted = applicationStateRepository.insertIfAbsent(
                applicationId, internshipId, pipeline.getCompanyProfileId(), pipeline.getId(), firstStage.getId(), now);
        if (inserted == 1) {
            // We won the race (or there was no race at all) — we are the one responsible for
            // recording the ENTERED event. A concurrent loser (inserted == 0) must not also
            // record one: the winner's event already represents this application's entry.
            AtsStageEvent event = new AtsStageEvent(
                    applicationId, internshipId, pipeline.getCompanyProfileId(),
                    null, firstStage.getId(), null, firstStage.getName(),
                    StageEventKind.ENTERED, null);
            stageEventRepository.saveAndFlush(event);
        }

        return applicationStateRepository.findById(applicationId);
    }
}
