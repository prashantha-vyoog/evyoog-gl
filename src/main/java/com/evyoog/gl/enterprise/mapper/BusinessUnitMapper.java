package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.enterprise.domain.BusinessUnit;
import com.evyoog.gl.enterprise.dto.BusinessUnitResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessUnitRequest;
import com.evyoog.gl.enterprise.dto.UpdateBusinessUnitRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface BusinessUnitMapper {

    BusinessUnit toEntity(CreateBusinessUnitRequest request);

    @Mapping(source = "legalEntity.id", target = "legalEntityId")
    @Mapping(source = "active", target = "isActive")
    BusinessUnitResponse toResponse(BusinessUnit entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(UpdateBusinessUnitRequest request, @MappingTarget BusinessUnit entity);
}
