package com.evyoog.gl.calendar.service;

import com.evyoog.gl.calendar.domain.AccountingCalendar;
import com.evyoog.gl.calendar.domain.PeriodType;
import com.evyoog.gl.calendar.dto.AccountingCalendarResponse;
import com.evyoog.gl.calendar.dto.CreateCalendarRequest;
import com.evyoog.gl.calendar.mapper.AccountingCalendarMapper;
import com.evyoog.gl.calendar.repository.AccountingCalendarRepository;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LedgerRepository;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingCalendarServiceTest {

    @Mock
    private AccountingCalendarRepository repository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private LegalEntityLedgerRepository legalEntityLedgerRepository;
    @Mock
    private AccountingCalendarMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private AccountingCalendarService service;

    private Ledger ledger() {
        Ledger ledger = Ledger.builder().code("LDG").name("Test Ledger").build();
        ledger.setId(UUID.randomUUID());
        return ledger;
    }

    private AccountingCalendarResponse responseFor(AccountingCalendar entity) {
        return new AccountingCalendarResponse(entity.getId(), entity.getLedger().getId(), entity.getLedger().getName(),
                entity.getName(), entity.getDescription(), entity.getFiscalYearStartMonth(), entity.getFiscalYearStartDay(),
                entity.getPeriodType(), entity.getPeriodsPerYear(), entity.isActive(), null, null, Instant.now(), Instant.now());
    }

    @Test
    void createCalendar_success_defaultAprilStart() {
        Ledger ledger = ledger();
        CreateCalendarRequest request = new CreateCalendarRequest(ledger.getId(), "FY Calendar", null,
                null, null, null, 2025);
        AccountingCalendar entity = new AccountingCalendar();
        AccountingCalendar saved = AccountingCalendar.builder().name("FY Calendar").ledger(ledger).build();
        saved.setId(UUID.randomUUID());

        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLedgerIdAndIsActiveTrue(ledger.getId())).thenReturn(false);
        when(legalEntityLedgerRepository.findByLedgerId(ledger.getId())).thenReturn(List.of());
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(responseFor(saved));

        AccountingCalendarResponse result = service.create(request, "prashanth");

        assertThat(result.currentFiscalYear()).isEqualTo("2025-26");
        assertThat(result.generatedPeriodCount()).isEqualTo(12);
    }

    @Test
    void createCalendar_duplicateForSameLedger_throws409() {
        Ledger ledger = ledger();
        CreateCalendarRequest request = new CreateCalendarRequest(ledger.getId(), "FY Calendar", null,
                null, null, null, 2025);

        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLedgerIdAndIsActiveTrue(ledger.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "CALENDAR_EXISTS");
    }

    @Test
    void createCalendar_customStartMonth_january() {
        Ledger ledger = ledger();
        CreateCalendarRequest request = new CreateCalendarRequest(ledger.getId(), "FY Calendar", null,
                1, null, null, 2025);
        AccountingCalendar entity = new AccountingCalendar();
        AccountingCalendar saved = AccountingCalendar.builder().name("FY Calendar").ledger(ledger).build();
        saved.setId(UUID.randomUUID());

        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLedgerIdAndIsActiveTrue(ledger.getId())).thenReturn(false);
        when(legalEntityLedgerRepository.findByLedgerId(ledger.getId())).thenReturn(List.of());
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(responseFor(saved));

        AccountingCalendarResponse result = service.create(request, "prashanth");

        assertThat(result.currentFiscalYear()).isEqualTo("2025");
    }

    @Test
    void deriveFiscalYearName_aprilStart_returns2025_26() {
        assertThat(service.deriveFiscalYearName(4, 2025)).isEqualTo("2025-26");
    }

    @Test
    void deriveFiscalYearName_januaryStart_returns2025() {
        assertThat(service.deriveFiscalYearName(1, 2025)).isEqualTo("2025");
    }

    @Test
    void derivePeriodName_april_returnsAPR_2025() {
        assertThat(service.derivePeriodName(4, 2025)).isEqualTo("APR-2025");
    }

    @Test
    void deriveQuarter_aprilStart_correctQuarters() {
        assertThat(service.deriveQuarter(4, 4)).isEqualTo(1);
        assertThat(service.deriveQuarter(6, 4)).isEqualTo(1);
        assertThat(service.deriveQuarter(7, 4)).isEqualTo(2);
        assertThat(service.deriveQuarter(9, 4)).isEqualTo(2);
        assertThat(service.deriveQuarter(10, 4)).isEqualTo(3);
        assertThat(service.deriveQuarter(12, 4)).isEqualTo(3);
        assertThat(service.deriveQuarter(1, 4)).isEqualTo(4);
        assertThat(service.deriveQuarter(3, 4)).isEqualTo(4);
    }

    @Test
    void generatePeriods_monthly_generates12() {
        List<AccountingCalendarService.GeneratedPeriod> periods =
                service.generatePeriods(4, 1, PeriodType.MONTHLY, 2025);

        assertThat(periods).hasSize(12);

        AccountingCalendarService.GeneratedPeriod first = periods.get(0);
        assertThat(first.name()).isEqualTo("APR-2025");
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(first.endDate()).isEqualTo(LocalDate.of(2025, 4, 30));
        assertThat(first.periodNumber()).isEqualTo(1);
        assertThat(first.quarter()).isEqualTo(1);
        assertThat(first.fiscalYear()).isEqualTo("2025-26");

        AccountingCalendarService.GeneratedPeriod last = periods.get(11);
        assertThat(last.name()).isEqualTo("MAR-2026");
        assertThat(last.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(last.endDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(last.periodNumber()).isEqualTo(12);
        assertThat(last.quarter()).isEqualTo(4);

        AccountingCalendarService.GeneratedPeriod feb = periods.get(10);
        assertThat(feb.name()).isEqualTo("FEB-2026");
        assertThat(feb.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void deactivate_alwaysBlockedBecausePeriodsAlreadyGenerated() {
        UUID id = UUID.randomUUID();
        AccountingCalendar entity = new AccountingCalendar();
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.deactivate(id, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "CALENDAR_HAS_PERIODS");
    }

    @Test
    void createCalendar_usesProvisioningAnswersWhenRequestOmitsFiscalStart() {
        Ledger ledger = ledger();
        CreateCalendarRequest request = new CreateCalendarRequest(ledger.getId(), "FY Calendar", null,
                null, null, null, null);
        AccountingCalendar entity = new AccountingCalendar();
        AccountingCalendar saved = AccountingCalendar.builder().name("FY Calendar").ledger(ledger).build();
        saved.setId(UUID.randomUUID());

        com.evyoog.gl.enterprise.domain.ConsumptionContext context = com.evyoog.gl.enterprise.domain.ConsumptionContext.builder()
                .provisioningAnswers(java.util.Map.of("fiscalStartMonth", 4, "fiscalStartYear", 2024))
                .build();
        com.evyoog.gl.enterprise.domain.BusinessGroup businessGroup = com.evyoog.gl.enterprise.domain.BusinessGroup.builder()
                .consumptionContext(context).build();
        com.evyoog.gl.enterprise.domain.LegalEntity legalEntity = com.evyoog.gl.enterprise.domain.LegalEntity.builder()
                .businessGroup(businessGroup).build();
        com.evyoog.gl.ledger.domain.LegalEntityLedger lel = com.evyoog.gl.ledger.domain.LegalEntityLedger.builder()
                .legalEntity(legalEntity).ledger(ledger).build();

        when(ledgerRepository.findById(ledger.getId())).thenReturn(Optional.of(ledger));
        when(repository.existsByLedgerIdAndIsActiveTrue(ledger.getId())).thenReturn(false);
        when(legalEntityLedgerRepository.findByLedgerId(ledger.getId())).thenReturn(List.of(lel));
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(responseFor(saved));

        AccountingCalendarResponse result = service.create(request, "prashanth");

        assertThat(result.currentFiscalYear()).isEqualTo("2024-25");
        assertThat(entity.getFiscalYearStartMonth()).isEqualTo(4);
    }
}
