package com.evyoog.gl.coa.api;

import com.evyoog.gl.coa.dto.ProvisioningTemplateResponse;
import com.evyoog.gl.coa.service.ChartOfAccountsService;
import com.evyoog.gl.coa.service.ProvisioningTemplateService;
import com.evyoog.gl.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gl/provisioning-templates")
@RequiredArgsConstructor
@Tag(name = "GL-05 Provisioning Templates")
public class ProvisioningTemplateController {

    private final ProvisioningTemplateService templateService;
    private final ChartOfAccountsService chartOfAccountsService;

    @GetMapping
    @Operation(summary = "List active provisioning templates")
    public ApiResponse<List<ProvisioningTemplateResponse>> list() {
        return ApiResponse.ok(templateService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a provisioning template by id")
    public ApiResponse<ProvisioningTemplateResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(templateService.getById(id));
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply a provisioning template's accounts to a Ledger's Natural Account dimension")
    public ApiResponse<Map<String, Integer>> apply(
            @PathVariable UUID id,
            @RequestParam UUID ledgerId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        int created = chartOfAccountsService.applyTemplate(id, ledgerId, userId);
        return ApiResponse.ok(Map.of("accountsCreated", created));
    }
}
