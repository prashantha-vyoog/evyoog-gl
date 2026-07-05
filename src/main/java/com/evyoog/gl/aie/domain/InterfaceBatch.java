package com.evyoog.gl.aie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Does NOT extend AuditableEntity — manages its own audit fields, matching the
 * pattern used by JournalHeader and AccountingPeriod. Batch status is its own
 * lifecycle (PENDING..POSTED/FAILED/PARTIAL), not an isActive soft-delete flag.
 */
@Entity
@Table(name = "interface_batch", schema = "aie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterfaceBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "batch_reference", length = 255)
    private String batchReference;

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Builder.Default
    @Column(name = "import_transport", nullable = false, length = 20)
    private String importTransport = "REST";

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "ledger_id")
    private UUID ledgerId;

    @Column(name = "accounting_period_id")
    private UUID accountingPeriodId;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "total_lines", nullable = false)
    private Integer totalLines = 0;

    @Builder.Default
    @Column(name = "valid_lines", nullable = false)
    private Integer validLines = 0;

    @Builder.Default
    @Column(name = "error_lines", nullable = false)
    private Integer errorLines = 0;

    @Column(name = "error_summary", length = 500)
    private String errorSummary;

    @Column(name = "journal_header_id")
    private UUID journalHeaderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
