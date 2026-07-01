package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.enterprise.dto.ConsumptionContextResponse;
import com.evyoog.gl.enterprise.dto.CreateConsumptionContextRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConsumptionContextMapper {

    ConsumptionContext toEntity(CreateConsumptionContextRequest request);

    @Mapping(source = "active", target = "isActive")
    ConsumptionContextResponse toResponse(ConsumptionContext entity);
}
