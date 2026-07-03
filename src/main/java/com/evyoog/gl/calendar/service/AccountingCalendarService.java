package com.evyoog.gl.calendar.service;

import com.evyoog.gl.calendar.domain.AccountingCalendar;
import com.evyoog.gl.calendar.domain.PeriodType;
import com.evyoog.gl.calendar.dto.AccountingCalendarResponse;
import com.evyoog.gl.calendar.dto.CreateCalendarRequest;
import com.evyoog.gl.calendar.dto.UpdateCalendarRequest;
import com.evyoog.gl.calendar.mapper.AccountingCalendarMapper;
import com.evyoog.gl.calendar.repository.AccountingCalendarRepository;
import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LedgerRepository;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountingCalendarService {

    private final AccountingCalendarRepository repository;
    private final LedgerRepository ledgerRepository;
    private final LegalEntityLedgerRepository legalEntityLedgerRepository;
    private final AccountingCalendarMapper mapper;
    private final AuditService auditService;

    @Transactional
    public AccountingCalendarResponse create(CreateCalendarRequest request, String performedBy) {
        Ledger ledger = ledgerRepository.findById(request.ledgerId())
                .orElseThrow(() -> new ResourceNotFoundException("Ledger", request.ledgerId()));

        if (repository.existsByLedgerIdAndIsActiveTrue(ledger.getId())) {
            throw new EvyoogException("CALENDAR_EXISTS",
                    "This Ledger already has an Accounting Calendar. " +
                            "A Ledger may have exactly one Accounting Calendar.");
        }

        Map<String, Object> provisioningAnswers = resolveProvisioningAnswers(ledger.getId());

        int fiscalYearStartMonth = request.fiscalYearStartMonth() != null
                ? request.fiscalYearStartMonth() : intAnswer(provisioningAnswers, "fiscalStartMonth", 4);
        int fiscalYearStartDay = request.fiscalYearStartDay() != null ? request.fiscalYearStartDay() : 1;
        PeriodType periodType = request.periodType() != null ? request.periodType() : PeriodType.MONTHLY;
        int periodsPerYear = periodsPerYear(periodType);
        int initialFiscalYear = request.initialFiscalYear() != null
                ? request.initialFiscalYear() : intAnswer(provisioningAnswers, "fiscalStartYear", LocalDate.now().getYear());

        // Fail fast before persisting anything if the date math for this fiscal year is invalid.
        List<GeneratedPeriod> periods = generatePeriods(fiscalYearStartMonth, fiscalYearStartDay, periodType, initialFiscalYear);
        String fiscalYearName = deriveFiscalYearName(fiscalYearStartMonth, initialFiscalYear);

        AccountingCalendar entity = mapper.toEntity(request);
        entity.setLedger(ledger);
        entity.setFiscalYearStartMonth(fiscalYearStartMonth);
        entity.setFiscalYearStartDay(fiscalYearStartDay);
        entity.setPeriodType(periodType);
        entity.setPeriodsPerYear(periodsPerYear);
        entity.setCreatedBy(performedBy);
        entity.setUpdatedBy(performedBy);

        AccountingCalendar saved = repository.saveAndFlush(entity);
        AccountingCalendarResponse response = mapper.toResponse(saved).withGeneratedInfo(periods.size(), fiscalYearName);

        auditService.log(AuditAction.CREATE, "accounting_calendar", saved.getId(), null, response, performedBy);

        return response;
    }

    @Transactional
    public AccountingCalendarResponse update(UUID id, UpdateCalendarRequest request, String performedBy) {
        AccountingCalendar entity = findOrThrow(id);
        AccountingCalendarResponse before = enrichCurrent(entity);

        mapper.updateFromRequest(request, entity);
        entity.setUpdatedBy(performedBy);

        AccountingCalendar saved = repository.saveAndFlush(entity);
        AccountingCalendarResponse response = enrichCurrent(saved);
        auditService.log(AuditAction.UPDATE, "accounting_calendar", saved.getId(), before, response, performedBy);

        return response;
    }

    @Transactional
    public void deactivate(UUID id, String performedBy) {
        findOrThrow(id);

        // GL-08 auto-generates periods synchronously on creation (Rule 2), so every existing
        // Accounting Calendar has already had its periods generated. Real period-existence
        // checking (e.g. "no journals posted yet") replaces this once GL-09 introduces
        // gl.accounting_period.
        throw new EvyoogException("CALENDAR_HAS_PERIODS",
                "Cannot delete an Accounting Calendar once its periods have been generated.", HttpStatus.CONFLICT);
    }

    @Transactional(readOnly = true)
    public AccountingCalendarResponse getById(UUID id) {
        return enrichCurrent(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Optional<AccountingCalendarResponse> getByLedgerId(UUID ledgerId) {
        return repository.findByLedgerId(ledgerId).map(this::enrichCurrent);
    }

    private AccountingCalendarResponse enrichCurrent(AccountingCalendar entity) {
        int currentFiscalYear = fiscalYearStartYearFor(LocalDate.now(), entity.getFiscalYearStartMonth(), entity.getFiscalYearStartDay());
        String fiscalYearName = deriveFiscalYearName(entity.getFiscalYearStartMonth(), currentFiscalYear);
        return mapper.toResponse(entity).withGeneratedInfo(entity.getPeriodsPerYear(), fiscalYearName);
    }

    private Map<String, Object> resolveProvisioningAnswers(UUID ledgerId) {
        return legalEntityLedgerRepository.findByLedgerId(ledgerId).stream()
                .map(lel -> lel.getLegalEntity().getBusinessGroup().getConsumptionContext())
                .filter(Objects::nonNull)
                .map(ConsumptionContext::getProvisioningAnswers)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Map.of());
    }

    private int intAnswer(Map<String, Object> answers, String key, int defaultValue) {
        Object value = answers.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private int periodsPerYear(PeriodType periodType) {
        return switch (periodType) {
            case MONTHLY -> 12;
            case QUARTERLY -> 4;
            case FISCAL_4_4_5 -> 13;
        };
    }

    private int fiscalYearStartYearFor(LocalDate date, int fiscalStartMonth, int fiscalStartDay) {
        LocalDate fiscalStartThisCalendarYear = LocalDate.of(date.getYear(), fiscalStartMonth, fiscalStartDay);
        return date.isBefore(fiscalStartThisCalendarYear) ? date.getYear() - 1 : date.getYear();
    }

    String deriveFiscalYearName(int fiscalYearStartMonth, int fiscalYearStartYear) {
        if (fiscalYearStartMonth == 1) {
            return String.valueOf(fiscalYearStartYear);
        }
        return fiscalYearStartYear + "-" + String.valueOf(fiscalYearStartYear + 1).substring(2);
    }

    String derivePeriodName(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH) + "-" + year;
    }

    int deriveQuarter(int month, int fiscalYearStartMonth) {
        int fiscalMonth = ((month - fiscalYearStartMonth + 12) % 12) + 1;
        return (fiscalMonth - 1) / 3 + 1;
    }

    List<GeneratedPeriod> generatePeriods(int fiscalYearStartMonth, int fiscalYearStartDay, PeriodType periodType, int fiscalYear) {
        return switch (periodType) {
            case MONTHLY -> generateMonthlyPeriods(fiscalYearStartMonth, fiscalYear);
            case QUARTERLY -> generateQuarterlyPeriods(fiscalYearStartMonth, fiscalYear);
            case FISCAL_4_4_5 -> generateFiscal445Periods(fiscalYearStartMonth, fiscalYearStartDay, fiscalYear);
        };
    }

    private List<GeneratedPeriod> generateMonthlyPeriods(int fiscalYearStartMonth, int fiscalYear) {
        String fiscalYearName = deriveFiscalYearName(fiscalYearStartMonth, fiscalYear);
        List<GeneratedPeriod> periods = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            int month = ((fiscalYearStartMonth - 1 + i) % 12) + 1;
            int year = fiscalYear + ((fiscalYearStartMonth - 1 + i) / 12);
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = YearMonth.of(year, month).atEndOfMonth();

            periods.add(new GeneratedPeriod(derivePeriodName(month, year), start, end, i + 1,
                    deriveQuarter(month, fiscalYearStartMonth), fiscalYearName));
        }

        return periods;
    }

    private List<GeneratedPeriod> generateQuarterlyPeriods(int fiscalYearStartMonth, int fiscalYear) {
        String fiscalYearName = deriveFiscalYearName(fiscalYearStartMonth, fiscalYear);
        List<GeneratedPeriod> periods = new ArrayList<>();

        for (int quarter = 0; quarter < 4; quarter++) {
            int startOffset = quarter * 3;
            int startMonth = ((fiscalYearStartMonth - 1 + startOffset) % 12) + 1;
            int startYear = fiscalYear + ((fiscalYearStartMonth - 1 + startOffset) / 12);
            LocalDate start = LocalDate.of(startYear, startMonth, 1);

            int endOffset = startOffset + 2;
            int endMonth = ((fiscalYearStartMonth - 1 + endOffset) % 12) + 1;
            int endYear = fiscalYear + ((fiscalYearStartMonth - 1 + endOffset) / 12);
            LocalDate end = YearMonth.of(endYear, endMonth).atEndOfMonth();

            periods.add(new GeneratedPeriod("Q" + (quarter + 1) + "-" + fiscalYearName, start, end,
                    quarter + 1, quarter + 1, fiscalYearName));
        }

        return periods;
    }

    /**
     * Phase 1 approximation: 13 periods of 4 weeks each, with the final period absorbing
     * whatever remains so the fiscal year closes exactly on the day before the next one starts.
     * A true retail 4-4-5 week-anchored calendar is out of scope until a subledger needs it.
     */
    private List<GeneratedPeriod> generateFiscal445Periods(int fiscalYearStartMonth, int fiscalYearStartDay, int fiscalYear) {
        String fiscalYearName = deriveFiscalYearName(fiscalYearStartMonth, fiscalYear);
        LocalDate fiscalStart = LocalDate.of(fiscalYear, fiscalYearStartMonth, fiscalYearStartDay);
        LocalDate fiscalEnd = fiscalStart.plusYears(1).minusDays(1);

        List<GeneratedPeriod> periods = new ArrayList<>();
        LocalDate cursor = fiscalStart;

        for (int i = 1; i <= 13; i++) {
            LocalDate start = cursor;
            LocalDate end = (i == 13) ? fiscalEnd : start.plusWeeks(4).minusDays(1);
            int quarter = Math.min(((i - 1) / 4) + 1, 4);

            periods.add(new GeneratedPeriod("P" + i + "-" + fiscalYearName, start, end, i, quarter, fiscalYearName));
            cursor = end.plusDays(1);
        }

        return periods;
    }

    private AccountingCalendar findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingCalendar", id));
    }

    public record GeneratedPeriod(String name, LocalDate startDate, LocalDate endDate,
                                   int periodNumber, int quarter, String fiscalYear) {
    }
}
