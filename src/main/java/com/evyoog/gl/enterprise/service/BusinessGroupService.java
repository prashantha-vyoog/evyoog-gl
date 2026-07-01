package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.BusinessGroup;
import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.enterprise.dto.BusinessGroupResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessGroupRequest;
import com.evyoog.gl.enterprise.mapper.BusinessGroupMapper;
import com.evyoog.gl.enterprise.repository.BusinessGroupRepository;
import com.evyoog.gl.enterprise.repository.ConsumptionContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessGroupService {

    private final BusinessGroupRepository repository;
    private final ConsumptionContextRepository consumptionContextRepository;
    private final BusinessGroupMapper mapper;
    private final AuditService auditService;

    @Transactional
    public BusinessGroupResponse create(CreateBusinessGroupRequest request, String performedBy) {
        ConsumptionContext context = consumptionContextRepository.findById(request.consumptionContextId())
                .orElseThrow(() -> new ResourceNotFoundException("ConsumptionContext", request.consumptionContextId()));

        if (repository.existsByConsumptionContextIdAndCode(context.getId(), request.code())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_CODE", "A business group with code '" + request.code() + "' already exists in this context.", "code");
        }

        BusinessGroup entity = mapper.toEntity(request);
        entity.setConsumptionContext(context);
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        BusinessGroup saved = repository.saveAndFlush(entity);
        BusinessGroupResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "business_group", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional(readOnly = true)
    public BusinessGroupResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<BusinessGroupResponse> list(UUID consumptionContextId) {
        List<BusinessGroup> entities = consumptionContextId != null
                ? repository.findByConsumptionContextId(consumptionContextId)
                : repository.findAll();
        return entities.stream().map(mapper::toResponse).toList();
    }

    private BusinessGroup findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessGroup", id));
    }
}
