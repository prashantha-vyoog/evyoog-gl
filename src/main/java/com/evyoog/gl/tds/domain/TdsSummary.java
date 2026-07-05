package com.evyoog.gl.tds.domain;

import com.evyoog.gl.enterprise.domain.LegalEntity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tds_summary", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TdsSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @Column(name = "tds_section", nullable = false, length = 10)
    private String tdsSection;

    @Builder.Default
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    @Builder.Default
    @Column(name = "total_payment_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalPaymentAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_tds_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalTdsAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
