package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.BatchAckLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BatchAckLogRepository extends JpaRepository<BatchAckLog, UUID> {
}
