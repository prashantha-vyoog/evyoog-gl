package com.evyoog.gl.balance.service;

import com.evyoog.gl.balance.dto.AccountBalanceResponse;
import com.evyoog.gl.balance.dto.AccountBalanceSummaryResponse;
import com.evyoog.gl.balance.dto.CarryForwardResponse;
import com.evyoog.gl.balance.mapper.AccountBalanceMapper;
import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.periodstatus.domain.PeriodStatus;
import com.evyoog.gl.periodstatus.domain.PeriodStatusEnum;
import com.evyoog.gl.periodstatus.repository.PeriodStatusRepository;
import com.evyoog.gl.posting.domain.AccountBalance;
import com.evyoog.gl.posting.repository.AccountBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountBalanceService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final PeriodStatusRepository periodStatusRepository;
    private final AccountBalanceMapper mapper;
    private final AuditService auditService;

    public List<AccountBalanceResponse> getBalances(UUID legalEntityId, UUID periodId, String accountCode) {
        return accountBalanceRepository.findByLegalEntityIdAndAccountingPeriodId(legalEntityId, periodId)
                .stream()
                .filter(ab -> accountCode == null || accountCode.equals(ab.getNaturalAccount().getCode()))
                .map(mapper::toResponse)
                .toList();
    }

    public AccountBalanceSummaryResponse getSummary(UUID legalEntityId, UUID periodId) {
        List<AccountBalance> balances = accountBalanceRepository
                .findByLegalEntityIdAndAccountingPeriodId(legalEntityId, periodId);

        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingPeriod", periodId));

        BigDecimal assets = sumByQualifier(balances, AccountQualifier.ASSET);
        BigDecimal liabilities = sumByQualifier(balances, AccountQualifier.LIABILITY);
        BigDecimal equity = sumByQualifier(balances, AccountQualifier.EQUITY);
        BigDecimal revenue = sumByQualifier(balances, AccountQualifier.REVENUE);
        BigDecimal expenses = sumByQualifier(balances, AccountQualifier.EXPENSE);

        String legalEntityName = balances.isEmpty() ? null : balances.get(0).getLegalEntity().getName();

        return AccountBalanceSummaryResponse.builder()
                .legalEntityId(legalEntityId)
                .legalEntityName(legalEntityName)
                .accountingPeriodId(periodId)
                .periodName(period.getName())
                .totalAssets(assets)
                .totalLiabilities(liabilities)
                .totalEquity(equity)
                .totalRevenue(revenue)
                .totalExpenses(expenses)
                .netIncome(revenue.subtract(expenses))
                .build();
    }

    @Transactional
    public CarryForwardResponse carryForward(UUID legalEntityId, UUID fromPeriodId, UUID toPeriodId, String performedBy) {
        PeriodStatus fromStatus = periodStatusRepository
                .findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId)
                .orElseThrow(() -> new EvyoogException("PERIOD_STATUS_NOT_FOUND",
                        "Period status not found for this Legal Entity and Period."));

        if (fromStatus.getStatus() != PeriodStatusEnum.CLOSED
                && fromStatus.getStatus() != PeriodStatusEnum.LOCKED) {
            throw new EvyoogException("FROM_PERIOD_NOT_CLOSED",
                    "The source period must be CLOSED or LOCKED before carrying forward balances. " +
                            "Current status: " + fromStatus.getStatus());
        }

        boolean alreadyDone = accountBalanceRepository
                .existsByLegalEntityIdAndAccountingPeriodId(legalEntityId, toPeriodId);
        if (alreadyDone) {
            throw new EvyoogException("CARRY_FORWARD_ALREADY_DONE",
                    "Balances have already been carried forward to this period.");
        }

        AccountingPeriod fromPeriod = accountingPeriodRepository.findById(fromPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingPeriod", fromPeriodId));
        AccountingPeriod toPeriod = accountingPeriodRepository.findById(toPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingPeriod", toPeriodId));

        List<AccountBalance> fromBalances = accountBalanceRepository
                .findByLegalEntityIdAndAccountingPeriodId(legalEntityId, fromPeriodId)
                .stream()
                .filter(ab -> isBalanceSheetAccount(ab.getNaturalAccount()))
                .toList();

        int count = 0;
        List<String> accountCodes = new ArrayList<>();
        for (AccountBalance from : fromBalances) {
            BigDecimal endingBalance = from.getBeginningBalance()
                    .add(from.getPeriodToDateDr())
                    .subtract(from.getPeriodToDateCr());

            AccountBalance toBalance = AccountBalance.builder()
                    .ledger(from.getLedger())
                    .legalEntity(from.getLegalEntity())
                    .accountingPeriod(toPeriod)
                    .naturalAccount(from.getNaturalAccount())
                    .accountCombination(from.getAccountCombination())
                    .beginningBalance(endingBalance)
                    .periodToDateDr(BigDecimal.ZERO)
                    .periodToDateCr(BigDecimal.ZERO)
                    .yearToDateDr(BigDecimal.ZERO)
                    .yearToDateCr(BigDecimal.ZERO)
                    .build();

            AccountBalance saved = accountBalanceRepository.save(toBalance);
            auditService.log(AuditAction.CREATE, "account_balance", saved.getId(), null,
                    Map.of("beginningBalance", saved.getBeginningBalance(),
                            "accountingPeriodId", toPeriod.getId()),
                    performedBy);

            count++;
            accountCodes.add(from.getNaturalAccount().getCode());
        }

        return CarryForwardResponse.builder()
                .fromPeriodId(fromPeriodId)
                .fromPeriodName(fromPeriod.getName())
                .toPeriodId(toPeriodId)
                .toPeriodName(toPeriod.getName())
                .balancesCarriedForward(count)
                .accountsCarriedForward(accountCodes)
                .build();
    }

    private boolean isBalanceSheetAccount(DimensionValue account) {
        return account.getAccountQualifier() == AccountQualifier.ASSET
                || account.getAccountQualifier() == AccountQualifier.LIABILITY
                || account.getAccountQualifier() == AccountQualifier.EQUITY;
    }

    private BigDecimal sumByQualifier(List<AccountBalance> balances, AccountQualifier qualifier) {
        return balances.stream()
                .filter(ab -> qualifier == ab.getNaturalAccount().getAccountQualifier())
                .map(ab -> ab.getBeginningBalance()
                        .add(ab.getPeriodToDateDr())
                        .subtract(ab.getPeriodToDateCr()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
