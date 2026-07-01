package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.CreateInventoryOrganisationRequest;
import com.evyoog.gl.enterprise.dto.InventoryOrganisationResponse;
import com.evyoog.gl.enterprise.service.InventoryOrganisationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/gl/inventory-organisations")
@RequiredArgsConstructor
@Tag(name = "GL-01 Inventory Organisations")
public class InventoryOrganisationController {

    private final InventoryOrganisationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an inventory organisation (optional under Thin ES)")
    public ApiResponse<InventoryOrganisationResponse> create(
            @Valid @RequestBody CreateInventoryOrganisationRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an inventory organisation by id")
    public ApiResponse<InventoryOrganisationResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "List inventory organisations, optionally filtered by business unit")
    public ApiResponse<List<InventoryOrganisationResponse>> list(
            @RequestParam(required = false) UUID businessUnitId) {
        return ApiResponse.ok(service.list(businessUnitId));
    }
}
