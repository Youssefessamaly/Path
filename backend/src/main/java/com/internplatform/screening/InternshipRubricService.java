package com.internplatform.screening;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import com.internplatform.internship.Internship;
import com.internplatform.internship.InternshipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company-owned CRUD for the versioned InternshipRubric attached to one internship — see V27.
 * A rubric's requirements are only ever mutable while it is DRAFT; activating it freezes it
 * (ACTIVE), and activating a later draft moves it to ARCHIVED — both are then immutable
 * forever, so a future screening result can always cite the exact rubric version it used.
 *
 * This never touches Internship/InternshipService/InternshipController — the pre-existing
 * flat required-skills list on Internship itself is completely untouched and keeps working
 * exactly as before; a rubric is an optional, additional, separate layer on top of it.
 */
@Service
public class InternshipRubricService {

    private final InternshipRubricRepository rubricRepository;
    private final InternshipRubricRequirementRepository requirementRepository;
    private final InternshipRepository internshipRepository;
    private final ScreeningProfileRepository screeningProfileRepository;
    private final ScreeningProfileRequirementRepository screeningProfileRequirementRepository;
    private final CompanyAccessResolver companyAccessResolver;
    private final SkillRepository skillRepository;

    public InternshipRubricService(
            InternshipRubricRepository rubricRepository,
            InternshipRubricRequirementRepository requirementRepository,
            InternshipRepository internshipRepository,
            ScreeningProfileRepository screeningProfileRepository,
            ScreeningProfileRequirementRepository screeningProfileRequirementRepository,
            CompanyAccessResolver companyAccessResolver,
            SkillRepository skillRepository
    ) {
        this.rubricRepository = rubricRepository;
        this.requirementRepository = requirementRepository;
        this.internshipRepository = internshipRepository;
        this.screeningProfileRepository = screeningProfileRepository;
        this.screeningProfileRequirementRepository = screeningProfileRequirementRepository;
        this.companyAccessResolver = companyAccessResolver;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public InternshipRubricResponse createDraft(UUID userId, UUID internshipId, InternshipRubricCreateRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        if (rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.DRAFT).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A draft rubric already exists for this internship");
        }

        UUID screeningProfileId = request == null ? null : request.screeningProfileId();
        List<ScreeningProfileRequirement> sourceRequirements = List.of();
        if (screeningProfileId != null) {
            ScreeningProfile sourceProfile = screeningProfileRepository
                    .findByIdAndCompanyProfileId(screeningProfileId, company.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening profile not found"));
            sourceRequirements = screeningProfileRequirementRepository
                    .findAllByScreeningProfileIdOrderByDisplayOrderAscCreatedAtAsc(sourceProfile.getId());
        }

        int nextVersion = rubricRepository.findMaxVersion(internship.getId()).orElse(0) + 1;
        InternshipRubric draft = new InternshipRubric(internship.getId(), screeningProfileId, nextVersion);
        InternshipRubric savedDraft = rubricRepository.saveAndFlush(draft);

        copyRequirementsFromProfile(savedDraft.getId(), sourceRequirements);

        return toResponse(savedDraft, requirementsFor(savedDraft.getId()));
    }

    /** The rubric a recruiter should currently see/edit: the DRAFT if one exists, otherwise
     *  the ACTIVE version, otherwise 404 — an internship with no rubric configured at all. */
    @Transactional(readOnly = true)
    public InternshipRubricResponse getCurrentRubric(UUID userId, UUID internshipId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        InternshipRubric current = currentRubricOrNull(internship.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No rubric configured for this internship"));

        return toResponse(current, requirementsFor(current.getId()));
    }

    @Transactional(readOnly = true)
    public List<InternshipRubricResponse> listVersions(UUID userId, UUID internshipId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        return rubricRepository.findAllByInternshipIdOrderByVersionDesc(internship.getId()).stream()
                .map(rubric -> toResponse(rubric, requirementsFor(rubric.getId())))
                .toList();
    }

    @Transactional
    public InternshipRubricResponse activateDraft(UUID userId, UUID internshipId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        InternshipRubric draft = rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.DRAFT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No draft rubric to activate"));

        if (requirementRepository.findAllByInternshipRubricId(draft.getId()).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A rubric needs at least one requirement before it can be activated");
        }

        rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.ACTIVE)
                .ifPresent(previouslyActive -> {
                    previouslyActive.setStatus(RubricStatus.ARCHIVED);
                    rubricRepository.save(previouslyActive);
                });

        draft.setStatus(RubricStatus.ACTIVE);
        draft.setActivatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        InternshipRubric activated = rubricRepository.saveAndFlush(draft);

        return toResponse(activated, requirementsFor(activated.getId()));
    }

    /** Starts a new DRAFT by copying the most recent version's requirements (preferring
     *  ACTIVE, else the highest version regardless of status) — the only way to "edit" an
     *  ACTIVE/ARCHIVED rubric, since those stay immutable once frozen. */
    @Transactional
    public InternshipRubricResponse duplicateCurrent(UUID userId, UUID internshipId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        if (rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.DRAFT).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A draft rubric already exists for this internship");
        }

        InternshipRubric source = rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.ACTIVE)
                .or(() -> rubricRepository.findAllByInternshipIdOrderByVersionDesc(internship.getId()).stream().findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No rubric exists yet for this internship"));

        int nextVersion = rubricRepository.findMaxVersion(internship.getId()).orElse(0) + 1;
        InternshipRubric newDraft = new InternshipRubric(internship.getId(), source.getScreeningProfileId(), nextVersion);
        InternshipRubric savedDraft = rubricRepository.saveAndFlush(newDraft);

        List<InternshipRubricRequirement> copied = requirementsFor(source.getId()).stream()
                .map(original -> {
                    InternshipRubricRequirement copy = new InternshipRubricRequirement(
                            savedDraft.getId(), original.getRequirementType());
                    copy.setSkillId(original.getSkillId());
                    copy.setLabel(original.getLabel());
                    copy.setWeight(original.getWeight());
                    copy.setMinimumEvidenceStrength(original.getMinimumEvidenceStrength());
                    copy.setAcceptedEvidenceSources(new HashSet<>(original.getAcceptedEvidenceSources()));
                    copy.setRecruiterNotes(original.getRecruiterNotes());
                    copy.setDisplayOrder(original.getDisplayOrder());
                    return copy;
                })
                .toList();
        requirementRepository.saveAll(copied);

        return toResponse(savedDraft, requirementsFor(savedDraft.getId()));
    }

