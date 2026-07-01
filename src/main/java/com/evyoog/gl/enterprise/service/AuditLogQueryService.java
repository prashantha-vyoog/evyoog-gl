package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.repository.AuditLogRepository;
import com.evyoog.gl.enterprise.dto.AuditLogResponse;
import com.evyoog.gl.enterprise.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String entityName, UUID entityId, Pageable pageable) {
        Page<com.evyoog.gl.common.audit.domain.AuditLog> page;
        if (entityName != null && entityId != null) {
            page = repository.findByEntityNameAndEntityId(entityName, entityId, pageable);
        } else if (entityName != null) {
            page = repository.findByEntityName(entityName, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(mapper::toResponse);
    }
}
