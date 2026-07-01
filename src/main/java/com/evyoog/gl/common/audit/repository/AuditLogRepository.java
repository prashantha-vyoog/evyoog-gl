package com.evyoog.gl.common.audit.repository;

import com.evyoog.gl.common.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityNameAndEntityId(String entityName, UUID entityId, Pageable pageable);

    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);
}
