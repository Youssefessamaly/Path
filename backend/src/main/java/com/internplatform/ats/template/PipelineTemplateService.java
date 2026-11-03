package com.internplatform.ats.template;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Company-owned CRUD for reusable recruitment pipeline templates. Viewing (list/get) is
 * available to any ACTIVE company member; every mutation (create, rename, duplicate, archive,
 * and all stage operations) is OWNER-only — see CompanyAccessResolver.requireOwner. All of this
 * is gated behind company approval (requireOwner/requireActiveMember both enforce it), matching
 * how InternshipService and CompanyApplicationService already gate marketplace features.
 */
@Service
public class PipelineTemplateService {

    private static final List<String> STARTER_STAGE_NAMES = List.of("Applied", "Screening", "Interview", "Offer");

    private final AtsPipelineTemplateRepository templateRepository;
    private final AtsTemplateStageRepository stageRepository;
    private final CompanyAccessResolver companyAccessResolver;

    public PipelineTemplateService(
            AtsPipelineTemplateRepository templateRepository,
            AtsTemplateStageRepository stageRepository,
            CompanyAccessResolver companyAccessResolver
    ) {
        this.templateRepository = templateRepository;
        this.stageRepository = stageRepository;
        this.companyAccessResolver = companyAccessResolver;
    }

    @Transactional
    public PipelineTemplateResponse createTemplate(UUID userId, PipelineTemplateCreateRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);

        AtsPipelineTemplate template = new AtsPipelineTemplate(context.companyProfileId(), request.name().trim());
        template.setDescription(normalizeBlankToNull(request.description()));

