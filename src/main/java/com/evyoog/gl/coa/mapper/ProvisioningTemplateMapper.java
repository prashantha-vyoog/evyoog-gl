package com.evyoog.gl.coa.mapper;

import com.evyoog.gl.coa.domain.ProvisioningTemplate;
import com.evyoog.gl.coa.dto.ProvisioningTemplateResponse;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProvisioningTemplateMapper {

    @Mapping(source = "active", target = "isActive")
    ProvisioningTemplateResponse toResponse(ProvisioningTemplate entity);
}
