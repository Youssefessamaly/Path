package com.internplatform.ats.candidate;

import com.internplatform.ats.pipeline.AtsInternshipPipeline;
import com.internplatform.ats.pipeline.AtsInternshipPipelineRepository;
import com.internplatform.ats.pipeline.AtsStage;
import com.internplatform.ats.pipeline.AtsStageRepository;
import com.internplatform.companyprofile.ApprovalStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.ApplicationMethod;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import com.internplatform.internship.InternshipStatus;
import com.internplatform.internship.WorkMode;
import com.internplatform.internshipapplication.ApplicationStatus;
import com.internplatform.internshipapplication.InternshipApplication;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct, service-level tests of the actual intake/backfill logic — deliberately NOT exercised
 * through the Spring event/AFTER_COMMIT wiring here (that integration is covered separately in
 * CandidateControllerTest, which must force a real commit to observe it — a plain
 * @Transactional rollback-based test, like every other test in this suite, never triggers an
 * AFTER_COMMIT listener at all). Calling ensureTracked directly exercises exactly the same code
 * a real submission's event listener calls.
 *
 * ensureTracked itself runs with plain (REQUIRED) propagation, so for every test here except
 * the concurrency one below it simply joins this test method's own @Transactional transaction —
 * every row it creates is rolled back automatically at the end of each test, same as any other
 * test in this suite. The one exception is the true-concurrency test, which needs its fixture
 * data visible to independent background threads (each with no ambient transaction of its own);
 * it forces a real commit via TestTransaction for that reason alone, and explicitly cleans up
 * everything it created afterward.
 */
@SpringBootTest
@Transactional
class AtsApplicationIntakeServiceTest {

    @Autowired
    private AtsApplicationIntakeService intakeService;

    @Autowired
    private AtsApplicationStateRepository applicationStateRepository;

    @Autowired
    private AtsStageEventRepository stageEventRepository;

    @Autowired
    private AtsInternshipPipelineRepository pipelineRepository;

    @Autowired
    private AtsStageRepository stageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private InternProfileRepository internProfileRepository;

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private InternshipApplicationRepository applicationRepository;

    @Autowired
    private SkillRepository skillRepository;

    private User createUser(Role role) {
        return userRepository.saveAndFlush(new User(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password123!"),
                role));
    }

    private CompanyProfile createCompanyProfile(User owner) {
        CompanyProfile profile = new CompanyProfile(owner.getId());
        profile.setCompanyName("Pathora Technologies");
        profile.markOnboardingCompleted(OffsetDateTime.now(ZoneOffset.UTC));
        profile.recordApprovalDecision(ApprovalStatus.APPROVED, OffsetDateTime.now(ZoneOffset.UTC));
        return companyProfileRepository.saveAndFlush(profile);
    }

    private Internship createInternship(CompanyProfile owner) {
        Internship internship = new Internship(owner.getId());
        internship.setTitle("Backend Engineering Intern");
        internship.setDescription("Work on our core platform.");
        internship.setLocation("Cairo, Egypt");
        internship.setWorkMode(WorkMode.HYBRID);
        internship.setApplicationDeadline(LocalDate.now(ZoneOffset.UTC).plusDays(30));
        internship.setStatus(InternshipStatus.OPEN);
        internship.setSkills(Set.of(skillRepository.findAllByOrderByNameAsc().get(0)));
        internship.setTargetMajors(Set.of());
        internship.setApplicationMethod(ApplicationMethod.INTERNAL);
        return internshipRepository.saveAndFlush(internship);
    }

    private InternProfile createInternProfile() {
        User internUser = createUser(Role.INTERN);
        InternProfile profile = new InternProfile(internUser.getId());
        profile.setFirstName("Amina");
        profile.setLastName("Hassan");
        return internProfileRepository.saveAndFlush(profile);
    }

