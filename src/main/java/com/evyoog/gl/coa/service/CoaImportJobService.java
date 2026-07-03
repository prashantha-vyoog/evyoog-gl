package com.evyoog.gl.coa.service;

import com.evyoog.gl.coa.domain.CoaImportJob;
import com.evyoog.gl.coa.dto.CoaImportJobResponse;
import com.evyoog.gl.coa.mapper.CoaImportJobMapper;
import com.evyoog.gl.coa.repository.CoaImportJobRepository;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * GET-only scaffold. GL-06 adds the POST that creates and processes import jobs.
 */
@Service
@RequiredArgsConstructor
public class CoaImportJobService {

    private final CoaImportJobRepository repository;
    private final CoaImportJobMapper mapper;

    @Transactional(readOnly = true)
    public CoaImportJobResponse getById(UUID id) {
        CoaImportJob entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CoaImportJob", id));
        return mapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CoaImportJobResponse> list(UUID ledgerId) {
        return repository.findByLedgerId(ledgerId).stream().map(mapper::toResponse).toList();
    }
}
