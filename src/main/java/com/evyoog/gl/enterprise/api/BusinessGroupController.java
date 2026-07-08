package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.BusinessGroupResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessGroupRequest;
import com.evyoog.gl.enterprise.service.BusinessGroupService;
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
@RequestMapping("/api/v1/gl/business-groups")
@RequiredArgsConstructor
@Tag(name = "GL-01 Business Groups")
public class BusinessGroupController {

    private final BusinessGroupService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:enterprise:manage')")
    @Operation(summary = "Create a business group")
    public ApiResponse<BusinessGroupResponse> create(
            @Valid @RequestBody CreateBusinessGroupRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gl:enterprise:view')")
    @Operation(summary = "Get a business group by id")
    public ApiResponse<BusinessGroupResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gl:enterprise:view')")
    @Operation(summary = "List business groups, optionally filtered by consumption context")
    public ApiResponse<List<BusinessGroupResponse>> list(
            @RequestParam(required = false) UUID consumptionContextId) {
        return ApiResponse.ok(service.list(consumptionContextId));
    }
}
