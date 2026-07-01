package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.enterprise.domain.InventoryOrganisation;
import com.evyoog.gl.enterprise.dto.CreateInventoryOrganisationRequest;
import com.evyoog.gl.enterprise.dto.InventoryOrganisationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryOrganisationMapper {

    InventoryOrganisation toEntity(CreateInventoryOrganisationRequest request);

    @Mapping(source = "businessUnit.id", target = "businessUnitId")
    @Mapping(source = "active", target = "isActive")
    InventoryOrganisationResponse toResponse(InventoryOrganisation entity);
}
