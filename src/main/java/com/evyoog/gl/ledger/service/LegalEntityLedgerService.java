package com.evyoog.gl.ledger.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.BusinessGroup;
import com.evyoog.gl.enterprise.domain.EsMode;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.domain.LedgerCategory;
import com.evyoog.gl.ledger.domain.LegalEntityLedger;
import com.evyoog.gl.ledger.dto.CreateLegalEntityLedgerRequest;
import com.evyoog.gl.ledger.dto.LegalEntityLedgerResponse;
import com.evyoog.gl.ledger.mapper.LegalEntityLedgerMapper;
import com.evyoog.gl.ledger.repository.LedgerRepository;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegalEntityLedgerService {

    private final LegalEntityLedgerRepository repository;
    private final LegalEntityRepository legalEntityRepository;
    private final LedgerRepository ledgerRepository;
    private final LegalEntityLedgerMapper mapper;
    private final AuditService auditService;

    @Transactional
    public LegalEntityLedgerResponse create(CreateLegalEntityLedgerRequest request, String performedBy) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.legalEntityId())
                .orElseThrow(() -> new ResourceNotFoundException("LegalEntity", request.legalEntityId()));
        Ledger ledger = ledgerRepository.findById(request.ledgerId())
                .orElseThrow(() -> new ResourceNotFoundException("Ledger", request.ledgerId()));

        LedgerCategory category = request.ledgerCategory() != null ? request.ledgerCategory() : LedgerCategory.PRIMARY;

        if (category == LedgerCategory.PRIMARY) {
            boolean hasPrimary = repository.existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(
                    legalEntity.getId(), LedgerCategory.PRIMARY);
            if (hasPrimary) {
                throw new EvyoogException("PRIMARY_LEDGER_EXISTS",
                        "This Legal Entity already has a PRIMARY Ledger assigned. " +
                                "A Legal Entity may have exactly one PRIMARY Ledger.",
                        HttpStatus.CONFLICT, "legalEntityId");
            }
        }

        BusinessGroup businessGroup = legalEntity.getBusinessGroup();
        if (businessGroup.getEsMode() == EsMode.THIN_ES && ledger.getFinanceMode() == FinanceMode.THICK) {
            throw new EvyoogException("THIN_ES_THICK_MODE_CONFLICT",
                    "A Thin ES Legal Entity cannot be assigned a THICK mode Ledger. " +
                            "Thin ES supports THIN or EVENT_ONLY finance modes only.",
                    HttpStatus.CONFLICT, "ledgerId");
        }

        LegalEntityLedger entity = LegalEntityLedger.builder()
                .legalEntity(legalEntity)
                .ledger(ledger)
                .ledgerCategory(category)
                .build();
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        LegalEntityLedger saved = repository.saveAndFlush(entity);
        LegalEntityLedgerResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "legal_entity_ledger", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional
    public void deactivate(UUID id, String performedBy) {
        LegalEntityLedger entity = findOrThrow(id);
        LegalEntityLedgerResponse before = mapper.toResponse(entity);

        entity.setActive(false);
        entity.setUpdatedBy(performedBy);

        LegalEntityLedger saved = repository.saveAndFlush(entity);
        auditService.log(AuditAction.DELETE, "legal_entity_ledger", saved.getId(), before, mapper.toResponse(saved), performedBy);
    }

    @Transactional(readOnly = true)
    public LegalEntityLedgerResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LegalEntityLedgerResponse> list(UUID legalEntityId, UUID ledgerId) {
        List<LegalEntityLedger> entities;
        if (legalEntityId != null) {
            entities = repository.findByLegalEntityId(legalEntityId);
        } else if (ledgerId != null) {
            entities = repository.findByLedgerId(ledgerId);
        } else {
            entities = repository.findAll();
        }
        return entities.stream().map(mapper::toResponse).toList();
    }

    private LegalEntityLedger findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LegalEntityLedger", id));
    }
}
