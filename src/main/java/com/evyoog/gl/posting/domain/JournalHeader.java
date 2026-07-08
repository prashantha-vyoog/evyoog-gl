package com.evyoog.gl.posting.domain;

import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.period.domain.AccountingPeriod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Does NOT extend AuditableEntity — manages its own audit fields, matching the
 * pattern used by AccountingPeriod and PeriodStatus.
 */
@Entity
@Table(name = "journal_header", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class JournalHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "journal_number", nullable = false, unique = true, length = 30)
    private String journalNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_source_id", nullable = false)
    private JournalSource journalSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_category_id", nullable = false)
    private JournalCategory journalCategory;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "gl_date", nullable = false)
    private LocalDate glDate;

    @Column(name = "accounting_date", nullable = false)
    private LocalDate accountingDate;

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Builder.Default
    @Column(name = "exchange_rate", nullable = false, precision = 20, scale = 10)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Builder.Default
    @Column(name = "total_debit", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_credit", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JournalStatus status = JournalStatus.DRAFT;

    // IMMUTABLE — written once at journal creation, never updated after that.
    @Enumerated(EnumType.STRING)
    @Column(name = "finance_mode_snapshot", nullable = false, length = 20)
    private FinanceMode financeModeSnapshot;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by", length = 100)
    private String postedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private JournalHeader reversalOf;

    @Builder.Default
    @Column(name = "is_reversal", nullable = false)
    private Boolean isReversal = false;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "notes")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extended_attributes", columnDefinition = "jsonb")
    private Map<String, Object> extendedAttributes;

    @Builder.Default
    @OneToMany(mappedBy = "journalHeader", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<JournalLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