        AtsPipelineTemplate saved = templateRepository.saveAndFlush(template);
        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public List<PipelineTemplateResponse> listTemplates(UUID userId, boolean includeArchived) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);

        return templateRepository.findAllByCompanyProfileIdOrderByCreatedAtAsc(context.companyProfileId()).stream()
                .filter(template -> includeArchived || !template.isArchived())
                .map(template -> toResponse(template, stagesOf(template.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineTemplateResponse getTemplate(UUID userId, UUID templateId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());
        return toResponse(template, stagesOf(template.getId()));
    }

    /**
     * Partial update. Setting isDefault=true first clears any other template this company
     * currently has marked default (at most one may be default at a time — also enforced by
     * the DB's partial unique index as a backstop).
     */
    @Transactional
    public PipelineTemplateResponse updateTemplate(UUID userId, UUID templateId, PipelineTemplateUpdateRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());

        if (template.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An archived template cannot be edited");
        }

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template name cannot be blank");
            }
            template.setName(trimmed);
        }

        if (request.description() != null) {
            template.setDescription(normalizeBlankToNull(request.description()));
        }

        if (request.isDefault() != null) {
            if (request.isDefault()) {
                clearExistingDefault(context.companyProfileId(), template.getId());
                template.setDefaultTemplate(true);
            } else {
                template.setDefaultTemplate(false);
            }
        }

        AtsPipelineTemplate saved = saveWithOptimisticLockCheck(template, "template");
        return toResponse(saved, stagesOf(saved.getId()));
    }

    /** Deep-copies the template and all of its stages into a brand-new, fully independent
     *  template — never the default, never archived, regardless of the original's state. */
    @Transactional
    public PipelineTemplateResponse duplicateTemplate(UUID userId, UUID templateId) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate original = findOwnedTemplateOrThrow(templateId, context.companyProfileId());

        AtsPipelineTemplate copy = new AtsPipelineTemplate(context.companyProfileId(), original.getName() + " (Copy)");
        copy.setDescription(original.getDescription());
        AtsPipelineTemplate savedCopy = templateRepository.saveAndFlush(copy);

        List<AtsTemplateStage> copiedStages = stageRepository.findAllByTemplateIdOrderByPositionAsc(original.getId())
                .stream()
                .map(stage -> new AtsTemplateStage(savedCopy.getId(), stage.getName(), stage.getPosition()))
                .toList();
        stageRepository.saveAll(copiedStages);

        return toResponse(savedCopy, stagesOf(savedCopy.getId()));
    }

    /** Archiving never deletes the template or its stages — past internship pipelines that
     *  snapshot this template's name remain meaningful. An archived template is simply no
     *  longer assignable to new internships and stops being "the" default. */
    @Transactional
    public PipelineTemplateResponse archiveTemplate(UUID userId, UUID templateId) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());

        if (!template.isArchived()) {
            template.archive(OffsetDateTime.now(ZoneOffset.UTC));
            template = saveWithOptimisticLockCheck(template, "template");
        }

        return toResponse(template, stagesOf(template.getId()));
    }

    @Transactional
    public TemplateStageResponse addStage(UUID userId, UUID templateId, TemplateStageCreateRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());
        requireNotArchived(template);

        int nextPosition = (int) stageRepository.countByTemplateId(template.getId());
        AtsTemplateStage stage = new AtsTemplateStage(template.getId(), request.name().trim(), nextPosition);
        AtsTemplateStage saved = stageRepository.saveAndFlush(stage);

        return toStageResponse(saved);
    }

    @Transactional
    public TemplateStageResponse updateStage(UUID userId, UUID templateId, UUID stageId, TemplateStageUpdateRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());
        requireNotArchived(template);
        AtsTemplateStage stage = findOwnedStageOrThrow(stageId, template.getId());

        stage.setName(request.name().trim());
        AtsTemplateStage saved = stageRepository.saveAndFlush(stage);

        return toStageResponse(saved);
    }

    /** A template must always have at least one stage — deleting the last one is rejected
     *  rather than leaving an unusable, empty pipeline template. Remaining stages are
     *  repositioned to stay a dense, gap-free 0..n-1 sequence. */
    @Transactional
    public void deleteStage(UUID userId, UUID templateId, UUID stageId) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());
        requireNotArchived(template);
        AtsTemplateStage stage = findOwnedStageOrThrow(stageId, template.getId());

        List<AtsTemplateStage> remaining = stageRepository.findAllByTemplateIdOrderByPositionAsc(template.getId())
                .stream()
                .filter(existing -> !existing.getId().equals(stageId))
                .toList();

        if (remaining.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A template must have at least one stage — delete the template instead");
        }

        stageRepository.delete(stage);

        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        stageRepository.saveAll(remaining);
    }

    /** stageIds must be an exact permutation of this template's current stage ids — rejected
     *  (400) if anything is added, dropped, or duplicated, rather than guessing intent. */
    @Transactional
    public List<TemplateStageResponse> reorderStages(UUID userId, UUID templateId, StageReorderRequest request) {
        CompanyContext context = companyAccessResolver.requireOwner(userId);
        AtsPipelineTemplate template = findOwnedTemplateOrThrow(templateId, context.companyProfileId());
        requireNotArchived(template);

        List<AtsTemplateStage> current = stageRepository.findAllByTemplateIdOrderByPositionAsc(template.getId());
        List<AtsTemplateStage> reordered = applyPermutation(current, request.stageIds());

        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setPosition(i);
        }
        stageRepository.saveAll(reordered);

        return reordered.stream().map(this::toStageResponse).toList();
    }

    // ===== shared helpers =====

    /** Validates that requestedIds is an exact permutation of current's ids (same elements,
     *  same count, no duplicates) and returns current's stages reordered to match — rejecting
     *  anything that adds, drops, or duplicates a stage id rather than guessing intent. */
    private List<AtsTemplateStage> applyPermutation(List<AtsTemplateStage> current, List<UUID> requestedIds) {
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

    private void clearExistingDefault(UUID companyProfileId, UUID exceptTemplateId) {
        templateRepository.findByCompanyProfileIdAndDefaultTemplateTrueAndArchivedAtIsNull(companyProfileId)
                .filter(existingDefault -> !existingDefault.getId().equals(exceptTemplateId))
                .ifPresent(existingDefault -> {
                    existingDefault.setDefaultTemplate(false);
                    templateRepository.saveAndFlush(existingDefault);
                });
    }

    private void requireNotArchived(AtsPipelineTemplate template) {
        if (template.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An archived template's stages cannot be changed");
        }
    }

    private AtsPipelineTemplate findOwnedTemplateOrThrow(UUID templateId, UUID companyProfileId) {
        return templateRepository.findByIdAndCompanyProfileId(templateId, companyProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    }

    private AtsTemplateStage findOwnedStageOrThrow(UUID stageId, UUID templateId) {
        return stageRepository.findByIdAndTemplateId(stageId, templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stage not found"));
    }

    private AtsPipelineTemplate saveWithOptimisticLockCheck(AtsPipelineTemplate template, String label) {
        try {
            return templateRepository.saveAndFlush(template);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This " + label + " was changed by someone else. Please refresh and try again.");
        }
    }

    private String normalizeBlankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<AtsTemplateStage> stagesOf(UUID templateId) {
        return stageRepository.findAllByTemplateIdOrderByPositionAsc(templateId);
    }

    private TemplateStageResponse toStageResponse(AtsTemplateStage stage) {
        return new TemplateStageResponse(stage.getId(), stage.getName(), stage.getPosition());
    }

    private PipelineTemplateResponse toResponse(AtsPipelineTemplate template, List<AtsTemplateStage> stages) {
        List<TemplateStageResponse> stageResponses = stages.stream()
                .sorted(Comparator.comparingInt(AtsTemplateStage::getPosition))
                .map(this::toStageResponse)
                .toList();

        return new PipelineTemplateResponse(
                template.getId(),
                template.getCompanyProfileId(),
                template.getName(),
                template.getDescription(),
                template.isDefaultTemplate(),
                template.isArchived(),
                stageResponses,
                template.getVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    /** The hardcoded starter stage names (Applied -> Screening -> Interview -> Offer) used by
     *  InternshipPipelineService when an internship's pipeline is assigned with no explicit
     *  template and the company has no default template of its own. Exposed here (rather than
     *  duplicated) since this service owns the one authoritative stage-naming concept. */
    public static List<String> starterStageNames() {
        return STARTER_STAGE_NAMES;
    }
}
