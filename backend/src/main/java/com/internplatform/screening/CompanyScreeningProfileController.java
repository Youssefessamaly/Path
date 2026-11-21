package com.internplatform.screening;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/screening-profiles")
public class CompanyScreeningProfileController {

    private final ScreeningProfileService profileService;

    public CompanyScreeningProfileController(ScreeningProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningProfileResponse createProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ScreeningProfileCreateRequest request
    ) {
        return profileService.createProfile(currentUserId(jwt), request);
    }

    @GetMapping
    public List<ScreeningProfileResponse> listProfiles(@AuthenticationPrincipal Jwt jwt) {
        return profileService.listProfiles(currentUserId(jwt));
    }

    @GetMapping("/{profileId}")
    public ScreeningProfileResponse getProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId
    ) {
        return profileService.getProfile(currentUserId(jwt), profileId);
    }

    @PatchMapping("/{profileId}")
    public ScreeningProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId,
            @Valid @RequestBody ScreeningProfileUpdateRequest request
    ) {
        return profileService.updateProfile(currentUserId(jwt), profileId, request);
    }

    @PostMapping("/{profileId}/archive")
    public ScreeningProfileResponse archiveProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId
    ) {
        return profileService.archiveProfile(currentUserId(jwt), profileId);
    }

    @PostMapping("/{profileId}/activate")
    public ScreeningProfileResponse activateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId
    ) {
        return profileService.activateProfile(currentUserId(jwt), profileId);
    }

    @PostMapping("/{profileId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningProfileResponse duplicateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId
    ) {
        return profileService.duplicateProfile(currentUserId(jwt), profileId);
    }

    @PostMapping("/{profileId}/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse addRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId,
            @Valid @RequestBody RequirementCreateRequest request
    ) {
        return profileService.addRequirement(currentUserId(jwt), profileId, request);
    }

    @PatchMapping("/{profileId}/requirements/{requirementId}")
    public RequirementResponse updateRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId,
            @PathVariable UUID requirementId,
            @Valid @RequestBody RequirementUpdateRequest request
    ) {
        return profileService.updateRequirement(currentUserId(jwt), profileId, requirementId, request);
    }

    @DeleteMapping("/{profileId}/requirements/{requirementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId,
            @PathVariable UUID requirementId
    ) {
        profileService.deleteRequirement(currentUserId(jwt), profileId, requirementId);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
