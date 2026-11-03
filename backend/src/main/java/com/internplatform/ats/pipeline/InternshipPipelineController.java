package com.internplatform.ats.pipeline;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/ats/internships/{internshipId}/pipeline")
public class InternshipPipelineController {

    private final InternshipPipelineService internshipPipelineService;

    public InternshipPipelineController(InternshipPipelineService internshipPipelineService) {
        this.internshipPipelineService = internshipPipelineService;
    }

    @GetMapping
    public InternshipPipelineResponse getPipeline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId
    ) {
        return internshipPipelineService.getPipeline(currentUserId(jwt), internshipId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InternshipPipelineResponse assignPipeline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @RequestBody(required = false) AssignPipelineRequest request
    ) {
        AssignPipelineRequest safeRequest = request != null ? request : new AssignPipelineRequest(null);
        return internshipPipelineService.assignPipeline(currentUserId(jwt), internshipId, safeRequest);
    }

    @PostMapping("/stages")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineStageResponse addStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @Valid @RequestBody PipelineStageCreateRequest request
    ) {
        return internshipPipelineService.addStage(currentUserId(jwt), internshipId, request);
    }

    @PatchMapping("/stages/{stageId}")
    public PipelineStageResponse updateStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID stageId,
            @Valid @RequestBody PipelineStageUpdateRequest request
    ) {
        return internshipPipelineService.updateStage(currentUserId(jwt), internshipId, stageId, request);
    }

    @DeleteMapping("/stages/{stageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @PathVariable UUID stageId
    ) {
        internshipPipelineService.deleteStage(currentUserId(jwt), internshipId, stageId);
    }

    @PutMapping("/stages/order")
    public List<PipelineStageResponse> reorderStages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID internshipId,
            @Valid @RequestBody PipelineStageReorderRequest request
    ) {
        return internshipPipelineService.reorderStages(currentUserId(jwt), internshipId, request);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
