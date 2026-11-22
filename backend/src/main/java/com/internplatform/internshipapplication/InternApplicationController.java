package com.internplatform.internshipapplication;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intern/applications")
public class InternApplicationController {

    private final InternshipApplicationService internshipApplicationService;

    public InternApplicationController(InternshipApplicationService internshipApplicationService) {
        this.internshipApplicationService = internshipApplicationService;
    }

    @GetMapping
    public List<InternshipApplicationResponse> listOwnApplications(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return internshipApplicationService.listOwnApplications(currentUserId(jwt));
    }

    @GetMapping("/{applicationId}")
    public InternshipApplicationResponse getOwnApplication(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return internshipApplicationService.getOwnApplication(currentUserId(jwt), applicationId);
    }

    /**
     * Withdraws AI-analysis consent for one of the caller's own applications. Reversible,
     * one-directional (can only move a grant to a withdrawal — a fresh grant only ever happens
     * at submission time), and takes effect immediately for any future recruiter-triggered
     * analysis — see InternshipApplicationService#withdrawAiAnalysisConsent.
     */
    @DeleteMapping("/{applicationId}/ai-analysis-consent")
    public InternshipApplicationResponse withdrawAiAnalysisConsent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return internshipApplicationService.withdrawAiAnalysisConsent(currentUserId(jwt), applicationId);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
