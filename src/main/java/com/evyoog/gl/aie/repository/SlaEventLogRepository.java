package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.SlaEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlaEventLogRepository extends JpaRepository<SlaEventLog, UUID> {

    List<SlaEventLog> findByLedgerId(UUID ledgerId);
}
