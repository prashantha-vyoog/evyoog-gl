package com.evyoog.gl.ledger.repository;

import com.evyoog.gl.ledger.domain.LedgerCategory;
import com.evyoog.gl.ledger.domain.LegalEntityLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LegalEntityLedgerRepository extends JpaRepository<LegalEntityLedger, UUID> {

    boolean existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(UUID legalEntityId, LedgerCategory ledgerCategory);

    List<LegalEntityLedger> findByLegalEntityId(UUID legalEntityId);

    List<LegalEntityLedger> findByLedgerId(UUID ledgerId);
}
