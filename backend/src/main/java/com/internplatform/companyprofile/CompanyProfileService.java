package com.internplatform.companyprofile;

import com.internplatform.ats.access.CompanyAccessResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CompanyProfileService {

    private static final Pattern LOCAL_EGYPT_PHONE = Pattern.compile("^01[0125]\\d{8}$");
    private static final Pattern INTERNATIONAL_EGYPT_PHONE = Pattern.compile("^\\+201[0125]\\d{8}$");

    private final CompanyProfileRepository companyProfileRepository;
    private final CompanyAccessResolver companyAccessResolver;

    public CompanyProfileService(
            CompanyProfileRepository companyProfileRepository,
            CompanyAccessResolver companyAccessResolver
    ) {
        this.companyProfileRepository = companyProfileRepository;
        this.companyAccessResolver = companyAccessResolver;
    }

    /** Any ACTIVE company member (owner or team member) may view the shared company profile —
     *  not gated by CompanyApprovalGuard, matching this service's pre-existing behavior of
     *  never checking approval for profile reads. */
    @Transactional(readOnly = true)
    public CompanyProfileResponse getProfile(UUID userId) {
        CompanyProfile profile = companyAccessResolver.findActiveMember(userId)
                .flatMap(companyAccessResolver::findCompanyProfile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        return toResponse(profile);
    }

    /** Deliberately NOT gated by CompanyApprovalGuard — a PENDING or REJECTED company must be
     *  able to check its own status (that is the whole point of the status existing). Any
     *  ACTIVE company member may check it, same as getProfile above. */
    @Transactional(readOnly = true)
    public CompanyApprovalStatusResponse getApprovalStatus(UUID userId) {
        CompanyProfile profile = companyAccessResolver.findActiveMember(userId)
                .flatMap(companyAccessResolver::findCompanyProfile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        return new CompanyApprovalStatusResponse(profile.getApprovalStatus(), profile.getApprovalReviewedAt());
    }

    /**
     * OWNER-only mutation. A non-owner ACTIVE member (a recruiter) is rejected with 403 before
     * touching anything. A user who is not yet a member of ANY company at all (the normal
     * brand-new-company case — findActiveMember returns empty) falls through untouched to the
     * pre-existing self-creating lookup below, exactly as before this resolver existed: the
     * very first PATCH for a new company is what brings its profile into existence.
     */
    @Transactional
    public CompanyProfileResponse upsertProfile(UUID userId, CompanyProfileRequest request) {
        companyAccessResolver.findActiveMember(userId).ifPresent(context -> {
            if (!context.isOwner()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Only the company owner can perform this action");
            }
        });

        CompanyProfile profile = companyProfileRepository.findByUserId(userId)
                .orElseGet(() -> new CompanyProfile(userId));

        if (request.companyName() != null) {
            profile.setCompanyName(request.companyName());
        }

        if (request.industry() != null) {
            profile.setIndustry(request.industry());
        }

        if (request.companySize() != null) {
            profile.setCompanySize(request.companySize());
        }

        if (request.location() != null) {
            profile.setLocation(request.location());
        }

        if (request.website() != null) {
            profile.setWebsite(request.website());
        }

        if (request.description() != null) {
            profile.setDescription(request.description());
        }

        if (request.contactName() != null) {
            profile.setContactName(request.contactName());
        }

        if (request.contactPhone() != null) {
            profile.setContactPhone(normalizeEgyptianPhoneNumber(request.contactPhone()));
        }

        CompanyProfile saved = companyProfileRepository.saveAndFlush(profile);
        return toResponse(saved);
    }

    /** OWNER-only, same reasoning as upsertProfile above. Unlike upsertProfile this never
     *  self-creates — findProfileOrThrow still 404s for a user with no profile at all, exactly
     *  as before. */
    @Transactional
    public CompletionResult completeOnboarding(UUID userId) {
        companyAccessResolver.findActiveMember(userId).ifPresent(context -> {
            if (!context.isOwner()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Only the company owner can perform this action");
            }
        });

        CompanyProfile profile = findProfileOrThrow(userId);

        if (profile.isOnboardingCompleted()) {
            return new CompletionResult(toResponse(profile), List.of());
        }

        List<String> missingFields = findMissingRequiredFields(profile);

        if (!missingFields.isEmpty()) {
            return new CompletionResult(null, missingFields);
        }

        profile.markOnboardingCompleted(OffsetDateTime.now(ZoneOffset.UTC));
        CompanyProfile saved = companyProfileRepository.saveAndFlush(profile);

        return new CompletionResult(toResponse(saved), List.of());
    }

    private CompanyProfile findProfileOrThrow(UUID userId) {
        return companyProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Company profile not found"
                ));
    }

    private List<String> findMissingRequiredFields(CompanyProfile profile) {
        List<String> missing = new ArrayList<>();

        if (isBlank(profile.getCompanyName())) {
            missing.add("companyName");
        }
        if (isBlank(profile.getIndustry())) {
            missing.add("industry");
        }
        if (isBlank(profile.getCompanySize())) {
            missing.add("companySize");
        }
        if (isBlank(profile.getLocation())) {
            missing.add("location");
        }
        if (isBlank(profile.getDescription())) {
            missing.add("description");
        }
        if (isBlank(profile.getContactName())) {
            missing.add("contactName");
        }
        if (isBlank(profile.getContactPhone())) {
            missing.add("contactPhone");
        }

        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEgyptianPhoneNumber(String phoneNumber) {
        if (LOCAL_EGYPT_PHONE.matcher(phoneNumber).matches()) {
            return "+20" + phoneNumber.substring(1);
        }

        if (INTERNATIONAL_EGYPT_PHONE.matcher(phoneNumber).matches()) {
            return phoneNumber;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Contact phone must be a valid Egyptian mobile number"
        );
    }

    private CompanyProfileResponse toResponse(CompanyProfile profile) {
        return new CompanyProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getCompanyName(),
                profile.getIndustry(),
                profile.getCompanySize(),
                profile.getLocation(),
                profile.getWebsite(),
                profile.getDescription(),
                profile.getContactName(),
                profile.getContactPhone(),
                profile.isOnboardingCompleted(),
                profile.getOnboardingCompletedAt(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    public record CompletionResult(
            CompanyProfileResponse profile,
            List<String> missingFields
    ) {
    }
}
