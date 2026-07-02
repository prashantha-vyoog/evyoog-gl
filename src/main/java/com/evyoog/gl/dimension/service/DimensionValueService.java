package com.evyoog.gl.dimension.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.common.exception.ValidationException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionType;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.domain.FinanceDimension;
import com.evyoog.gl.dimension.domain.NormalBalance;
import com.evyoog.gl.dimension.dto.CreateDimensionValueRequest;
import com.evyoog.gl.dimension.dto.DimensionValueResponse;
import com.evyoog.gl.dimension.dto.UpdateDimensionValueRequest;
import com.evyoog.gl.dimension.mapper.DimensionValueMapper;
import com.evyoog.gl.dimension.repository.DimensionValueRepository;
import com.evyoog.gl.dimension.repository.FinanceDimensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DimensionValueService {

    private static final Map<AccountQualifier, NormalBalance> DEFAULT_NORMAL_BALANCE = Map.of(
            AccountQualifier.ASSET, NormalBalance.DR,
            AccountQualifier.LIABILITY, NormalBalance.CR,
            AccountQualifier.EQUITY, NormalBalance.CR,
            AccountQualifier.REVENUE, NormalBalance.CR,
            AccountQualifier.EXPENSE, NormalBalance.DR,
            AccountQualifier.BUDGETARY, NormalBalance.DR
    );

    private final DimensionValueRepository repository;
    private final FinanceDimensionRepository financeDimensionRepository;
    private final DimensionValueMapper mapper;
    private final AuditService auditService;

    @Transactional
    public DimensionValueResponse create(CreateDimensionValueRequest request, String performedBy) {
        FinanceDimension financeDimension = financeDimensionRepository.findById(request.financeDimensionId())
                .orElseThrow(() -> new ResourceNotFoundException("FinanceDimension", request.financeDimensionId()));

        if (repository.existsByFinanceDimensionIdAndCode(financeDimension.getId(), request.code())) {
            throw new DuplicateResourceException("DUPLICATE_DIMENSION_VALUE_CODE",
                    "A Dimension Value with code '" + request.code() + "' already exists for this Finance Dimension.",
                    "code");
        }

        boolean isNaturalAccount = financeDimension.getDimensionType() == DimensionType.NATURAL_ACCOUNT;
        if (isNaturalAccount && request.accountQualifier() == null) {
            throw new ValidationException("ACCOUNT_QUALIFIER_REQUIRED",
                    "Account qualifier is required for Natural Account dimension values. " +
                            "Valid values: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, BUDGETARY.",
                    "accountQualifier");
        }

        DimensionValue parent = null;
        if (request.parentValueId() != null) {
            parent = repository.findById(request.parentValueId())
                    .orElseThrow(() -> new ResourceNotFoundException("DimensionValue", request.parentValueId()));

            if (parent.getAccountQualifier() != request.accountQualifier()) {
                throw new EvyoogException("QUALIFIER_MISMATCH",
                        "A child account's qualifier must match its parent. " +
                                "Assets roll up to Assets, Expenses roll up to Expenses, etc.");
            }
        }

        DimensionValue entity = mapper.toEntity(request);
        entity.setFinanceDimension(financeDimension);
        entity.setParentValue(parent);
        entity.setSummary(request.isSummary() != null && request.isSummary());
        entity.setPostable(request.isPostable() == null || request.isPostable());
        entity.setGstApplicable(request.gstApplicable() != null && request.gstApplicable());
        entity.setTdsApplicable(request.tdsApplicable() != null && request.tdsApplicable());
        entity.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        entity.setNormalBalance(request.normalBalance() != null
                ? request.normalBalance()
                : deriveNormalBalance(request.accountQualifier()));
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        DimensionValue saved = repository.saveAndFlush(entity);
        DimensionValueResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.CREATE, "dimension_value", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional
    public DimensionValueResponse update(UUID id, UpdateDimensionValueRequest request, String performedBy) {
        DimensionValue entity = findOrThrow(id);
        DimensionValueResponse before = mapper.toResponse(entity);

        mapper.updateFromRequest(request, entity);
        if (request.isSummary() != null) {
            entity.setSummary(request.isSummary());
        }
        if (request.isPostable() != null) {
            entity.setPostable(request.isPostable());
        }
        entity.setUpdatedBy(performedBy);

        DimensionValue saved = repository.saveAndFlush(entity);
        DimensionValueResponse response = mapper.toResponse(saved);
        auditService.log(AuditAction.UPDATE, "dimension_value", saved.getId(), before, response, performedBy);

        return response;
    }

    @Transactional
    public void deactivate(UUID id, String performedBy) {
        DimensionValue entity = findOrThrow(id);
        DimensionValueResponse before = mapper.toResponse(entity);

        // Phase 1: GL-11 (Manual Journal Entry) does not exist yet, so there is no
        // journal line history that references this value. Once posting exists,
        // guard here against deactivating a Dimension Value in use.
        entity.setActive(false);
        entity.setUpdatedBy(performedBy);

        DimensionValue saved = repository.saveAndFlush(entity);
        auditService.log(AuditAction.DELETE, "dimension_value", saved.getId(), before, mapper.toResponse(saved), performedBy);
    }

    @Transactional(readOnly = true)
    public DimensionValueResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DimensionValueResponse> list(UUID financeDimensionId, UUID parentValueId) {
        List<DimensionValue> entities = parentValueId != null
                ? repository.findByFinanceDimensionIdAndParentValueId(financeDimensionId, parentValueId)
                : repository.findByFinanceDimensionId(financeDimensionId);
        return entities.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DimensionValueResponse> search(UUID ledgerId, String code) {
        return repository.findByLedgerIdAndCode(ledgerId, code).stream().map(mapper::toResponse).toList();
    }

    private NormalBalance deriveNormalBalance(AccountQualifier qualifier) {
        return qualifier == null ? null : DEFAULT_NORMAL_BALANCE.get(qualifier);
    }

    private DimensionValue findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DimensionValue", id));
    }
}
