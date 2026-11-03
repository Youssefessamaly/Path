package com.internplatform.ats.template;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/ats/pipeline-templates")
public class PipelineTemplateController {

    private final PipelineTemplateService pipelineTemplateService;

    public PipelineTemplateController(PipelineTemplateService pipelineTemplateService) {
        this.pipelineTemplateService = pipelineTemplateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineTemplateResponse createTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PipelineTemplateCreateRequest request
    ) {
        return pipelineTemplateService.createTemplate(currentUserId(jwt), request);
    }

    @GetMapping
    public List<PipelineTemplateResponse> listTemplates(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived
    ) {
        return pipelineTemplateService.listTemplates(currentUserId(jwt), includeArchived);
    }

    @GetMapping("/{templateId}")
    public PipelineTemplateResponse getTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId
    ) {
        return pipelineTemplateService.getTemplate(currentUserId(jwt), templateId);
    }

    @PatchMapping("/{templateId}")
    public PipelineTemplateResponse updateTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @Valid @RequestBody PipelineTemplateUpdateRequest request
    ) {
        return pipelineTemplateService.updateTemplate(currentUserId(jwt), templateId, request);
    }

    @PostMapping("/{templateId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineTemplateResponse duplicateTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId
    ) {
        return pipelineTemplateService.duplicateTemplate(currentUserId(jwt), templateId);
    }

    @PostMapping("/{templateId}/archive")
    public PipelineTemplateResponse archiveTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId
    ) {
        return pipelineTemplateService.archiveTemplate(currentUserId(jwt), templateId);
    }

    @PostMapping("/{templateId}/stages")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateStageResponse addStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @Valid @RequestBody TemplateStageCreateRequest request
    ) {
        return pipelineTemplateService.addStage(currentUserId(jwt), templateId, request);
    }

    @PatchMapping("/{templateId}/stages/{stageId}")
    public TemplateStageResponse updateStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @PathVariable UUID stageId,
            @Valid @RequestBody TemplateStageUpdateRequest request
    ) {
        return pipelineTemplateService.updateStage(currentUserId(jwt), templateId, stageId, request);
    }

    @DeleteMapping("/{templateId}/stages/{stageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @PathVariable UUID stageId
    ) {
        pipelineTemplateService.deleteStage(currentUserId(jwt), templateId, stageId);
    }

    @PutMapping("/{templateId}/stages/order")
    public List<TemplateStageResponse> reorderStages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @Valid @RequestBody StageReorderRequest request
    ) {
        return pipelineTemplateService.reorderStages(currentUserId(jwt), templateId, request);
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
