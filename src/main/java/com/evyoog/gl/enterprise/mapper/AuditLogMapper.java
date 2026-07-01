package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.common.audit.domain.AuditLog;
import com.evyoog.gl.enterprise.dto.AuditLogResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog entity);
}
