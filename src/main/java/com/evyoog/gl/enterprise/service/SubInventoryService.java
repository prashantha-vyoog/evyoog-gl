package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.InventoryOrganisation;
import com.evyoog.gl.enterprise.domain.SubInventory;
import com.evyoog.gl.enterprise.dto.CreateSubInventoryRequest;
import com.evyoog.gl.enterprise.dto.SubInventoryResponse;
import com.evyoog.gl.enterprise.mapper.SubInventoryMapper;
import com.evyoog.gl.enterprise.repository.InventoryOrganisationRepository;
import com.evyoog.gl.enterprise.repository.SubInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubInventoryService {

    private final SubInventoryRepository repository;
    private final InventoryOrganisationRepository inventoryOrganisationRepository;
    private final SubInventoryMapper mapper;
    private final AuditService auditService;

    @Transactional
    public SubInventoryResponse create(CreateSubInventoryRequest request, String performedBy) {
        InventoryOrganisation inventoryOrganisation = inventoryOrganisationRepository.findById(request.inventoryOrganisationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryOrganisation", request.inventoryOrganisationId()));

        if (repository.existsByInventoryOrganisationIdAndCode(inventoryOrganisation.getId(), request.code())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_CODE", "A sub-inventory with code '" + request.code() + "' already exists in this inventory organisation.", "code");
        }

        SubInventory entity = mapper.toEntity(request);
        entity.setInventoryOrganisation(inventoryOrganisation);
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        SubInventory saved = repository.saveAndFlush(entity);
        SubInventoryResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "sub_inventory", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional(readOnly = true)
    public SubInventoryResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SubInventoryResponse> list(UUID inventoryOrganisationId) {
        List<SubInventory> entities = inventoryOrganisationId != null
                ? repository.findByInventoryOrganisationId(inventoryOrganisationId)
                : repository.findAll();
        return entities.stream().map(mapper::toResponse).toList();
    }

    private SubInventory findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubInventory", id));
    }
}
