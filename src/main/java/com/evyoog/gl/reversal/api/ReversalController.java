package com.evyoog.gl.reversal.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.reversal.dto.ReversalRequest;
import com.evyoog.gl.reversal.dto.ReversalResponse;
import com.evyoog.gl.reversal.service.ReversalService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-13 Journal Reversal")
public class ReversalController {

    private final ReversalService service;

    @PostMapping("/api/v1/gl/journals/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('gl:journal:reverse')")
    @Operation(summary = "Reverse a POSTED journal — creates a new balanced journal with all DR/CR lines flipped")
    public ApiResponse<ReversalResponse> reverse(
            @PathVariable UUID id,
            @Valid @RequestBody ReversalRequest request) {
        return ApiResponse.created(service.reverse(id, request));
    }

    @GetMapping("/api/v1/gl/journals/{id}/reversal")
    @PreAuthorize("hasAuthority('gl:journal:view')")
    @Operation(summary = "Get the reversal pair for a journal")
    public ApiResponse<ReversalResponse> getReversalDetails(@PathVariable UUID id) {
        return ApiResponse.ok(service.getReversalDetails(id));
    }
}
