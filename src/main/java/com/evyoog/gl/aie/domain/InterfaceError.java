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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interface_error", schema = "aie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterfaceError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private InterfaceBatch batch;

    // null = batch-level error (not attributable to one line)
    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "error_code", nullable = false, length = 50)
    private String errorCode;

    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    // VALIDATE, ENRICH, POST
    @Column(name = "error_stage", nullable = false, length = 20)
    private String errorStage;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
