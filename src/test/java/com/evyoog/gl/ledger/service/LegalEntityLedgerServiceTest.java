package com.evyoog.gl.ledger.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.enterprise.domain.AccountingStandard;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalEntityLedgerServiceTest {

    @Mock
    private LegalEntityLedgerRepository repository;
    @Mock
    private LegalEntityRepository legalEntityRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private LegalEntityLedgerMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private LegalEntityLedgerService service;

    private LegalEntity legalEntity(EsMode esMode) {
        BusinessGroup bg = BusinessGroup.builder().esMode(esMode).build();
        bg.setId(UUID.randomUUID());
        LegalEntity le = LegalEntity.builder().businessGroup(bg).code("LE-001").name("LE")
                .accountingStandard(AccountingStandard.IND_AS).build();
        le.setId(UUID.randomUUID());
        return le;
    }

    private Ledger ledger(FinanceMode financeMode) {
        Ledger ledger = Ledger.builder().code("LDG-001").name("Ledger").financeMode(financeMode)
                .ledgerCategory(LedgerCategory.PRIMARY).functionalCurrency("INR").accountingStandard(AccountingStandard.IND_AS).build();
        ledger.setId(UUID.randomUUID());
        return ledger;
    }

    private void stubResponse(LegalEntityLedger saved) {
        LegalEntityLedgerResponse response = new LegalEntityLedgerResponse(
                saved.getId(), saved.getLegalEntity().getId(), saved.getLegalEntity().getName(),
                saved.getLedger().getId(), saved.getLedger().getName(), saved.getLedger().getCode(),
                saved.getLedger().getFinanceMode(), saved.getLedgerCategory(), saved.isActive(), Instant.now());
        when(mapper.toResponse(saved)).thenReturn(response);
    }

    @Test
    void assignLedger_primarySuccess() {
        LegalEntity le = legalEntity(EsMode.THICK_ES);
        Ledger ledger = ledger(FinanceMode.THICK);
        CreateLegalEntityLedgerRequest request = new CreateLegalEntityLedgerRequest(le.getId(), ledger.getId(), LedgerCategory.PRIMARY);

        when(legalEntityRepository.findById(le.getId())).thenReturn(Optional.of(le));
        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(le.getId(), LedgerCategory.PRIMARY)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            LegalEntityLedger arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            LegalEntityLedger saved = inv.getArgument(0);
            return new LegalEntityLedgerResponse(saved.getId(), le.getId(), le.getName(), ledger.getId(), ledger.getName(),
                    ledger.getCode(), ledger.getFinanceMode(), saved.getLedgerCategory(), saved.isActive(), Instant.now());
        });

        LegalEntityLedgerResponse result = service.create(request, "prashanth");

        assertThat(result.ledgerCategory()).isEqualTo(LedgerCategory.PRIMARY);
        assertThat(result.financeMode()).isEqualTo(FinanceMode.THICK);
    }

    @Test
    void assignLedger_secondPrimary_shouldThrow409() {
        LegalEntity le = legalEntity(EsMode.THICK_ES);
        Ledger ledger = ledger(FinanceMode.THICK);
        CreateLegalEntityLedgerRequest request = new CreateLegalEntityLedgerRequest(le.getId(), ledger.getId(), LedgerCategory.PRIMARY);

        when(legalEntityRepository.findById(le.getId())).thenReturn(Optional.of(le));
        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(le.getId(), LedgerCategory.PRIMARY)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PRIMARY_LEDGER_EXISTS");
    }

    @Test
    void assignLedger_thinEsWithThickLedger_shouldThrow409() {
        LegalEntity le = legalEntity(EsMode.THIN_ES);
        Ledger ledger = ledger(FinanceMode.THICK);
        CreateLegalEntityLedgerRequest request = new CreateLegalEntityLedgerRequest(le.getId(), ledger.getId(), LedgerCategory.PRIMARY);

        when(legalEntityRepository.findById(le.getId())).thenReturn(Optional.of(le));
        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(le.getId(), LedgerCategory.PRIMARY)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THIN_ES_THICK_MODE_CONFLICT");
    }

    @Test
    void assignLedger_thinEsWithThinLedger_shouldSucceed() {
        LegalEntity le = legalEntity(EsMode.THIN_ES);
        Ledger ledger = ledger(FinanceMode.THIN);
        CreateLegalEntityLedgerRequest request = new CreateLegalEntityLedgerRequest(le.getId(), ledger.getId(), LedgerCategory.PRIMARY);

        when(legalEntityRepository.findById(le.getId())).thenReturn(Optional.of(le));
        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(le.getId(), LedgerCategory.PRIMARY)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            LegalEntityLedger arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            LegalEntityLedger saved = inv.getArgument(0);
            return new LegalEntityLedgerResponse(saved.getId(), le.getId(), le.getName(), ledger.getId(), ledger.getName(),
                    ledger.getCode(), ledger.getFinanceMode(), saved.getLedgerCategory(), saved.isActive(), Instant.now());
        });

        LegalEntityLedgerResponse result = service.create(request, "prashanth");

        assertThat(result.financeMode()).isEqualTo(FinanceMode.THIN);
    }

    @Test
    void assignLedger_thinEsWithEventOnlyLedger_shouldSucceed() {
        LegalEntity le = legalEntity(EsMode.THIN_ES);
        Ledger ledger = ledger(FinanceMode.EVENT_ONLY);
        CreateLegalEntityLedgerRequest request = new CreateLegalEntityLedgerRequest(le.getId(), ledger.getId(), LedgerCategory.PRIMARY);

        when(legalEntityRepository.findById(le.getId())).thenReturn(Optional.of(le));
        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(le.getId(), LedgerCategory.PRIMARY)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            LegalEntityLedger arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            LegalEntityLedger saved = inv.getArgument(0);
            return new LegalEntityLedgerResponse(saved.getId(), le.getId(), le.getName(), ledger.getId(), ledger.getName(),
                    ledger.getCode(), ledger.getFinanceMode(), saved.getLedgerCategory(), saved.isActive(), Instant.now());
        });

        LegalEntityLedgerResponse result = service.create(request, "prashanth");

        assertThat(result.financeMode()).isEqualTo(FinanceMode.EVENT_ONLY);
    }

    @Test
    void assignLedger_secondaryAssignment_shouldSucceed() {
        LegalEntity le = legalEntity(EsMode.THICK_ES);
        Ledger ledger = ledger(FinanceMode.THICK);
        CreateLegalEntityLedgerRequest request = new CreateLegalEntityLedgerRequest(le.getId(), ledger.getId(), LedgerCategory.SECONDARY);

        when(legalEntityRepository.findById(le.getId())).thenReturn(Optional.of(le));
        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            LegalEntityLedger arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            LegalEntityLedger saved = inv.getArgument(0);
            return new LegalEntityLedgerResponse(saved.getId(), le.getId(), le.getName(), ledger.getId(), ledger.getName(),
                    ledger.getCode(), ledger.getFinanceMode(), saved.getLedgerCategory(), saved.isActive(), Instant.now());
        });

        LegalEntityLedgerResponse result = service.create(request, "prashanth");

        assertThat(result.ledgerCategory()).isEqualTo(LedgerCategory.SECONDARY);
    }
}
