package com.internplatform.ats.pipeline;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.ats.template.AtsPipelineTemplate;
import com.internplatform.ats.template.AtsPipelineTemplateRepository;
import com.internplatform.ats.template.AtsTemplateStage;
import com.internplatform.ats.template.AtsTemplateStageRepository;
import com.internplatform.ats.template.PipelineTemplateService;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An internship's own, independent recruitment pipeline — assigned once from a company
 * AtsPipelineTemplate (or from the hardcoded starter), then freely customizable afterward
 * without ever affecting the source template or any other internship (see assignPipeline for
 * the deep-copy that makes this independence structural, not just a convention).
 *
 * Viewing is available to any ACTIVE company member; assigning and every stage mutation is
 * OWNER-only, matching PipelineTemplateService's split. All of it is approval-gated via
 * CompanyAccessResolver, matching InternshipService/CompanyApplicationService.
 *
 * Deliberately does not touch internship_applications or any candidate/application-status
 * concept — this phase is pipeline structure only. ACCEPTED/REJECTED/WITHDRAWN remain
 * exclusively internship_applications.status values; no stage here ever represents one.
 */
@Service
public class InternshipPipelineService {

    private final AtsInternshipPipelineRepository pipelineRepository;
    private final AtsStageRepository stageRepository;
    private final InternshipRepository internshipRepository;
    private final AtsPipelineTemplateRepository templateRepository;
    private final AtsTemplateStageRepository templateStageRepository;
    private final CompanyAccessResolver companyAccessResolver;

    public InternshipPipelineService(
            AtsInternshipPipelineRepository pipelineRepository,
            AtsStageRepository stageRepository,
            InternshipRepository internshipRepository,
            AtsPipelineTemplateRepository templateRepository,
            AtsTemplateStageRepository templateStageRepository,
            CompanyAccessResolver companyAccessResolver
    ) {
        this.pipelineRepository = pipelineRepository;
        this.stageRepository = stageRepository;
        this.internshipRepository = internshipRepository;
        this.templateRepository = templateRepository;
        this.templateStageRepository = templateStageRepository;
        this.companyAccessResolver = companyAccessResolver;
    }

    /** 404 both when the internship doesn't belong to this company and when it does but has no
     *  pipeline assigned yet — the same "not created yet" convention GET /company/profile uses
     *  before a profile exists, so clients coerce a 404 here to "no pipeline configured" rather
     *  than treating it as an error. */
    @Transactional(readOnly = true)
    public InternshipPipelineResponse getPipeline(UUID userId, UUID internshipId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, context.companyProfileId());

        AtsInternshipPipeline pipeline = pipelineRepository.findByInternshipId(internship.getId())
                .orElseThrow(this::pipelineNotFound);

