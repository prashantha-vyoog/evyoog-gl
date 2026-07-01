package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.enterprise.domain.SubInventory;
import com.evyoog.gl.enterprise.dto.CreateSubInventoryRequest;
import com.evyoog.gl.enterprise.dto.SubInventoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubInventoryMapper {

    SubInventory toEntity(CreateSubInventoryRequest request);

    @Mapping(source = "inventoryOrganisation.id", target = "inventoryOrganisationId")
    @Mapping(source = "active", target = "isActive")
    SubInventoryResponse toResponse(SubInventory entity);
}
