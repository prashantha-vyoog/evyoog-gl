package com.evyoog.gl.wizard.service;

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
import com.evyoog.gl.wizard.domain.SessionPurpose;
import com.evyoog.gl.wizard.dto.WizardAnswersRequest;
import com.evyoog.gl.wizard.dto.WizardProvisioningResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupWizardServiceTest {

    @Mock
    private ConsumptionContextRepository consumptionContextRepository;
    @Mock
    private BusinessGroupService businessGroupService;
    @Mock
    private LegalEntityService legalEntityService;
    @Mock
    private BusinessUnitService businessUnitService;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private SetupWizardService service;

    private WizardAnswersRequest requestWith(UUID contextId, BusinessType businessType, LegalStructure legalStructure,
                                              List<IndianState> states, SessionPurpose sessionPurpose) {
        return new WizardAnswersRequest(
                contextId, "Coimbatore Manufacturing Pvt Ltd", legalStructure, businessType,
                states, 4, 2025, false, "MANUFACTURING", sessionPurpose);
    }

    private ConsumptionContext workspaceContext(UUID id) {
        ConsumptionContext context = ConsumptionContext.builder()
                .segmentType(SegmentType.WORKSPACE)
                .code("CTX-001")
                .name("Coimbatore Group")
                .status("ACTIVE")
                .build();
        context.setId(id);
        return context;
    }

    @Test
    void run_whenContextNotFound_shouldThrow404() {
        UUID contextId = UUID.randomUUID();
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.empty());

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.MANUFACTURING, LegalStructure.PRIVATE_LIMITED,
                List.of(IndianState.TAMIL_NADU), null);

        assertThatThrownBy(() -> service.run(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "CONTEXT_NOT_FOUND");
    }

    @Test
    void run_whenAlreadyProvisioned_shouldThrow409WizardAlreadyRun() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = workspaceContext(contextId);
        context.setProvisioningAnswers(Map.of("companyName", "Already Run Co"));
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.MANUFACTURING, LegalStructure.PRIVATE_LIMITED,
                List.of(IndianState.TAMIL_NADU), null);

        assertThatThrownBy(() -> service.run(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "WIZARD_ALREADY_RUN");
    }

    @Test
    void run_whenSessionContextMissingSessionPurpose_shouldThrow400() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = ConsumptionContext.builder()
                .segmentType(SegmentType.SESSION)
                .code("CTX-SESSION-001")
                .name("Trial Session")
                .status("ACTIVE")
                .build();
        context.setId(contextId);
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.MANUFACTURING, LegalStructure.PRIVATE_LIMITED,
                List.of(IndianState.TAMIL_NADU), null);

        assertThatThrownBy(() -> service.run(request, "prashanth"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_PURPOSE_REQUIRED");
    }

    @Test
    void run_manufacturingAndPrivateLimited_shouldDeriveThickEsAndIndAs() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = workspaceContext(contextId);
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        stubProvisioning(contextId, List.of(IndianState.TAMIL_NADU));

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.MANUFACTURING, LegalStructure.PRIVATE_LIMITED,
                List.of(IndianState.TAMIL_NADU), null);

        WizardProvisioningResponse response = service.run(request, "prashanth");

        assertThat(response.esMode()).isEqualTo(EsMode.THICK_ES);
        assertThat(response.accountingStandard()).isEqualTo(AccountingStandard.IND_AS);
    }

    @Test
    void run_servicesAndProprietorship_shouldDeriveThinEsAndIgaap() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = workspaceContext(contextId);
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        stubProvisioning(contextId, List.of(IndianState.KARNATAKA));

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.SERVICES, LegalStructure.PROPRIETORSHIP,
                List.of(IndianState.KARNATAKA), null);

        WizardProvisioningResponse response = service.run(request, "prashanth");

        assertThat(response.esMode()).isEqualTo(EsMode.THIN_ES);
        assertThat(response.accountingStandard()).isEqualTo(AccountingStandard.IGAAP);
    }

    @Test
    void run_withMultipleStates_shouldCreateOneBusinessUnitPerState() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = workspaceContext(contextId);
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        List<IndianState> states = List.of(IndianState.TAMIL_NADU, IndianState.MAHARASHTRA, IndianState.KARNATAKA);
        stubProvisioning(contextId, states);

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.TRADING, LegalStructure.LLP, states, null);

        WizardProvisioningResponse response = service.run(request, "prashanth");

        assertThat(response.businessUnits()).hasSize(3);
        verify(businessUnitService, org.mockito.Mockito.times(3))
                .create(any(CreateBusinessUnitRequest.class), ArgumentMatchers.eq("prashanth"));
    }

    @Test
    void run_onSuccess_shouldPersistProvisioningAnswersOnContext() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = workspaceContext(contextId);
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        stubProvisioning(contextId, List.of(IndianState.TAMIL_NADU));

        WizardAnswersRequest request = requestWith(
                contextId, BusinessType.MANUFACTURING, LegalStructure.PRIVATE_LIMITED,
                List.of(IndianState.TAMIL_NADU), null);

        service.run(request, "prashanth");

        assertThat(context.getProvisioningAnswers()).isNotNull();
        assertThat(context.getProvisioningAnswers().get("companyName")).isEqualTo("Coimbatore Manufacturing Pvt Ltd");
    }

    private void stubProvisioning(UUID contextId, List<IndianState> states) {
        UUID businessGroupId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        Instant now = Instant.now();

        when(consumptionContextRepository.saveAndFlush(any(ConsumptionContext.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessGroupResponse businessGroupResponse = new BusinessGroupResponse(
                businessGroupId, contextId, "BG-CTX-001", "Coimbatore Manufacturing Pvt Ltd",
                EsMode.THICK_ES, "INR", true, now, now);
        when(businessGroupService.create(any(CreateBusinessGroupRequest.class), any())).thenReturn(businessGroupResponse);

        LegalEntityResponse legalEntityResponse = new LegalEntityResponse(
                legalEntityId, businessGroupId, "LE-CTX-001", "Coimbatore Manufacturing Pvt Ltd",
                AccountingStandard.IND_AS, null, true, now, now);
        when(legalEntityService.create(any(CreateLegalEntityRequest.class), any())).thenReturn(legalEntityResponse);

        for (IndianState state : states) {
            BusinessUnitResponse businessUnitResponse = new BusinessUnitResponse(
                    UUID.randomUUID(), legalEntityId, "BU-" + state.getStateCode() + "-001",
                    "Coimbatore Manufacturing Pvt Ltd — " + state.getStateName(), null, state.getStateCode(),
                    true, now, now);
            when(businessUnitService.create(
                    ArgumentMatchers.argThat(req -> req != null && req.stateCode().equals(state.getStateCode())),
                    any()))
                    .thenReturn(businessUnitResponse);
        }
    }
}