        return toResponse(pipeline, stagesOf(pipeline.getId()));
    }

    /**
     * Materializes this internship's pipeline. Resolution order when templateId is omitted:
     * the company's own marked-default template, else the hardcoded starter (Applied ->
     * Screening -> Interview -> Offer). Either way, every stage is copied into a brand-new
     * AtsStage row — physically independent of the source from the moment this method
     * returns, so a later edit to the template (rename/reorder/delete a stage, even archiving
     * it entirely) can never alter this pipeline. One pipeline per internship: a second call
     * for the same internship is rejected with 409 rather than silently replacing it.
     */
    @Transactional
    public InternshipPipelineResponse assignPipeline(UUID userId, UUID internshipId, AssignPipelineRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, context.companyProfileId());

        if (pipelineRepository.existsByInternshipId(internship.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This internship already has a pipeline assigned");
        }

        ResolvedSource source = resolveSource(context.companyProfileId(), request.templateId());

        AtsInternshipPipeline pipeline = new AtsInternshipPipeline(internship.getId(), context.companyProfileId());
        pipeline.setSourceTemplateId(source.templateId());
        pipeline.setSourceTemplateName(source.templateName());
        AtsInternshipPipeline savedPipeline = pipelineRepository.saveAndFlush(pipeline);

        List<AtsStage> stages = new ArrayList<>();
        int position = 0;
        for (String stageName : source.stageNames()) {
            stages.add(new AtsStage(savedPipeline.getId(), stageName, position));
            position++;
        }
        stageRepository.saveAll(stages);

        return toResponse(savedPipeline, stagesOf(savedPipeline.getId()));
    }

    @Transactional
    public PipelineStageResponse addStage(UUID userId, UUID internshipId, PipelineStageCreateRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsInternshipPipeline pipeline = findOwnedPipelineOrThrow(internshipId, context);

        int nextPosition = (int) stageRepository.countByPipelineId(pipeline.getId());
        AtsStage stage = new AtsStage(pipeline.getId(), request.name().trim(), nextPosition);
        AtsStage saved = stageRepository.saveAndFlush(stage);

        return toStageResponse(saved);
    }

    @Transactional
    public PipelineStageResponse updateStage(
            UUID userId, UUID internshipId, UUID stageId, PipelineStageUpdateRequest request
    ) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsInternshipPipeline pipeline = findOwnedPipelineOrThrow(internshipId, context);
        AtsStage stage = findOwnedStageOrThrow(stageId, pipeline.getId());

        stage.setName(request.name().trim());
        AtsStage saved = stageRepository.saveAndFlush(stage);

        return toStageResponse(saved);
    }

    /** A pipeline must always have at least one stage. No candidate-occupancy check exists yet
     *  in this phase (no candidate-to-stage table) — safe candidate migration on stage removal
     *  is deferred to the candidate-movement phase. Remaining stages are repositioned to stay
     *  a dense, gap-free 0..n-1 sequence. */
    @Transactional
    public void deleteStage(UUID userId, UUID internshipId, UUID stageId) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsInternshipPipeline pipeline = findOwnedPipelineOrThrow(internshipId, context);
        AtsStage stage = findOwnedStageOrThrow(stageId, pipeline.getId());

        List<AtsStage> remaining = stageRepository.findAllByPipelineIdOrderByPositionAsc(pipeline.getId()).stream()
                .filter(existing -> !existing.getId().equals(stageId))
                .toList();

        if (remaining.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A pipeline must have at least one stage");
        }

        stageRepository.delete(stage);

        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        stageRepository.saveAll(remaining);
    }

    @Transactional
    public List<PipelineStageResponse> reorderStages(UUID userId, UUID internshipId, PipelineStageReorderRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsInternshipPipeline pipeline = findOwnedPipelineOrThrow(internshipId, context);

        List<AtsStage> current = stageRepository.findAllByPipelineIdOrderByPositionAsc(pipeline.getId());
        List<AtsStage> reordered = applyPermutation(current, request.stageIds());

        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setPosition(i);
        }
        stageRepository.saveAll(reordered);

        return reordered.stream().map(this::toStageResponse).toList();
    }

    // ===== helpers =====

    private record ResolvedSource(UUID templateId, String templateName, List<String> stageNames) {
    }

    /**
     * templateId explicitly given: must be this company's own, non-archived template (400/404
     * otherwise). Omitted: the company's marked default template if one exists and is not
     * archived, else the hardcoded starter — never an error, so every company can always get a
     * working pipeline on an internship with zero setup.
     */
    private ResolvedSource resolveSource(UUID companyProfileId, UUID requestedTemplateId) {
        if (requestedTemplateId != null) {
            AtsPipelineTemplate template = templateRepository
                    .findByIdAndCompanyProfileId(requestedTemplateId, companyProfileId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));

            if (template.isArchived()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Cannot assign an archived template to an internship");
            }

            List<String> names = templateStageRepository.findAllByTemplateIdOrderByPositionAsc(template.getId())
                    .stream()
                    .sorted(Comparator.comparingInt(AtsTemplateStage::getPosition))
                    .map(AtsTemplateStage::getName)
                    .toList();

            return new ResolvedSource(template.getId(), template.getName(), names);
        }

        return templateRepository
                .findByCompanyProfileIdAndDefaultTemplateTrueAndArchivedAtIsNull(companyProfileId)
                .map(defaultTemplate -> {
                    List<String> names = templateStageRepository
                            .findAllByTemplateIdOrderByPositionAsc(defaultTemplate.getId())
                            .stream()
                            .sorted(Comparator.comparingInt(AtsTemplateStage::getPosition))
                            .map(AtsTemplateStage::getName)
                            .toList();
                    return new ResolvedSource(defaultTemplate.getId(), defaultTemplate.getName(), names);
                })
                .orElseGet(() -> new ResolvedSource(null, "Default Pipeline", PipelineTemplateService.starterStageNames()));
    }

    /** Validates that requestedIds is an exact permutation of current's ids and returns
     *  current's stages reordered to match — rejecting anything that adds, drops, or
     *  duplicates a stage id rather than guessing intent. */
    private List<AtsStage> applyPermutation(List<AtsStage> current, List<UUID> requestedIds) {
        Set<UUID> requestedSet = new HashSet<>(requestedIds);
        if (requestedSet.size() != requestedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stageIds contains a duplicate id");
        }

        Set<UUID> currentIds = new HashSet<>();
        current.forEach(stage -> currentIds.add(stage.getId()));

        if (!requestedSet.equals(currentIds)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "stageIds must be an exact permutation of the current stage ids");
        }

        return requestedIds.stream()
                .map(id -> current.stream().filter(stage -> stage.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    private AtsInternshipPipeline findOwnedPipelineOrThrow(UUID internshipId, CompanyContext context) {
        Internship internship = findOwnedInternshipOrThrow(internshipId, context.companyProfileId());
        return pipelineRepository.findByInternshipId(internship.getId())
                .orElseThrow(this::pipelineNotFound);
    }

    private Internship findOwnedInternshipOrThrow(UUID internshipId, UUID companyProfileId) {
        return internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));
    }

    private AtsStage findOwnedStageOrThrow(UUID stageId, UUID pipelineId) {
        return stageRepository.findByIdAndPipelineId(stageId, pipelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stage not found"));
    }

    private ResponseStatusException pipelineNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No pipeline configured for this internship yet");
    }

    private List<AtsStage> stagesOf(UUID pipelineId) {
        return stageRepository.findAllByPipelineIdOrderByPositionAsc(pipelineId);
    }

    private PipelineStageResponse toStageResponse(AtsStage stage) {
        return new PipelineStageResponse(stage.getId(), stage.getName(), stage.getPosition());
    }

    private InternshipPipelineResponse toResponse(AtsInternshipPipeline pipeline, List<AtsStage> stages) {
        List<PipelineStageResponse> stageResponses = stages.stream()
                .sorted(Comparator.comparingInt(AtsStage::getPosition))
                .map(this::toStageResponse)
                .toList();

        return new InternshipPipelineResponse(
                pipeline.getId(),
                pipeline.getInternshipId(),
                pipeline.getCompanyProfileId(),
                pipeline.getSourceTemplateId(),
                pipeline.getSourceTemplateName(),
                stageResponses,
                pipeline.getVersion(),
                pipeline.getCreatedAt(),
                pipeline.getUpdatedAt()
        );
    }
}
