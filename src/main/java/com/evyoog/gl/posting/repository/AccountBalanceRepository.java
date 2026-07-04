package com.evyoog.gl.posting.repository;

import com.evyoog.gl.posting.domain.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    // account_combination is JSONB — matching the exact combination is done in
    // the service layer since comparing JSONB map equality safely in JPQL is
    // brittle. Per account+period, the candidate list here is small.
    List<AccountBalance> findByLedgerIdAndLegalEntityIdAndAccountingPeriodIdAndNaturalAccountId(
            UUID ledgerId, UUID legalEntityId, UUID accountingPeriodId, UUID naturalAccountId);

    List<AccountBalance> findByLegalEntityIdAndAccountingPeriodId(UUID legalEntityId, UUID accountingPeriodId);

    boolean existsByLegalEntityIdAndAccountingPeriodId(UUID legalEntityId, UUID accountingPeriodId);
}