    private InternshipApplication createApplication(Internship internship, ApplicationStatus status) {
        InternProfile internProfile = createInternProfile();
        InternshipApplication application = new InternshipApplication(internship.getId(), internProfile.getId());
        application.setCvFileName("resume.pdf");
        application.setCvStorageKey(UUID.randomUUID() + "/resume.pdf");
        application.setCvContentType("application/pdf");
        application.setCvSizeBytes(1024);
        application.setCvUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        InternshipApplication saved = applicationRepository.saveAndFlush(application);
        if (status != ApplicationStatus.APPLIED) {
            saved.setStatus(status);
            saved = applicationRepository.saveAndFlush(saved);
        }
        return saved;
    }

    /** Deep-copies stage NAMES into a brand-new pipeline — independent of the template
     *  package's own materialization logic, since this test only needs a pipeline with known
     *  stages, not template-assignment behavior itself (already covered elsewhere). */
    private AtsInternshipPipeline createPipelineWithStages(Internship internship, String... stageNames) {
        AtsInternshipPipeline pipeline = new AtsInternshipPipeline(internship.getId(), internship.getCompanyProfileId());
        AtsInternshipPipeline saved = pipelineRepository.saveAndFlush(pipeline);
        for (int i = 0; i < stageNames.length; i++) {
            stageRepository.saveAndFlush(new AtsStage(saved.getId(), stageNames[i], i));
        }
        return saved;
    }

    @Test
    void newApplicationEntersTheFirstStageOfTheAssignedPipeline() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        AtsInternshipPipeline pipeline = createPipelineWithStages(internship, "Applied", "Screening", "Interview");
        AtsStage firstStage = stageRepository.findAllByPipelineIdOrderByPositionAsc(pipeline.getId()).get(0);

        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        Optional<AtsApplicationState> result = intakeService.ensureTracked(application.getId(), internship.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getCurrentStageId()).isEqualTo(firstStage.getId());
        assertThat(result.get().getCompanyProfileId()).isEqualTo(company.getId());
        assertThat(result.get().getPipelineId()).isEqualTo(pipeline.getId());

