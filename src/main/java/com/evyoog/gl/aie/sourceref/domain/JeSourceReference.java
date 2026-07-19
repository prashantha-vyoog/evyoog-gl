package com.evyoog.gl.aie.sourceref.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "je_source_reference", schema = "aie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JeSourceReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_header_id", nullable = false)
    private JournalHeader journalHeader;

    @Column(name = "source_system", nullable = false, length = 30)
    private String sourceSystem;

    @Column(name = "source_document_type", nullable = false, length = 30)
    private String sourceDocumentType;

    @Column(name = "source_document_id", nullable = false, length = 100)
    private String sourceDocumentId;

    @Column(name = "source_document_ref", length = 255)
    private String sourceDocumentRef;

    @Column(name = "source_line_number")
    private Integer sourceLineNumber;

    @Column(name = "amount", precision = 20, scale = 4)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
