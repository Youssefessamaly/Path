package com.internplatform.screening;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.ats.access.CompanyContext;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.internprofile.Skill;
import com.internplatform.internprofile.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Company-owned CRUD for reusable ScreeningProfile templates and their requirements — see
 * V26. A profile is never used directly for screening; InternshipRubricService copies its
 * requirements into a per-internship rubric on request. Authorization follows the same
 * "any ACTIVE member of an approved company" policy already used by
 * CompanyApplicationService/InternshipApplicationQuestionService — see CompanyAccessResolver.
 */
@Service
public class ScreeningProfileService {

    private final ScreeningProfileRepository profileRepository;
    private final ScreeningProfileRequirementRepository requirementRepository;
    private final CompanyAccessResolver companyAccessResolver;
    private final SkillRepository skillRepository;

    public ScreeningProfileService(
            ScreeningProfileRepository profileRepository,
            ScreeningProfileRequirementRepository requirementRepository,
            CompanyAccessResolver companyAccessResolver,
            SkillRepository skillRepository
    ) {
        this.profileRepository = profileRepository;
        this.requirementRepository = requirementRepository;
        this.companyAccessResolver = companyAccessResolver;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public ScreeningProfileResponse createProfile(UUID userId, ScreeningProfileCreateRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);

        ScreeningProfile profile = new ScreeningProfile(
                company.getId(), request.name().trim(), normalizeBlankToNull(request.description()));

        ScreeningProfile saved = profileRepository.saveAndFlush(profile);
        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public List<ScreeningProfileResponse> listProfiles(UUID userId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);

        return profileRepository.findAllByCompanyProfileIdOrderByNameAsc(company.getId()).stream()
                .map(profile -> toResponse(profile, requirementsFor(profile.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScreeningProfileResponse getProfile(UUID userId, UUID profileId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile profile = findOwnedProfileOrThrow(profileId, company.getId());
        return toResponse(profile, requirementsFor(profile.getId()));
    }

    @Transactional
    public ScreeningProfileResponse updateProfile(UUID userId, UUID profileId, ScreeningProfileUpdateRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile profile = findOwnedProfileOrThrow(profileId, company.getId());

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be blank");
            }
            profile.setName(trimmed);
        }

        if (request.description() != null) {
            profile.setDescription(normalizeBlankToNull(request.description()));
        }

        ScreeningProfile saved = profileRepository.saveAndFlush(profile);
        return toResponse(saved, requirementsFor(saved.getId()));
    }

    @Transactional
    public ScreeningProfileResponse archiveProfile(UUID userId, UUID profileId) {
        return setStatus(userId, profileId, ScreeningProfileStatus.ARCHIVED);
    }

    @Transactional
    public ScreeningProfileResponse activateProfile(UUID userId, UUID profileId) {
        return setStatus(userId, profileId, ScreeningProfileStatus.ACTIVE);
    }

    private ScreeningProfileResponse setStatus(UUID userId, UUID profileId, ScreeningProfileStatus status) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile profile = findOwnedProfileOrThrow(profileId, company.getId());
        profile.setStatus(status);
        ScreeningProfile saved = profileRepository.saveAndFlush(profile);
        return toResponse(saved, requirementsFor(saved.getId()));
    }

    /** Creates an independent copy — new ids throughout, including every requirement — so
     *  editing the copy can never affect the original, or vice versa. */
    @Transactional
    public ScreeningProfileResponse duplicateProfile(UUID userId, UUID profileId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile source = findOwnedProfileOrThrow(profileId, company.getId());

        ScreeningProfile copy = new ScreeningProfile(company.getId(), source.getName() + " (Copy)", source.getDescription());
        ScreeningProfile savedCopy = profileRepository.saveAndFlush(copy);

        List<ScreeningProfileRequirement> copiedRequirements = requirementsFor(source.getId()).stream()
                .map(original -> {
                    ScreeningProfileRequirement copied = new ScreeningProfileRequirement(
                            savedCopy.getId(), original.getRequirementType());
                    copied.setSkillId(original.getSkillId());
                    copied.setLabel(original.getLabel());
                    copied.setWeight(original.getWeight());
                    copied.setMinimumEvidenceStrength(original.getMinimumEvidenceStrength());
                    copied.setAcceptedEvidenceSources(new HashSet<>(original.getAcceptedEvidenceSources()));
                    copied.setRecruiterNotes(original.getRecruiterNotes());
                    copied.setDisplayOrder(original.getDisplayOrder());
                    return copied;
                })
                .toList();
        requirementRepository.saveAll(copiedRequirements);

        return toResponse(savedCopy, requirementsFor(savedCopy.getId()));
    }

    @Transactional
    public RequirementResponse addRequirement(UUID userId, UUID profileId, RequirementCreateRequest request) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile profile = findOwnedProfileOrThrow(profileId, company.getId());

        RequirementValidationSupport.requireSkillOrLabel(request.skillId(), request.label());
        if (request.skillId() != null) {
            RequirementValidationSupport.resolveSkillOrThrow(skillRepository, request.skillId());
            if (requirementRepository.existsByScreeningProfileIdAndSkillId(profile.getId(), request.skillId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This profile already has a requirement for that skill");
            }
        }

        ScreeningProfileRequirement requirement = new ScreeningProfileRequirement(profile.getId(), request.requirementType());
        applyCreate(requirement, request);

        ScreeningProfileRequirement saved = requirementRepository.saveAndFlush(requirement);
        return toRequirementResponse(saved);
    }

    @Transactional
    public RequirementResponse updateRequirement(
            UUID userId, UUID profileId, UUID requirementId, RequirementUpdateRequest request
    ) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile profile = findOwnedProfileOrThrow(profileId, company.getId());
        ScreeningProfileRequirement requirement = requirementRepository
                .findByIdAndScreeningProfileId(requirementId, profile.getId())
                .orElseThrow(this::requirementNotFound);

        RequirementValidationSupport.requireNonBlankLabelIfSkillless(requirement.getSkillId(), request.label());
        applyUpdate(requirement, request);

        ScreeningProfileRequirement saved = requirementRepository.saveAndFlush(requirement);
        return toRequirementResponse(saved);
    }

