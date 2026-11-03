package com.internplatform.ats.candidate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only ATS candidate-tracking endpoints — the foundation for the future candidate Kanban
 * phase. No stage-movement endpoint exists yet; these only ever report the current, automatic-
 * intake-assigned state.
 */
@RestController
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
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

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
