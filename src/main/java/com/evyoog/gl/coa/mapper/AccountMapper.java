package com.evyoog.gl.coa.mapper;

import com.evyoog.gl.coa.dto.AccountResponse;
import com.evyoog.gl.dimension.dto.DimensionValueResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(source = "accountQualifier", target = "qualifier")
    @Mapping(source = "parentValueId", target = "parentAccountId")
    @Mapping(source = "parentValueCode", target = "parentAccountCode")
    @Mapping(source = "parentValueName", target = "parentAccountName")
    @Mapping(target = "children", ignore = true)
    AccountResponse toResponse(DimensionValueResponse response);
}
