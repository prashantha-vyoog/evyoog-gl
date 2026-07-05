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

import java.time.Instant;
import java.util.UUID;

/**
 * GL-16's own post-stage acknowledgement log — deliberately separate from
 * {@link SlaEventLog}, which is aie.sla_event_log written exclusively by
 * PostingEngine.emitEvent() for the EVENT_ONLY finance mode branch and has an
 * unrelated column shape (ledgerId/legalEntityId/accountingPeriodId/eventPayload).
 */
@Entity
@Table(name = "batch_ack_log", schema = "aie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchAckLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "journal_header_id")
    private UUID journalHeaderId;

    // GL_POSTED, GL_FAILED
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    // ACKNOWLEDGED, FAILED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
