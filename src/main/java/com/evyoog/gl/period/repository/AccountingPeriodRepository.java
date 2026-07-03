package com.evyoog.gl.period.repository;

import com.evyoog.gl.period.domain.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    boolean existsByAccountingCalendarIdAndFiscalYear(UUID accountingCalendarId, String fiscalYear);

    long countByAccountingCalendarId(UUID accountingCalendarId);

    List<AccountingPeriod> findByAccountingCalendarIdOrderByStartDateAsc(UUID accountingCalendarId);

    List<AccountingPeriod> findByAccountingCalendarIdAndFiscalYearOrderByStartDateAsc(
            UUID accountingCalendarId, String fiscalYear);

    Optional<AccountingPeriod> findByAccountingCalendarIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID accountingCalendarId, LocalDate startDate, LocalDate endDate);

    Optional<AccountingPeriod> findTopByAccountingCalendarIdOrderByEndDateDesc(UUID accountingCalendarId);
}
