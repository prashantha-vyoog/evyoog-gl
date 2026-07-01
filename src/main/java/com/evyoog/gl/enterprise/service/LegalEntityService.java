package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.enterprise.domain.BusinessGroup;
import com.evyoog.gl.enterprise.domain.EsMode;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.dto.CreateLegalEntityRequest;
import com.evyoog.gl.enterprise.dto.LegalEntityResponse;
import com.evyoog.gl.enterprise.dto.UpdateLegalEntityRequest;
import com.evyoog.gl.enterprise.mapper.LegalEntityMapper;
import com.evyoog.gl.enterprise.repository.BusinessGroupRepository;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegalEntityService {

    private final LegalEntityRepository repository;
    private final BusinessGroupRepository businessGroupRepository;
    private final LegalEntityMapper mapper;
    private final AuditService auditService;

    @Transactional
    public LegalEntityResponse create(CreateLegalEntityRequest request, String performedBy) {
        BusinessGroup businessGroup = businessGroupRepository.findById(request.businessGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("BusinessGroup", request.businessGroupId()));

        if (businessGroup.getEsMode() == EsMode.THIN_ES) {
            long count = repository.countByBusinessGroupId(businessGroup.getId());
            if (count >= 1) {
                throw new EvyoogException("THIN_ES_LE_LIMIT",
                        "Thin ES Business Groups support exactly one Legal Entity.",
                        HttpStatus.CONFLICT, "businessGroupId");
            }
        }

        if (repository.existsByBusinessGroupIdAndCode(businessGroup.getId(), request.code())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_CODE", "A legal entity with code '" + request.code() + "' already exists in this business group.", "code");
        }

        LegalEntity entity = mapper.toEntity(request);
        entity.setBusinessGroup(businessGroup);
        entity.setAccountingStandard(request.accountingStandard() != null ? request.accountingStandard() : AccountingStandard.IND_AS);
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        LegalEntity saved = repository.saveAndFlush(entity);
        LegalEntityResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "legal_entity", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional
    public LegalEntityResponse update(UUID id, UpdateLegalEntityRequest request, String performedBy) {
        LegalEntity entity = findOrThrow(id);
        LegalEntityResponse before = mapper.toResponse(entity);

        mapper.updateFromRequest(request, entity);
        entity.setUpdatedBy(performedBy);

        LegalEntity saved = repository.saveAndFlush(entity);
        LegalEntityResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.UPDATE, "legal_entity", saved.getId(), before, response, performedBy);

        return response;
    }

    @Transactional(readOnly = true)
    public LegalEntityResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LegalEntityResponse> list(UUID businessGroupId) {
        List<LegalEntity> entities = businessGroupId != null
                ? repository.findByBusinessGroupId(businessGroupId)
                : repository.findAll();
        return entities.stream().map(mapper::toResponse).toList();
    }

    private LegalEntity findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LegalEntity", id));
    }
}
