package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.BusinessUnit;
import com.evyoog.gl.enterprise.domain.InventoryOrganisation;
import com.evyoog.gl.enterprise.dto.CreateInventoryOrganisationRequest;
import com.evyoog.gl.enterprise.dto.InventoryOrganisationResponse;
import com.evyoog.gl.enterprise.mapper.InventoryOrganisationMapper;
import com.evyoog.gl.enterprise.repository.BusinessUnitRepository;
import com.evyoog.gl.enterprise.repository.InventoryOrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryOrganisationService {

    private final InventoryOrganisationRepository repository;
    private final BusinessUnitRepository businessUnitRepository;
    private final InventoryOrganisationMapper mapper;
    private final AuditService auditService;

    @Transactional
    public InventoryOrganisationResponse create(CreateInventoryOrganisationRequest request, String performedBy) {
        BusinessUnit businessUnit = businessUnitRepository.findById(request.businessUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("BusinessUnit", request.businessUnitId()));

        if (repository.existsByBusinessUnitIdAndCode(businessUnit.getId(), request.code())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_CODE", "An inventory organisation with code '" + request.code() + "' already exists in this business unit.", "code");
        }

        InventoryOrganisation entity = mapper.toEntity(request);
        entity.setBusinessUnit(businessUnit);
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        InventoryOrganisation saved = repository.saveAndFlush(entity);
        InventoryOrganisationResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "inventory_organisation", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional(readOnly = true)
    public InventoryOrganisationResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<InventoryOrganisationResponse> list(UUID businessUnitId) {
        List<InventoryOrganisation> entities = businessUnitId != null
                ? repository.findByBusinessUnitId(businessUnitId)
                : repository.findAll();
        return entities.stream().map(mapper::toResponse).toList();
    }

    private InventoryOrganisation findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryOrganisation", id));
    }
}
