package com.evyoog.gl.auth.api;

import com.evyoog.gl.auth.dto.CreateRoleRequest;
import com.evyoog.gl.auth.dto.PermissionResponse;
import com.evyoog.gl.auth.dto.RoleResponse;
import com.evyoog.gl.auth.dto.UpdateRoleRequest;
import com.evyoog.gl.auth.service.RoleService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "AUTH-01 Role Management")
public class RoleController {

    private final RoleService service;

    @PostMapping("/api/v1/auth/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:roles:create')")
    @Operation(summary = "Create a custom role with a set of permissions")
    public ApiResponse<RoleResponse> create(
            @Valid @RequestBody CreateRoleRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/api/v1/auth/roles")
    @PreAuthorize("hasAuthority('gl:roles:view')")
    @Operation(summary = "List all roles")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/api/v1/auth/roles/{id}")
    @PreAuthorize("hasAuthority('gl:roles:view')")
    @Operation(summary = "Get a role by id, including its resolved permissions")
    public ApiResponse<RoleResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PatchMapping("/api/v1/auth/roles/{id}")
    @PreAuthorize("hasAuthority('gl:roles:edit')")
    @Operation(summary = "Edit a custom role's name, description, permissions or active flag")
    public ApiResponse<RoleResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateRoleRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.ok(service.update(id, request, userId));
    }

    @GetMapping("/api/v1/auth/permissions")
    @PreAuthorize("hasAuthority('gl:roles:view')")
    @Operation(summary = "List all available permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.ok(service.listPermissions());
    }
}
