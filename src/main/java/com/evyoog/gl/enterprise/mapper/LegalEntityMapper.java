package com.evyoog.gl.enterprise.mapper;

import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.dto.CreateLegalEntityRequest;
import com.evyoog.gl.enterprise.dto.LegalEntityResponse;
import com.evyoog.gl.enterprise.dto.UpdateLegalEntityRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface LegalEntityMapper {

    LegalEntity toEntity(CreateLegalEntityRequest request);

    @Mapping(source = "businessGroup.id", target = "businessGroupId")
    @Mapping(source = "active", target = "isActive")
    LegalEntityResponse toResponse(LegalEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(UpdateLegalEntityRequest request, @MappingTarget LegalEntity entity);
}
