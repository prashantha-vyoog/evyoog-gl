package com.evyoog.gl.wizard.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ValidationException;
import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.enterprise.domain.EsMode;
import com.evyoog.gl.enterprise.domain.SegmentType;
import com.evyoog.gl.enterprise.dto.BusinessGroupResponse;
import com.evyoog.gl.enterprise.dto.BusinessUnitResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessGroupRequest;
import com.evyoog.gl.enterprise.dto.CreateBusinessUnitRequest;
import com.evyoog.gl.enterprise.dto.CreateLegalEntityRequest;
import com.evyoog.gl.enterprise.dto.LegalEntityResponse;
import com.evyoog.gl.enterprise.repository.ConsumptionContextRepository;
import com.evyoog.gl.enterprise.service.BusinessGroupService;
import com.evyoog.gl.enterprise.service.BusinessUnitService;
import com.evyoog.gl.enterprise.service.LegalEntityService;
import com.evyoog.gl.wizard.domain.BusinessType;
import com.evyoog.gl.wizard.domain.IndianState;
import com.evyoog.gl.wizard.domain.LegalStructure;
import com.evyoog.gl.wizard.dto.ProvisionedBusinessUnit;
import com.evyoog.gl.wizard.dto.WizardAnswersRequest;
import com.evyoog.gl.wizard.dto.WizardProvisioningResponse;
import com.evyoog.gl.wizard.dto.WizardStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SetupWizardService {

    private final ConsumptionContextRepository consumptionContextRepository;
    private final BusinessGroupService businessGroupService;
    private final LegalEntityService legalEntityService;
    private final BusinessUnitService businessUnitService;
    private final AuditService auditService;

    @Transactional
    public WizardProvisioningResponse run(WizardAnswersRequest request, String performedBy) {
        ConsumptionContext context = findContextOrThrow(request.contextId());

        if (context.getProvisioningAnswers() != null) {
            throw new EvyoogException("WIZARD_ALREADY_RUN",
                    "Setup Wizard has already been completed for this context.");
        }

        if (context.getSegmentType() == SegmentType.SESSION && request.sessionPurpose() == null) {
            throw new ValidationException("SESSION_PURPOSE_REQUIRED",
                    "Session purpose is required for Session contexts.", "sessionPurpose");
        }

        EsMode esMode = deriveEsMode(request.businessType());
        AccountingStandard accountingStandard = deriveAccountingStandard(request.legalStructure());

        BusinessGroupResponse businessGroup = businessGroupService.create(
                new CreateBusinessGroupRequest(
                        context.getId(),
                        "BG-" + context.getCode(),
                        request.companyName(),
                        esMode,
                        "INR"),
                performedBy);

        LegalEntityResponse legalEntity = legalEntityService.create(
                new CreateLegalEntityRequest(
                        businessGroup.id(),
                        "LE-" + context.getCode(),
                        request.companyName(),
                        accountingStandard,
                        null),
                performedBy);

        List<ProvisionedBusinessUnit> businessUnits = request.states().stream()
                .map(state -> provisionBusinessUnit(legalEntity.id(), request.companyName(), state, performedBy))
                .toList();

        Instant completedAt = Instant.now();
        Map<String, Object> provisioningAnswers = buildProvisioningAnswers(
                request, esMode, accountingStandard, businessGroup, legalEntity, businessUnits, completedAt);

        context.setProvisioningAnswers(provisioningAnswers);
        context.setUpdatedBy(performedBy);
        ConsumptionContext saved = consumptionContextRepository.saveAndFlush(context);
        auditService.log(AuditAction.UPDATE, "consumption_context", saved.getId(),
                null, provisioningAnswers, performedBy);

        return new WizardProvisioningResponse(
                context.getId(),
                businessGroup.id(),
                businessGroup.name(),
                esMode,
                legalEntity.id(),
                legalEntity.name(),
                accountingStandard,
                businessUnits,
                request.startMonth(),
                request.startYear(),
                request.hasOwnCoa(),
                request.industryTemplate(),
                request.sessionPurpose(),
                completedAt);
    }

    @Transactional(readOnly = true)
    public WizardStatusResponse getStatus(UUID contextId) {
        ConsumptionContext context = findContextOrThrow(contextId);
        Map<String, Object> answers = context.getProvisioningAnswers();
        boolean completed = answers != null;
        Instant completedAt = completed && answers.get("wizardCompletedAt") != null
                ? Instant.parse((String) answers.get("wizardCompletedAt"))
                : null;

        return new WizardStatusResponse(context.getId(), completed, completedAt, answers);
    }

    private ProvisionedBusinessUnit provisionBusinessUnit(UUID legalEntityId, String companyName,
                                                           IndianState state, String performedBy) {
        BusinessUnitResponse businessUnit = businessUnitService.create(
                new CreateBusinessUnitRequest(
                        legalEntityId,
                        "BU-" + state.getStateCode() + "-001",
                        companyName + " — " + state.getStateName(),
                        null,
                        state.getStateCode()),
                performedBy);

        return new ProvisionedBusinessUnit(
                businessUnit.id(), businessUnit.code(), businessUnit.name(), state.getStateName(), state.getStateCode());
    }

    private Map<String, Object> buildProvisioningAnswers(WizardAnswersRequest request, EsMode esMode,
                                                          AccountingStandard accountingStandard,
                                                          BusinessGroupResponse businessGroup,
                                                          LegalEntityResponse legalEntity,
                                                          List<ProvisionedBusinessUnit> businessUnits,
                                                          Instant completedAt) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("companyName", request.companyName());
        answers.put("legalStructure", request.legalStructure().name());
        answers.put("businessType", request.businessType().name());
        answers.put("esMode", esMode.name());
        answers.put("accountingStandard", accountingStandard.name());
        answers.put("states", request.states().stream().map(Enum::name).toList());
        answers.put("fiscalStartMonth", request.startMonth());
        answers.put("fiscalStartYear", request.startYear());
        answers.put("hasOwnCoa", request.hasOwnCoa());
        answers.put("industryTemplate", request.industryTemplate());
        answers.put("sessionPurpose", request.sessionPurpose() != null ? request.sessionPurpose().name() : null);
        answers.put("wizardCompletedAt", completedAt.toString());
        answers.put("businessGroupId", businessGroup.id().toString());
        answers.put("legalEntityId", legalEntity.id().toString());
        answers.put("businessUnitIds", businessUnits.stream().map(bu -> bu.businessUnitId().toString()).toList());
        return answers;
    }

    private EsMode deriveEsMode(BusinessType businessType) {
        return switch (businessType) {
            case MANUFACTURING, TRADING, MIXED -> EsMode.THICK_ES;
            case SERVICES, SIMPLE -> EsMode.THIN_ES;
        };
    }

    private AccountingStandard deriveAccountingStandard(LegalStructure structure) {
        return switch (structure) {
            case PRIVATE_LIMITED, PUBLIC_LIMITED -> AccountingStandard.IND_AS;
            case LLP, PROPRIETORSHIP, PARTNERSHIP -> AccountingStandard.IGAAP;
        };
    }

    private ConsumptionContext findContextOrThrow(UUID contextId) {
        return consumptionContextRepository.findById(contextId)
                .orElseThrow(() -> new EvyoogException("CONTEXT_NOT_FOUND",
                        "Consumption context not found: " + contextId, HttpStatus.NOT_FOUND));
    }
}
