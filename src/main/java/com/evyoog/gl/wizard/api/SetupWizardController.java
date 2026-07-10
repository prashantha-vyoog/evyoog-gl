package com.evyoog.gl.wizard.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.wizard.domain.IndianState;
import com.evyoog.gl.wizard.dto.IndianStateResponse;
import com.evyoog.gl.wizard.dto.WizardAnswersRequest;
import com.evyoog.gl.wizard.dto.WizardProvisioningResponse;
import com.evyoog.gl.wizard.dto.WizardStatusResponse;
import com.evyoog.gl.wizard.service.SetupWizardService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gl/setup-wizard")
@RequiredArgsConstructor
@Tag(name = "GL-02 Setup Wizard")
public class SetupWizardController {

    private final SetupWizardService service;

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run the Setup Wizard for a Consumption Context, provisioning the enterprise hierarchy")
    public ApiResponse<WizardProvisioningResponse> run(
            @Valid @RequestBody WizardAnswersRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ApiResponse.created(service.run(request, userId));
    }

    @GetMapping("/status/{contextId}")
    @Operation(summary = "Get the Setup Wizard completion status for a Consumption Context")
    public ApiResponse<WizardStatusResponse> status(@PathVariable UUID contextId) {
        return ApiResponse.ok(service.getStatus(contextId));
    }

    @GetMapping("/states")
    @Operation(summary = "List all Indian states with their GST state codes")
    public ApiResponse<List<IndianStateResponse>> states() {
        List<IndianStateResponse> response = Arrays.stream(IndianState.values())
                .map(state -> new IndianStateResponse(state.name(), state.getStateName(), state.getStateCode()))
                .toList();
        return ApiResponse.ok(response);
    }
}
