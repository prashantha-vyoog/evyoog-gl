package com.evyoog.gl.posting.domain;

import com.evyoog.gl.dimension.domain.DimensionValue;
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
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "journal_line", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_header_id", nullable = false)
    private JournalHeader journalHeader;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "account_combination", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> accountCombination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "natural_account_value_id", nullable = false)
    private DimensionValue naturalAccount;

    @Column(name = "debit_amount", precision = 20, scale = 2)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", precision = 20, scale = 2)
    private BigDecimal creditAmount;

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "description", length = 255)
    private String description;

    @Builder.Default
    @Column(name = "gst_applicable", nullable = false)
    private Boolean gstApplicable = false;

    @Column(name = "gst_type", length = 10)
    private String gstType;

    @Builder.Default
    @Column(name = "tds_applicable", nullable = false)
    private Boolean tdsApplicable = false;

    @Column(name = "tds_section", length = 10)
    private String tdsSection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extended_attributes", columnDefinition = "jsonb")
    private Map<String, Object> extendedAttributes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
