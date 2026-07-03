package com.evyoog.gl.coa.service;

import com.evyoog.gl.coa.domain.ProvisioningTemplate;
import com.evyoog.gl.coa.dto.ProvisioningTemplateResponse;
import com.evyoog.gl.coa.mapper.ProvisioningTemplateMapper;
import com.evyoog.gl.coa.repository.ProvisioningTemplateRepository;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProvisioningTemplateService {

    private final ProvisioningTemplateRepository repository;
    private final ProvisioningTemplateMapper mapper;

    @Transactional(readOnly = true)
    public List<ProvisioningTemplateResponse> list() {
        return repository.findByIsActiveTrue().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProvisioningTemplateResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    private ProvisioningTemplate findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProvisioningTemplate", id));
    }
}