        List<AtsStageEvent> events = stageEventRepository.findAllByApplicationIdOrderByOccurredAtAsc(application.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFromStageId()).isNull();
        assertThat(events.get(0).getToStageId()).isEqualTo(firstStage.getId());
        assertThat(events.get(0).getToStageName()).isEqualTo("Applied");
        assertThat(events.get(0).getEventKind()).isEqualTo(StageEventKind.ENTERED);
        assertThat(events.get(0).getActorMemberId()).isNull();
    }

    @Test
    void callingEnsureTrackedTwiceIsIdempotent() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Interview");
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        Optional<AtsApplicationState> first = intakeService.ensureTracked(application.getId(), internship.getId());
        Optional<AtsApplicationState> second = intakeService.ensureTracked(application.getId(), internship.getId());

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().getCurrentStageId()).isEqualTo(first.get().getCurrentStageId());

        assertThat(applicationStateRepository.findById(application.getId())).isPresent();
        assertThat(stageEventRepository.findAllByApplicationIdOrderByOccurredAtAsc(application.getId())).hasSize(1);
    }

    @Test
    void applicationIsNotTrackedWhenInternshipHasNoAssignedPipeline() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        // Deliberately no pipeline created for this internship.
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        Optional<AtsApplicationState> result = intakeService.ensureTracked(application.getId(), internship.getId());

        assertThat(result).isEmpty();
        assertThat(applicationStateRepository.findById(application.getId())).isEmpty();
        assertThat(stageEventRepository.findAllByApplicationIdOrderByOccurredAtAsc(application.getId())).isEmpty();
    }

    @Test
    void decidedApplicationIsNeverGuessedIntoAStageEvenWhenAPipelineExists() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Interview", "Offer");

        // This application was already decided BEFORE ever being tracked — simulating a
        // pre-existing application from before pipeline assignment (Phase 3 Step 4).
        InternshipApplication acceptedApplication = createApplication(internship, ApplicationStatus.ACCEPTED);
        InternshipApplication rejectedApplication = createApplication(internship, ApplicationStatus.REJECTED);

        Optional<AtsApplicationState> acceptedResult =
                intakeService.ensureTracked(acceptedApplication.getId(), internship.getId());
        Optional<AtsApplicationState> rejectedResult =
                intakeService.ensureTracked(rejectedApplication.getId(), internship.getId());

        assertThat(acceptedResult).isEmpty();
        assertThat(rejectedResult).isEmpty();
        assertThat(applicationStateRepository.findById(acceptedApplication.getId())).isEmpty();
        assertThat(applicationStateRepository.findById(rejectedApplication.getId())).isEmpty();
    }

    @Test
    void alreadyTrackedApplicationKeepsItsStageEvenAfterBeingDecidedLater() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Interview");
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        Optional<AtsApplicationState> whileApplied = intakeService.ensureTracked(application.getId(), internship.getId());
        assertThat(whileApplied).isPresent();
        UUID stageWhileApplied = whileApplied.get().getCurrentStageId();

        // Decide the application via the existing, untouched status field — exactly as the
        // real company-decision endpoint would.
        application.setStatus(ApplicationStatus.ACCEPTED);
        applicationRepository.saveAndFlush(application);

        Optional<AtsApplicationState> afterDecision = intakeService.ensureTracked(application.getId(), internship.getId());

        assertThat(afterDecision).isPresent();
        assertThat(afterDecision.get().getCurrentStageId()).isEqualTo(stageWhileApplied);
        assertThat(stageEventRepository.findAllByApplicationIdOrderByOccurredAtAsc(application.getId())).hasSize(1);
    }

    @Test
    void pipelineWithNoStagesIsHandledSafelyAndNeverThrows() {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        // A pipeline with zero stages should not occur in practice (every materialized
        // pipeline always gets at least one), but ensureTracked must still handle it safely.
        pipelineRepository.saveAndFlush(new AtsInternshipPipeline(internship.getId(), company.getId()));
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);

        Optional<AtsApplicationState> result = intakeService.ensureTracked(application.getId(), internship.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void concurrentIntakeAttemptsForTheSameApplicationNeverCreateDuplicateRecords() throws Exception {
        User owner = createUser(Role.COMPANY);
        CompanyProfile company = createCompanyProfile(owner);
        Internship internship = createInternship(company);
        createPipelineWithStages(internship, "Applied", "Interview");
        InternshipApplication application = createApplication(internship, ApplicationStatus.APPLIED);
        UUID internUserId = internProfileRepository.findById(application.getInternProfileId())
                .orElseThrow()
                .getUserId();

        // The 8 threads below run with no ambient transaction of their own, so they can only
        // ever see fixture data that has actually been committed — force a real commit here
        // (same technique CandidateControllerTest's end-to-end test uses) so the concurrent
        // ensureTracked calls can see the company/internship/pipeline/application above.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<java.util.concurrent.Future<Optional<AtsApplicationState>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return intakeService.ensureTracked(application.getId(), internship.getId());
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (java.util.concurrent.Future<Optional<AtsApplicationState>> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isPresent();
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        // Exactly one tracking row and one history event, regardless of how many concurrent
        // callers raced to create it — the application_id PRIMARY KEY is the real guarantee.
        TestTransaction.start();
        assertThat(applicationStateRepository.findById(application.getId())).isPresent();
        assertThat(stageEventRepository.findAllByApplicationIdOrderByOccurredAtAsc(application.getId())).hasSize(1);

        // This test forced a real commit above, so — unlike every other test in this class —
        // nothing here is rolled back automatically. Explicitly delete both top-level users;
        // every row this test created (company, internship, pipeline, stages, application,
        // ATS state, stage event) cascades away from those two deletes.
        userRepository.deleteById(owner.getId());
        userRepository.deleteById(internUserId);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }
}
