package com.evyoog.gl.ledger.mapper;

import com.evyoog.gl.ledger.domain.LegalEntityLedger;
import com.evyoog.gl.ledger.dto.LegalEntityLedgerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LegalEntityLedgerMapper {

    @Mapping(source = "legalEntity.id", target = "legalEntityId")
    @Mapping(source = "legalEntity.name", target = "legalEntityName")
    @Mapping(source = "ledger.id", target = "ledgerId")
    @Mapping(source = "ledger.name", target = "ledgerName")
    @Mapping(source = "ledger.code", target = "ledgerCode")
    @Mapping(source = "ledger.financeMode", target = "financeMode")
    @Mapping(source = "active", target = "isActive")
    LegalEntityLedgerResponse toResponse(LegalEntityLedger entity);
}
