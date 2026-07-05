package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.DeduplicationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeduplicationLogRepository extends JpaRepository<DeduplicationLog, UUID> {

    boolean existsByEventId(String eventId);
}
