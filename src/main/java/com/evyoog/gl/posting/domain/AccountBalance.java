package com.evyoog.gl.posting.domain;

import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.period.domain.AccountingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "account_balance", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "natural_account_value_id", nullable = false)
    private DimensionValue naturalAccount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "account_combination", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> accountCombination;

    @Builder.Default
    @Column(name = "beginning_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal beginningBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "period_to_date_dr", nullable = false, precision = 20, scale = 2)
    private BigDecimal periodToDateDr = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "period_to_date_cr", nullable = false, precision = 20, scale = 2)
    private BigDecimal periodToDateCr = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "year_to_date_dr", nullable = false, precision = 20, scale = 2)
    private BigDecimal yearToDateDr = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "year_to_date_cr", nullable = false, precision = 20, scale = 2)
    private BigDecimal yearToDateCr = BigDecimal.ZERO;

    // Optimistic locking — Phase 1. To switch to pessimistic: remove @Version,
    // use PESSIMISTIC_WRITE in the repository query instead.
    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
