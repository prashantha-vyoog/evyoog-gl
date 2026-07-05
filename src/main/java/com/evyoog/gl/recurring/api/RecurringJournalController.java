package com.evyoog.gl.recurring.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.recurring.dto.CreateRecurringTemplateRequest;
import com.evyoog.gl.recurring.dto.DeactivateTemplateRequest;
import com.evyoog.gl.recurring.dto.GenerateRecurringJournalRequest;
import com.evyoog.gl.recurring.dto.GenerateRecurringJournalResponse;
import com.evyoog.gl.recurring.dto.RecurringRunResponse;
import com.evyoog.gl.recurring.dto.RecurringTemplateResponse;
import com.evyoog.gl.recurring.service.RecurringJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-14 Recurring Journals")
public class RecurringJournalController {

    private final RecurringJournalService service;

    @PostMapping("/api/v1/gl/recurring-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a Recurring Journal template")
    public ApiResponse<RecurringTemplateResponse> createTemplate(@Valid @RequestBody CreateRecurringTemplateRequest request) {
        return ApiResponse.created(service.createTemplate(request));
    }

    @GetMapping("/api/v1/gl/recurring-templates/{id}")
    @Operation(summary = "Get a Recurring Journal template by ID")
    public ApiResponse<RecurringTemplateResponse> getTemplate(@PathVariable UUID id) {
        return ApiResponse.ok(service.getTemplate(id));
    }

    @GetMapping("/api/v1/gl/recurring-templates")
    @Operation(summary = "List active Recurring Journal templates for a Legal Entity")
    public ApiResponse<List<RecurringTemplateResponse>> listTemplates(@RequestParam UUID legalEntityId) {
        return ApiResponse.ok(service.listTemplates(legalEntityId));
    }

    @PatchMapping("/api/v1/gl/recurring-templates/{id}/deactivate")
    @Operation(summary = "Deactivate a Recurring Journal template")
    public ApiResponse<RecurringTemplateResponse> deactivateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateTemplateRequest request) {
        return ApiResponse.ok(service.deactivateTemplate(id, request));
    }

    @PostMapping("/api/v1/gl/recurring-templates/{id}/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate and post a journal from a Recurring Journal template for a target period")
    public ApiResponse<GenerateRecurringJournalResponse> generate(
            @PathVariable UUID id,
            @Valid @RequestBody GenerateRecurringJournalRequest request) {
        return ApiResponse.created(service.generate(id, request));
    }

    @GetMapping("/api/v1/gl/recurring-templates/{id}/runs")
    @Operation(summary = "Chronological generation history for a Recurring Journal template")
    public ApiResponse<List<RecurringRunResponse>> getRuns(@PathVariable UUID id) {
        return ApiResponse.ok(service.getRuns(id));
    }
}
