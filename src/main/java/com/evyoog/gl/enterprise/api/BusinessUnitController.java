package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.BusinessUnitResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessUnitRequest;
import com.evyoog.gl.enterprise.dto.UpdateBusinessUnitRequest;
import com.evyoog.gl.enterprise.service.BusinessUnitService;
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
@RequestMapping("/api/v1/gl/business-units")
@RequiredArgsConstructor
@Tag(name = "GL-01 Business Units")
public class BusinessUnitController {

    private final BusinessUnitService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a business unit (validates GSTIN format)")
    public ApiResponse<BusinessUnitResponse> create(
            @Valid @RequestBody CreateBusinessUnitRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a business unit by id")
    public ApiResponse<BusinessUnitResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "List business units, optionally filtered by legal entity")
    public ApiResponse<List<BusinessUnitResponse>> list(
            @RequestParam(required = false) UUID legalEntityId) {
        return ApiResponse.ok(service.list(legalEntityId));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update mutable fields of a business unit")
    public ApiResponse<BusinessUnitResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessUnitRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.update(id, request, userId));
    }
}
