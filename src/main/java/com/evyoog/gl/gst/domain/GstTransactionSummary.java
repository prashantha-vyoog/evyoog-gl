package com.evyoog.gl.gst.domain;

import com.evyoog.gl.enterprise.domain.BusinessUnit;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gst_transaction_summary", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GstTransactionSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_header_id", nullable = false)
    private JournalHeader journalHeader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_line_id", nullable = false)
    private JournalLine journalLine;

    @Column(name = "gst_type", nullable = false, length = 10)
    private String gstType;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Builder.Default
    @Column(name = "taxable_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "cgst_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "sgst_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "igst_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "utgst_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal utgstAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_tax_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalTaxAmount = BigDecimal.ZERO;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "place_of_supply", length = 2)
    private String placeOfSupply;

    @Builder.Default
    @Column(name = "is_reverse_charge", nullable = false)
    private Boolean isReverseCharge = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
