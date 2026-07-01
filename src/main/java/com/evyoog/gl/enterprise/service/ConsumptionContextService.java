package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.enterprise.dto.ConsumptionContextResponse;
import com.evyoog.gl.enterprise.dto.CreateConsumptionContextRequest;
import com.evyoog.gl.enterprise.mapper.ConsumptionContextMapper;
import com.evyoog.gl.enterprise.repository.ConsumptionContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsumptionContextService {

    private final ConsumptionContextRepository repository;
    private final ConsumptionContextMapper mapper;
    private final AuditService auditService;

    @Transactional
    public ConsumptionContextResponse create(CreateConsumptionContextRequest request, String performedBy) {
        if (repository.existsByCode(request.code())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_CODE", "A consumption context with code '" + request.code() + "' already exists.", "code");
        }

        ConsumptionContext entity = mapper.toEntity(request);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        ConsumptionContext saved = repository.saveAndFlush(entity);
        ConsumptionContextResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "consumption_context", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional(readOnly = true)
    public ConsumptionContextResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ConsumptionContextResponse> list() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    private ConsumptionContext findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConsumptionContext", id));
    }
}
