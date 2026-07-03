package com.evyoog.gl.calendar.domain;

import com.evyoog.gl.common.domain.AuditableEntity;
import com.evyoog.gl.ledger.domain.Ledger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "accounting_calendar", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class AccountingCalendar extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_id", nullable = false, unique = true)
    private Ledger ledger;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth;

    @Column(name = "fiscal_year_start_day", nullable = false)
    private int fiscalYearStartDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType;

    @Column(name = "periods_per_year", nullable = false)
    private int periodsPerYear;
}
