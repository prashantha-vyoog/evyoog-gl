package com.evyoog.gl.aie.sourceref.repository;

import com.evyoog.gl.aie.sourceref.domain.JeSourceReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JeSourceReferenceRepository extends JpaRepository<JeSourceReference, UUID> {

    List<JeSourceReference> findByJournalHeaderId(UUID journalHeaderId);

    List<JeSourceReference> findBySourceSystemAndSourceDocumentId(String sourceSystem, String sourceDocumentId);
}
