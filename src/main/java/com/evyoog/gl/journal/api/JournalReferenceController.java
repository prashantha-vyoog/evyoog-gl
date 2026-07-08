package com.evyoog.gl.journal.api;

import com.evyoog.gl.common.response.ApiResponse;
import com.evyoog.gl.journal.dto.JournalCategoryResponse;
import com.evyoog.gl.journal.dto.JournalSourceResponse;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL-11 Manual Journal Entry — Reference Data")
public class JournalReferenceController {

    private final JournalSourceRepository journalSourceRepository;
    private final JournalCategoryRepository journalCategoryRepository;

    @GetMapping("/api/v1/gl/journal-sources")
    @PreAuthorize("hasAuthority('gl:journal:view')")
    @Operation(summary = "List active journal sources")
    public ApiResponse<List<JournalSourceResponse>> listSources() {
        List<JournalSourceResponse> sources = journalSourceRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(s -> new JournalSourceResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getRequiresApproval()))
                .toList();
        return ApiResponse.ok(sources);
    }

    @GetMapping("/api/v1/gl/journal-categories")
    @PreAuthorize("hasAuthority('gl:journal:view')")
    @Operation(summary = "List active journal categories")
    public ApiResponse<List<JournalCategoryResponse>> listCategories() {
        List<JournalCategoryResponse> categories = journalCategoryRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(c -> new JournalCategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription()))
                .toList();
        return ApiResponse.ok(categories);
    }
}
