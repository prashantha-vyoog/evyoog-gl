package com.evyoog.gl.calendar.repository;

import com.evyoog.gl.calendar.domain.AccountingCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountingCalendarRepository extends JpaRepository<AccountingCalendar, UUID> {

    boolean existsByLedgerIdAndIsActiveTrue(UUID ledgerId);

    Optional<AccountingCalendar> findByLedgerId(UUID ledgerId);
}
