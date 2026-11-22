package com.internplatform.screening;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/internships/{internshipId}")
public class CompanyScreeningController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ScreeningService screeningService;
    private final CompanyAiAssistService aiAssistService;

    public CompanyScreeningController(ScreeningService screeningService, CompanyAiAssistService aiAssistService) {
        this.screeningService = screeningService;
        this.aiAssistService = aiAssistService;
    }

    @PostMapping("/applications/{applicationId}/screening-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningReportResponse screenApplication(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID applicationId
    ) {
        return screeningService.screenApplication(currentUserId(jwt), internshipId, applicationId);
    }

    @GetMapping("/applications/{applicationId}/screening-reports/latest")
    public ScreeningReportResponse getLatestReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID applicationId
    ) {
        return screeningService.getLatestReport(currentUserId(jwt), internshipId, applicationId);
    }

    @PostMapping("/screening-reports/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ScreeningReportResponse> screenBatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @Valid @RequestBody ScreeningBatchRequest request
    ) {
        return screeningService.screenBatch(currentUserId(jwt), internshipId, request);
    }

    @GetMapping("/screening-reports")
    public Page<ScreeningSummaryResponse> listSummaries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return screeningService.listSummaries(currentUserId(jwt), internshipId, pageable);
    }

    /**
     * Stage 5B Phase C1: optional AI-assisted re-interpretation of this specific, already-
     * computed report's NEEDS_REVIEW/INSUFFICIENT_EVIDENCE requirements only — never a new
     * deterministic screening pass, never a write to this or any other report. Returns 200
     * with completed=false (never a 4xx/5xx) whenever AI is disabled, pending privacy review,
     * rate-limited, or the provider fails — see CompanyAiAssistService.
     */
    @PostMapping("/applications/{applicationId}/screening-reports/{reportId}/ai-evidence-analysis")
    public AiEvidenceAnalysisResponse analyzeEvidenceWithAi(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID applicationId,
            @PathVariable UUID reportId,
            @RequestBody(required = false) AiEvidenceAnalysisApiRequest request
    ) {
        return aiAssistService.analyzeEvidence(currentUserId(jwt), internshipId, applicationId, reportId, request);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
