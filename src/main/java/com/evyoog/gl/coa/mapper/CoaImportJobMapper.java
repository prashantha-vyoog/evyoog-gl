package com.evyoog.gl.coa.mapper;

import com.evyoog.gl.coa.domain.CoaImportJob;
import com.evyoog.gl.coa.dto.CoaImportJobResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CoaImportJobMapper {

    @Mapping(source = "ledger.id", target = "ledgerId")
    @Mapping(source = "financeDimension.id", target = "financeDimensionId")
    CoaImportJobResponse toResponse(CoaImportJob entity);
}
