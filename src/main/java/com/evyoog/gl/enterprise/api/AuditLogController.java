package com.evyoog.gl.enterprise.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.enterprise.dto.AuditLogResponse;
import com.evyoog.gl.enterprise.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gl/audit-log")
@RequiredArgsConstructor
@Tag(name = "GL-29 Audit Trail")
public class AuditLogController {

    private final AuditLogQueryService service;

    @GetMapping
    @Operation(summary = "Search audit log entries, optionally filtered by entity name and id")
    public ApiResponse<Page<AuditLogResponse>> search(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) UUID entityId,
            Pageable pageable) {
        return ApiResponse.ok(service.search(entityName, entityId, pageable));
    }
}
