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
 * Fast idempotency check for AIE imports. A row here is written BEFORE any
 * other processing begins (GL-16 Stage 1) so a duplicate event_id is rejected
 * before any batch/line rows are created.
 */
@Entity
@Table(name = "deduplication_log", schema = "aie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeduplicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "event_id", unique = true, nullable = false, length = 255)
    private String eventId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
