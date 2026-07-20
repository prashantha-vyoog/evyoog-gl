package com.evyoog.gl.coa.excel.service;

import com.evyoog.gl.coa.dto.AccountResponse;
import com.evyoog.gl.coa.dto.CreateAccountRequest;
import com.evyoog.gl.coa.service.ChartOfAccountsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * GL-06 — creates a single account row in its own transaction, isolated from
 * the enclosing CoaImportJobService.importFromExcel() transaction.
 *
 * ChartOfAccountsService.createAccount() (via DimensionValueService.create())
 * is itself @Transactional. Called directly from within another @Transactional
 * method it joins the SAME physical transaction (propagation REQUIRED) — so
 * when one row throws (e.g. a duplicate code), Spring marks that *shared*
 * transaction rollback-only the moment the exception exits create()'s proxy,
 * even though the caller catches it afterward. The job record then fails to
 * save with UnexpectedRollbackException. REQUIRES_NEW here suspends the job's
 * transaction and runs each row in its own, so one bad row only rolls back
 * that row — never the job bookkeeping around it.
 */
@Service
@RequiredArgsConstructor
public class CoaImportRowService {

    private final ChartOfAccountsService chartOfAccountsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountResponse createAccountIsolated(CreateAccountRequest request, String performedBy) {
        return chartOfAccountsService.createAccount(request, performedBy);
    }
}
