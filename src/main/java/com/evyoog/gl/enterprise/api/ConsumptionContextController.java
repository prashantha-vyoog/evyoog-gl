package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.ConsumptionContextResponse;
import com.evyoog.gl.enterprise.dto.CreateConsumptionContextRequest;
import com.evyoog.gl.enterprise.service.ConsumptionContextService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gl/consumption-contexts")
@RequiredArgsConstructor
@Tag(name = "GL-01 Consumption Contexts")
public class ConsumptionContextController {

    private final ConsumptionContextService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:enterprise:manage')")
    @Operation(summary = "Create a consumption context (WORKSPACE or SESSION)")
    public ApiResponse<ConsumptionContextResponse> create(
            @Valid @RequestBody CreateConsumptionContextRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.create(request, userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gl:enterprise:view')")
    @Operation(summary = "Get a consumption context by id")
    public ApiResponse<ConsumptionContextResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gl:enterprise:view')")
    @Operation(summary = "List all consumption contexts")
    public ApiResponse<List<ConsumptionContextResponse>> list() {
        return ApiResponse.ok(service.list());
    }
}
