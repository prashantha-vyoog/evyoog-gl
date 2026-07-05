package com.evyoog.gl.recurring.domain;

import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.posting.domain.JournalHeader;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail of every journal generated from a
 * RecurringJournalTemplate. Does not extend AuditableEntity — a run is never
 * updated or soft-deleted, matching the JournalApprovalLog pattern.
 */
@Entity
@Table(name = "recurring_journal_run", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringJournalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RecurringJournalTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_header_id", nullable = false)
    private JournalHeader journalHeader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "generated_by", nullable = false, length = 100)
    private String generatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
