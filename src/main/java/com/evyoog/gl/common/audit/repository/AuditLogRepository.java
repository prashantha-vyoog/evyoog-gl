package com.evyoog.gl.common.audit.repository;

import com.evyoog.gl.common.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityNameAndEntityId(String entityName, UUID entityId, Pageable pageable);

    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);

    List<AuditLog> findByEntityNameAndEntityIdOrderByPerformedAtAsc(String entityName, UUID entityId);
}