    @Transactional
    public void deleteRequirement(UUID userId, UUID profileId, UUID requirementId) {
        CompanyProfile company = findCompanyProfileOrThrow(userId);
        ScreeningProfile profile = findOwnedProfileOrThrow(profileId, company.getId());
        ScreeningProfileRequirement requirement = requirementRepository
                .findByIdAndScreeningProfileId(requirementId, profile.getId())
                .orElseThrow(this::requirementNotFound);

        requirementRepository.delete(requirement);
    }

    private void applyCreate(ScreeningProfileRequirement requirement, RequirementCreateRequest request) {
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

    private void applyUpdate(ScreeningProfileRequirement requirement, RequirementUpdateRequest request) {
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

    private List<ScreeningProfileRequirement> requirementsFor(UUID profileId) {
        return requirementRepository.findAllByScreeningProfileIdOrderByDisplayOrderAscCreatedAtAsc(profileId);
    }

    private CompanyProfile findCompanyProfileOrThrow(UUID userId) {
        CompanyContext context = companyAccessResolver.requireActiveMember(userId);
        return companyAccessResolver.findCompanyProfile(context).orElseThrow(this::companyNotFound);
    }

    private ScreeningProfile findOwnedProfileOrThrow(UUID profileId, UUID companyProfileId) {
        return profileRepository.findByIdAndCompanyProfileId(profileId, companyProfileId)
                .orElseThrow(this::profileNotFound);
    }

    private ResponseStatusException companyNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
    }

    private ResponseStatusException profileNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening profile not found");
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

    private ScreeningProfileResponse toResponse(ScreeningProfile profile, List<ScreeningProfileRequirement> requirements) {
        return new ScreeningProfileResponse(
                profile.getId(),
                profile.getCompanyProfileId(),
                profile.getName(),
                profile.getDescription(),
                profile.getStatus(),
                requirements.stream().map(this::toRequirementResponse).toList(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private RequirementResponse toRequirementResponse(ScreeningProfileRequirement requirement) {
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
