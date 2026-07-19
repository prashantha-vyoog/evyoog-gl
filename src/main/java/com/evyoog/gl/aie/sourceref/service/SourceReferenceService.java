package com.evyoog.gl.aie.sourceref.service;

import com.evyoog.gl.aie.sourceref.domain.JeSourceReference;
import com.evyoog.gl.aie.sourceref.dto.CreateSourceReferenceRequest;
import com.evyoog.gl.aie.sourceref.dto.SourceReferenceResponse;
import com.evyoog.gl.aie.sourceref.repository.JeSourceReferenceRepository;
import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceReferenceService {

    private final JeSourceReferenceRepository repository;
    private final JournalHeaderRepository journalHeaderRepository;
    private final AuditService auditService;

    @Transactional
    public SourceReferenceResponse create(CreateSourceReferenceRequest request) {
        JournalHeader journalHeader = journalHeaderRepository.findById(request.journalHeaderId())
                .orElseThrow(() -> new EvyoogException("JOURNAL_NOT_FOUND",
                        "Journal header not found: " + request.journalHeaderId(), HttpStatus.NOT_FOUND));

        JeSourceReference entity = JeSourceReference.builder()
                .journalHeader(journalHeader)
                .sourceSystem(request.sourceSystem())
                .sourceDocumentType(request.sourceDocumentType())
                .sourceDocumentId(request.sourceDocumentId())
                .sourceDocumentRef(request.sourceDocumentRef())
                .sourceLineNumber(request.sourceLineNumber())
                .amount(request.amount())
                .createdBy(request.createdBy())
                .build();

        JeSourceReference saved = repository.save(entity);
        SourceReferenceResponse response = toResponse(saved);

        auditService.log(AuditAction.CREATE, "je_source_reference", saved.getId(),
                null, response, request.createdBy());

        return response;
    }

    @Transactional(readOnly = true)
    public List<SourceReferenceResponse> getByJournal(UUID journalHeaderId) {
        if (!journalHeaderRepository.existsById(journalHeaderId)) {
            throw new EvyoogException("JOURNAL_NOT_FOUND",
                    "Journal header not found: " + journalHeaderId, HttpStatus.NOT_FOUND);
        }
        return repository.findByJournalHeaderId(journalHeaderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceReferenceResponse> getBySource(String sourceSystem, String sourceDocumentId) {
        return repository.findBySourceSystemAndSourceDocumentId(sourceSystem, sourceDocumentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceReferenceResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public void delete(UUID id) {
        JeSourceReference entity = findOrThrow(id);

        if (entity.getJournalHeader().getStatus() != JournalStatus.DRAFT) {
            throw new EvyoogException("CANNOT_DELETE_POSTED_REF",
                    "Source reference cannot be deleted — linked journal is not in DRAFT status.",
                    HttpStatus.CONFLICT);
        }

        SourceReferenceResponse response = toResponse(entity);
        repository.delete(entity);

        auditService.log(AuditAction.DELETE, "je_source_reference", entity.getId(),
                response, null, entity.getCreatedBy());
    }

    private JeSourceReference findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EvyoogException("SOURCE_REF_NOT_FOUND",
                        "Source reference not found: " + id, HttpStatus.NOT_FOUND));
    }

    private SourceReferenceResponse toResponse(JeSourceReference entity) {
        return new SourceReferenceResponse(
                entity.getId(),
                entity.getJournalHeader().getId(),
                entity.getJournalHeader().getJournalNumber(),
                entity.getSourceSystem(),
                entity.getSourceDocumentType(),
                entity.getSourceDocumentId(),
                entity.getSourceDocumentRef(),
                entity.getSourceLineNumber(),
                entity.getAmount(),
                entity.getCreatedAt(),
                entity.getCreatedBy());
    }
}
