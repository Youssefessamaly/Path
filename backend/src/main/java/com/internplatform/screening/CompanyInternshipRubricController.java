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
@RequestMapping("/api/v1/company/internships/{internshipId}/rubric")
public class CompanyInternshipRubricController {

    private final InternshipRubricService rubricService;

    public CompanyInternshipRubricController(InternshipRubricService rubricService) {
        this.rubricService = rubricService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InternshipRubricResponse createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @RequestBody(required = false) InternshipRubricCreateRequest request
    ) {
        return rubricService.createDraft(currentUserId(jwt), internshipId, request);
    }

    @GetMapping
    public InternshipRubricResponse getCurrentRubric(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        return rubricService.getCurrentRubric(currentUserId(jwt), internshipId);
    }

    @GetMapping("/versions")
    public List<InternshipRubricResponse> listVersions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        return rubricService.listVersions(currentUserId(jwt), internshipId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        rubricService.discardDraft(currentUserId(jwt), internshipId);
    }

    @PostMapping("/activate")
    public InternshipRubricResponse activateDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        return rubricService.activateDraft(currentUserId(jwt), internshipId);
    }

    @PostMapping("/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public InternshipRubricResponse duplicateCurrent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        return rubricService.duplicateCurrent(currentUserId(jwt), internshipId);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse addRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @Valid @RequestBody RequirementCreateRequest request
    ) {
        return rubricService.addRequirement(currentUserId(jwt), internshipId, request);
    }

    @PatchMapping("/requirements/{requirementId}")
    public RequirementResponse updateRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID requirementId,
            @Valid @RequestBody RequirementUpdateRequest request
    ) {
        return rubricService.updateRequirement(currentUserId(jwt), internshipId, requirementId, request);
    }

    @DeleteMapping("/requirements/{requirementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID requirementId
    ) {
        rubricService.deleteRequirement(currentUserId(jwt), internshipId, requirementId);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
