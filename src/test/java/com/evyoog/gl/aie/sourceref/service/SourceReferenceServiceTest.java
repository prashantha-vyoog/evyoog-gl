package com.evyoog.gl.aie.sourceref.service;

import com.evyoog.gl.aie.sourceref.domain.JeSourceReference;
import com.evyoog.gl.aie.sourceref.dto.CreateSourceReferenceRequest;
import com.evyoog.gl.aie.sourceref.dto.SourceReferenceResponse;
import com.evyoog.gl.aie.sourceref.repository.JeSourceReferenceRepository;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceReferenceServiceTest {

    @Mock
    private JeSourceReferenceRepository repository;
    @Mock
    private JournalHeaderRepository journalHeaderRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private SourceReferenceService service;

    @Test
    void testCreate_validJournal_createsReference() {
        JournalHeader journal = journal(JournalStatus.DRAFT);
        when(journalHeaderRepository.findById(journal.getId())).thenReturn(Optional.of(journal));
        when(repository.save(any(JeSourceReference.class))).thenAnswer(inv -> {
            JeSourceReference entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        CreateSourceReferenceRequest request = new CreateSourceReferenceRequest(
                journal.getId(), "AP", "INVOICE", "INV-1001", "AP/INV-1001",
                null, new BigDecimal("500.00"), "svc-account");

        SourceReferenceResponse response = service.create(request);

        assertThat(response.journalHeaderId()).isEqualTo(journal.getId());
        assertThat(response.journalNumber()).isEqualTo(journal.getJournalNumber());
        assertThat(response.sourceSystem()).isEqualTo("AP");
        verify(auditService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testCreate_journalNotFound_throws404() {
        UUID journalId = UUID.randomUUID();
        when(journalHeaderRepository.findById(journalId)).thenReturn(Optional.empty());

        CreateSourceReferenceRequest request = new CreateSourceReferenceRequest(
                journalId, "AP", "INVOICE", "INV-1002", null, null, null, "svc-account");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "JOURNAL_NOT_FOUND");
    }

    @Test
    void testGetByJournal_returnsAllReferences() {
        JournalHeader journal = journal(JournalStatus.DRAFT);
        JeSourceReference ref = reference(journal);
        when(journalHeaderRepository.existsById(journal.getId())).thenReturn(true);
        when(repository.findByJournalHeaderId(journal.getId())).thenReturn(List.of(ref));

        List<SourceReferenceResponse> result = service.getByJournal(journal.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceDocumentId()).isEqualTo(ref.getSourceDocumentId());
    }

    @Test
    void testGetBySource_returnsLinkedJournals() {
        JournalHeader journal = journal(JournalStatus.DRAFT);
        JeSourceReference ref = reference(journal);
        when(repository.findBySourceSystemAndSourceDocumentId("AP", "INV-1001"))
                .thenReturn(List.of(ref));

        List<SourceReferenceResponse> result = service.getBySource("AP", "INV-1001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).journalHeaderId()).isEqualTo(journal.getId());
    }

    @Test
    void testGetById_returnsReference() {
        JournalHeader journal = journal(JournalStatus.DRAFT);
        JeSourceReference ref = reference(journal);
        when(repository.findById(ref.getId())).thenReturn(Optional.of(ref));

        SourceReferenceResponse response = service.getById(ref.getId());

        assertThat(response.id()).isEqualTo(ref.getId());
    }

    @Test
    void testGetById_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "SOURCE_REF_NOT_FOUND");
    }

    @Test
    void testDelete_draftJournal_succeeds() {
        JournalHeader journal = journal(JournalStatus.DRAFT);
        JeSourceReference ref = reference(journal);
        when(repository.findById(ref.getId())).thenReturn(Optional.of(ref));
        lenient().when(repository.findById(ref.getId())).thenReturn(Optional.of(ref));

        service.delete(ref.getId());

        verify(repository).delete(ref);
        verify(auditService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDelete_postedJournal_throws409() {
        JournalHeader journal = journal(JournalStatus.POSTED);
        JeSourceReference ref = reference(journal);
        when(repository.findById(ref.getId())).thenReturn(Optional.of(ref));

        assertThatThrownBy(() -> service.delete(ref.getId()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "CANNOT_DELETE_POSTED_REF");
    }

    private JournalHeader journal(JournalStatus status) {
        return JournalHeader.builder()
                .id(UUID.randomUUID())
                .journalNumber("JE-0001-00001")
                .status(status)
                .build();
    }

    private JeSourceReference reference(JournalHeader journal) {
        return JeSourceReference.builder()
                .id(UUID.randomUUID())
                .journalHeader(journal)
                .sourceSystem("AP")
                .sourceDocumentType("INVOICE")
                .sourceDocumentId("INV-1001")
                .createdBy("svc-account")
                .build();
    }
}
