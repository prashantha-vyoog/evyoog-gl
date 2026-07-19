package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.SlaEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface SlaEventLogRepository extends JpaRepository<SlaEventLog, UUID>, JpaSpecificationExecutor<SlaEventLog> {

    List<SlaEventLog> findByLedgerId(UUID ledgerId);

    Page<SlaEventLog> findByLegalEntityId(UUID legalEntityId, Pageable pageable);
}
