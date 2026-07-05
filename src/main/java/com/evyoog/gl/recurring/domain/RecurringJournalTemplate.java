package com.evyoog.gl.recurring.domain;

import com.evyoog.gl.common.domain.AuditableEntity;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.period.domain.AccountingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "recurring_journal_template", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class RecurringJournalTemplate extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_period_id")
    private AccountingPeriod startPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_period_id")
    private AccountingPeriod endPeriod;

    @Column(name = "journal_source_code", nullable = false, length = 30)
    private String journalSourceCode;

    @Column(name = "journal_category_code", nullable = false, length = 30)
    private String journalCategoryCode;

    @Column(name = "reference", length = 100)
    private String reference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lines", columnDefinition = "jsonb", nullable = false)
    private List<RecurringTemplateLine> lines;
}