    @Transactional
    public void discardDraft(UUID userId, UUID internshipId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());

        InternshipRubric draft = rubricRepository.findByInternshipIdAndStatus(internship.getId(), RubricStatus.DRAFT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No draft rubric to discard"));

        rubricRepository.delete(draft);
    }

    @Transactional
    public RequirementResponse addRequirement(UUID userId, UUID internshipId, RequirementCreateRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipRubric draft = requireDraft(internship.getId());

        RequirementValidationSupport.requireSkillOrLabel(request.skillId(), request.label());
        if (request.skillId() != null) {
            RequirementValidationSupport.resolveSkillOrThrow(skillRepository, request.skillId());
            if (requirementRepository.existsByInternshipRubricIdAndSkillId(draft.getId(), request.skillId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This rubric already has a requirement for that skill");
            }
        }

        InternshipRubricRequirement requirement = new InternshipRubricRequirement(draft.getId(), request.requirementType());
        applyCreate(requirement, request);

        InternshipRubricRequirement saved = requirementRepository.saveAndFlush(requirement);
        return toRequirementResponse(saved);
    }

    @Transactional
    public RequirementResponse updateRequirement(
            UUID userId, UUID internshipId, UUID requirementId, RequirementUpdateRequest request
    ) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipRubric draft = requireDraft(internship.getId());

        InternshipRubricRequirement requirement = requirementRepository
                .findByIdAndInternshipRubricId(requirementId, draft.getId())
                .orElseThrow(this::requirementNotFound);

        RequirementValidationSupport.requireNonBlankLabelIfSkillless(requirement.getSkillId(), request.label());
        applyUpdate(requirement, request);

        InternshipRubricRequirement saved = requirementRepository.saveAndFlush(requirement);
        return toRequirementResponse(saved);
    }

    @Transactional
    public void deleteRequirement(UUID userId, UUID internshipId, UUID requirementId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        Internship internship = findOwnedInternshipOrThrow(internshipId, company.getId());
        InternshipRubric draft = requireDraft(internship.getId());

        InternshipRubricRequirement requirement = requirementRepository
                .findByIdAndInternshipRubricId(requirementId, draft.getId())
                .orElseThrow(this::requirementNotFound);

        requirementRepository.delete(requirement);
    }

