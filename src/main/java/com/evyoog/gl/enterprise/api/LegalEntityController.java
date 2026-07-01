package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.CreateLegalEntityRequest;
import com.evyoog.gl.enterprise.dto.LegalEntityResponse;
import com.evyoog.gl.enterprise.dto.UpdateLegalEntityRequest;
import com.evyoog.gl.enterprise.service.LegalEntityService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gl/legal-entities")
@RequiredArgsConstructor
@Tag(name = "GL-01 Legal Entities")
public class LegalEntityController {

    private final LegalEntityService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a legal entity (enforces Thin ES one-LE limit)")
    public ApiResponse<LegalEntityResponse> create(
            @Valid @RequestBody CreateLegalEntityRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a legal entity by id")
    public ApiResponse<LegalEntityResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "List legal entities, optionally filtered by business group")
    public ApiResponse<List<LegalEntityResponse>> list(
            @RequestParam(required = false) UUID businessGroupId) {
        return ApiResponse.ok(service.list(businessGroupId));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update mutable fields of a legal entity")
    public ApiResponse<LegalEntityResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLegalEntityRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.update(id, request, userId));
    }
}
