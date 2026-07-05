package com.evyoog.gl.aie.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interface_line", schema = "aie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterfaceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private InterfaceBatch batch;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "account_code", length = 30)
    private String accountCode;

    // Resolved during the ENRICH stage — null until then.
    @Column(name = "natural_account_value_id")
    private UUID naturalAccountValueId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "account_combination", columnDefinition = "jsonb")
    private Map<String, String> accountCombination;

    @Column(name = "debit_amount", precision = 20, scale = 2)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", precision = 20, scale = 2)
    private BigDecimal creditAmount;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "gst_type", length = 10)
    private String gstType;

    @Builder.Default
    @Column(name = "gst_applicable", nullable = false)
    private Boolean gstApplicable = false;

    @Column(name = "tds_section", length = 10)
    private String tdsSection;

    @Builder.Default
    @Column(name = "tds_applicable", nullable = false)
    private Boolean tdsApplicable = false;

    @Builder.Default
    @Column(name = "line_status", nullable = false, length = 20)
    private String lineStatus = "PENDING";

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