    private InternshipRubric requireDraft(UUID internshipId) {
        return rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.DRAFT)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only a draft rubric can be edited — create one, or duplicate the current rubric into a new draft"
                ));
    }

    private Optional<InternshipRubric> currentRubricOrNull(UUID internshipId) {
        Optional<InternshipRubric> draft = rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.DRAFT);
        if (draft.isPresent()) {
            return draft;
        }
        return rubricRepository.findByInternshipIdAndStatus(internshipId, RubricStatus.ACTIVE);
    }

    private void copyRequirementsFromProfile(UUID rubricId, List<ScreeningProfileRequirement> sourceRequirements) {
        if (sourceRequirements.isEmpty()) {
            return;
        }
        List<InternshipRubricRequirement> copied = sourceRequirements.stream()
                .map(original -> {
                    InternshipRubricRequirement copy = new InternshipRubricRequirement(rubricId, original.getRequirementType());
                    copy.setSkillId(original.getSkillId());
                    copy.setLabel(original.getLabel());
                    copy.setWeight(original.getWeight());
                    copy.setMinimumEvidenceStrength(original.getMinimumEvidenceStrength());
                    copy.setAcceptedEvidenceSources(new HashSet<>(original.getAcceptedEvidenceSources()));
                    copy.setRecruiterNotes(original.getRecruiterNotes());
                    copy.setDisplayOrder(original.getDisplayOrder());
                    return copy;
                })
                .toList();
        requirementRepository.saveAll(copied);
    }

    private void applyCreate(InternshipRubricRequirement requirement, RequirementCreateRequest request) {
        requirement.setSkillId(request.skillId());
        requirement.setLabel(normalizeBlankToNull(request.label()));
        requirement.setWeight(request.weight());
        requirement.setMinimumEvidenceStrength(request.minimumEvidenceStrength());
        if (request.acceptedEvidenceSources() != null) {
            requirement.setAcceptedEvidenceSources(request.acceptedEvidenceSources());
        }
        requirement.setRecruiterNotes(normalizeBlankToNull(request.recruiterNotes()));
        requirement.setDisplayOrder(request.displayOrder());
    }

    private void applyUpdate(InternshipRubricRequirement requirement, RequirementUpdateRequest request) {
        if (request.requirementType() != null) {
            requirement.setRequirementType(request.requirementType());
        }
        if (request.label() != null) {
            requirement.setLabel(normalizeBlankToNull(request.label()));
        }
        if (request.weight() != null) {
            requirement.setWeight(request.weight());
        }
        if (request.minimumEvidenceStrength() != null) {
            requirement.setMinimumEvidenceStrength(request.minimumEvidenceStrength());
        }
        if (request.acceptedEvidenceSources() != null) {
            requirement.setAcceptedEvidenceSources(request.acceptedEvidenceSources());
        }
        if (request.recruiterNotes() != null) {
            requirement.setRecruiterNotes(normalizeBlankToNull(request.recruiterNotes()));
        }
        if (request.displayOrder() != null) {
            requirement.setDisplayOrder(request.displayOrder());
        }
    }

    private List<InternshipRubricRequirement> requirementsFor(UUID rubricId) {
        return requirementRepository.findAllByInternshipRubricIdOrderByDisplayOrderAscCreatedAtAsc(rubricId);
    }

    private CompanyProfile findCompanyProfileOrThrow(UUID userId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        return companyAccessResolver.findCompanyProfile(context).orElseThrow(this::companyNotFound);
    }

    private Internship findOwnedInternshipOrThrow(UUID internshipId, UUID companyProfileId) {
        return internshipRepository.findByIdAndCompanyProfileId(internshipId, companyProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));
    }

    private ResponseStatusException companyNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
    }

    private ResponseStatusException requirementNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found");
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private InternshipRubricResponse toResponse(InternshipRubric rubric, List<InternshipRubricRequirement> requirements) {
        return new InternshipRubricResponse(
                rubric.getId(),
                rubric.getInternshipId(),
                rubric.getScreeningProfileId(),
                rubric.getVersion(),
                rubric.getStatus(),
                requirements.stream().map(this::toRequirementResponse).toList(),
                rubric.getCreatedAt(),
                rubric.getUpdatedAt(),
                rubric.getActivatedAt()
        );
    }

    private RequirementResponse toRequirementResponse(InternshipRubricRequirement requirement) {
        String skillName = requirement.getSkillId() == null
                ? null
                : skillRepository.findById(requirement.getSkillId()).map(Skill::getName).orElse(null);

        return new RequirementResponse(
                requirement.getId(),
                requirement.getRequirementType(),
                requirement.getSkillId(),
                skillName,
                requirement.getLabel(),
                requirement.getWeight(),
                requirement.getMinimumEvidenceStrength(),
                requirement.getAcceptedEvidenceSources(),
                requirement.getRecruiterNotes(),
                requirement.getDisplayOrder(),
                requirement.getCreatedAt(),
                requirement.getUpdatedAt()
        );
    }
}
