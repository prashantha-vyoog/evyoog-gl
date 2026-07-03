package com.evyoog.gl.ledger.repository;

import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.domain.LedgerCategory;
import com.evyoog.gl.ledger.domain.LegalEntityLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalEntityLedgerRepository extends JpaRepository<LegalEntityLedger, UUID> {

    boolean existsByLegalEntityIdAndLedgerCategoryAndIsActiveTrue(UUID legalEntityId, LedgerCategory ledgerCategory);

    List<LegalEntityLedger> findByLegalEntityId(UUID legalEntityId);

    List<LegalEntityLedger> findByLedgerId(UUID ledgerId);

    boolean existsByLegalEntityIdAndLedgerIdAndIsActiveTrue(UUID legalEntityId, UUID ledgerId);

    @Query("""
            select lel.ledger from LegalEntityLedger lel
            where lel.legalEntity.id = :legalEntityId
            and lel.ledgerCategory = com.evyoog.gl.ledger.domain.LedgerCategory.PRIMARY
            and lel.isActive = true
            """)
    Optional<Ledger> findPrimaryLedgerByLegalEntityId(@Param("legalEntityId") UUID legalEntityId);
}
