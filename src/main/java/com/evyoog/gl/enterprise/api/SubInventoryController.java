package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.CreateSubInventoryRequest;
import com.evyoog.gl.enterprise.dto.SubInventoryResponse;
import com.evyoog.gl.enterprise.service.SubInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/gl/sub-inventories")
@RequiredArgsConstructor
@Tag(name = "GL-01 Sub-Inventories")
public class SubInventoryController {

    private final SubInventoryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:enterprise:manage')")
    @Operation(summary = "Create a sub-inventory")
    public ApiResponse<SubInventoryResponse> create(
            @Valid @RequestBody CreateSubInventoryRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gl:enterprise:view')")
    @Operation(summary = "Get a sub-inventory by id")
    public ApiResponse<SubInventoryResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gl:enterprise:view')")
    @Operation(summary = "List sub-inventories, optionally filtered by inventory organisation")
    public ApiResponse<List<SubInventoryResponse>> list(
            @RequestParam(required = false) UUID inventoryOrganisationId) {
        return ApiResponse.ok(service.list(inventoryOrganisationId));
    }
}
