package com.internplatform.ats.candidate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * ATS candidate-tracking endpoints: read-only current stage/history (the original Kanban-
 * foundation phase), plus the one human-initiated mutation — advancing to the next stage (see
 * AtsCandidateMovementService). Rejecting an applicant deliberately has no endpoint here; it
 * reuses the existing CompanyApplicationController PATCH .../status endpoint unchanged.
 */
@RestController
public class CandidateController {

    private final CandidateService candidateService;
    private final AtsCandidateMovementService candidateMovementService;

    public CandidateController(CandidateService candidateService, AtsCandidateMovementService candidateMovementService) {
        this.candidateService = candidateService;
        this.candidateMovementService = candidateMovementService;
    }

    @GetMapping("/api/v1/company/ats/internships/{internshipId}/candidates")
    public List<CandidateStageResponse> listCandidates(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        return candidateService.listCandidates(currentUserId(jwt), internshipId);
    }

    @GetMapping("/api/v1/company/ats/applications/{applicationId}")
    public CandidateStageResponse getCandidateStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return candidateService.getCandidateStage(currentUserId(jwt), applicationId);
    }

    @GetMapping("/api/v1/company/ats/applications/{applicationId}/history")
    public List<StageEventResponse> getCandidateHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return candidateService.getCandidateHistory(currentUserId(jwt), applicationId);
    }

    @PostMapping("/api/v1/company/ats/applications/{applicationId}/advance")
    public CandidateStageResponse advance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return candidateMovementService.advance(currentUserId(jwt), applicationId);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
