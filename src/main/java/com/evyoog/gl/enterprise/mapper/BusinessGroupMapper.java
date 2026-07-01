package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.enterprise.domain.BusinessGroup;
import com.evyoog.gl.enterprise.dto.BusinessGroupResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessGroupRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BusinessGroupMapper {

    BusinessGroup toEntity(CreateBusinessGroupRequest request);

    @Mapping(source = "consumptionContext.id", target = "consumptionContextId")
    @Mapping(source = "active", target = "isActive")
    BusinessGroupResponse toResponse(BusinessGroup entity);
}
