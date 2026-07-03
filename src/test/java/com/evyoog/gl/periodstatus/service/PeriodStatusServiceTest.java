package com.evyoog.gl.periodstatus.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.domain.LedgerCategory;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.periodstatus.domain.PeriodStatus;
import com.evyoog.gl.periodstatus.domain.PeriodStatusEnum;
import com.evyoog.gl.periodstatus.dto.CreatePeriodStatusRequest;
import com.evyoog.gl.periodstatus.dto.PeriodStatusResponse;
import com.evyoog.gl.periodstatus.mapper.PeriodStatusMapper;
import com.evyoog.gl.periodstatus.repository.PeriodStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodStatusServiceTest {

    @Mock
    private PeriodStatusRepository repository;
    @Mock
    private LegalEntityRepository legalEntityRepository;
    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private LegalEntityLedgerRepository legalEntityLedgerRepository;
    @Mock
    private PeriodStatusMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private PeriodStatusService service;

    private LegalEntity legalEntity;
    private AccountingPeriod period;

    @BeforeEach
    void setUp() {
        legalEntity = LegalEntity.builder().code("LE-01").name("LE One").accountingStandard(AccountingStandard.IND_AS).build();
        legalEntity.setId(UUID.randomUUID());

        period = AccountingPeriod.builder().name("APR-2026").fiscalYear("2026-27").periodNumber(1).quarterNumber(1)
                .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2026, 4, 30)).build();
        period.setId(UUID.randomUUID());

        lenient().when(mapper.toResponse(any())).thenAnswer(inv -> {
            PeriodStatus ps = inv.getArgument(0);
            return new PeriodStatusResponse(ps.getId(), ps.getLegalEntity().getId(), ps.getLegalEntity().getName(),
                    ps.getAccountingPeriod().getId(), ps.getAccountingPeriod().getName(), ps.getAccountingPeriod().getFiscalYear(),
                    ps.getStatus(), ps.getOpenedAt(), ps.getOpenedBy(), ps.getClosedAt(), ps.getClosedBy(),
                    ps.getLockedAt(), ps.getLockedBy(), ps.getCreatedAt(), ps.getUpdatedAt());
        });
        lenient().when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Ledger ledger(FinanceMode mode) {
        Ledger l = Ledger.builder().code("LDG-01").name("Ledger").financeMode(mode).ledgerCategory(LedgerCategory.PRIMARY)
                .functionalCurrency("INR").accountingStandard(AccountingStandard.IND_AS).build();
        l.setId(UUID.randomUUID());
        return l;
    }

    private PeriodStatus periodStatus(PeriodStatusEnum status) {
        PeriodStatus ps = PeriodStatus.builder().legalEntity(legalEntity).accountingPeriod(period).status(status).build();
        ps.setId(UUID.randomUUID());
        return ps;
    }

    private void stubLedger(FinanceMode mode) {
        when(legalEntityLedgerRepository.findPrimaryLedgerByLegalEntityId(legalEntity.getId()))
                .thenReturn(Optional.of(ledger(mode)));
    }

    // ---- create() ----

    @Test
    void create_success_initialisesToNotOpened() {
        when(legalEntityRepository.findById(legalEntity.getId())).thenReturn(Optional.of(legalEntity));
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));
        stubLedger(FinanceMode.THICK);
        when(repository.existsByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId())).thenReturn(false);

        PeriodStatusResponse result = service.create(
                new CreatePeriodStatusRequest(legalEntity.getId(), period.getId()), "prashanth");

        assertThat(result.status()).isEqualTo(PeriodStatusEnum.NOT_OPENED);
    }

    @Test
    void create_alreadyExists_throws409() {
        when(legalEntityRepository.findById(legalEntity.getId())).thenReturn(Optional.of(legalEntity));
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));
        stubLedger(FinanceMode.THICK);
        when(repository.existsByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreatePeriodStatusRequest(legalEntity.getId(), period.getId()), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PERIOD_STATUS_EXISTS");
    }

    @Test
    void create_eventOnlyLedger_throws409() {
        when(legalEntityRepository.findById(legalEntity.getId())).thenReturn(Optional.of(legalEntity));
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));
        stubLedger(FinanceMode.EVENT_ONLY);

        assertThatThrownBy(() -> service.create(new CreatePeriodStatusRequest(legalEntity.getId(), period.getId()), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_ONLY_NO_PERIOD_STATUS");
    }

    // ---- open() ----

    @Test
    void testOpenPeriod_fromNotOpened_success() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.NOT_OPENED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        PeriodStatusResponse result = service.open(ps.getId(), "prashanth");

        assertThat(result.status()).isEqualTo(PeriodStatusEnum.OPEN);
        assertThat(result.openedAt()).isNotNull();
        assertThat(result.openedBy()).isEqualTo("prashanth");
    }

    @Test
    void testOpenPeriod_fromClosed_throws409() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.CLOSED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        assertThatThrownBy(() -> service.open(ps.getId(), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PERIOD_TRANSITION");
    }

    @Test
    void testOpenPeriod_alreadyOpen_throws409() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.OPEN);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        assertThatThrownBy(() -> service.open(ps.getId(), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PERIOD_TRANSITION");
    }

    @Test
    void testOpenPeriod_eventOnlyLedger_throws409() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.NOT_OPENED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.EVENT_ONLY);

        assertThatThrownBy(() -> service.open(ps.getId(), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_ONLY_NO_PERIOD_STATUS");
    }

    // ---- close() ----

    @Test
    void testClosePeriod_fromOpen_success() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.OPEN);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        PeriodStatusResponse result = service.close(ps.getId(), "prashanth");

        assertThat(result.status()).isEqualTo(PeriodStatusEnum.CLOSED);
        assertThat(result.closedAt()).isNotNull();
        assertThat(result.closedBy()).isEqualTo("prashanth");
    }

    @Test
    void testClosePeriod_fromNotOpened_throws409() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.NOT_OPENED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        assertThatThrownBy(() -> service.close(ps.getId(), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PERIOD_TRANSITION");
    }

    // ---- lock() ----

    @Test
    void testLockPeriod_fromClosed_thickMode_success() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.CLOSED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        PeriodStatusResponse result = service.lock(ps.getId(), "prashanth");

        assertThat(result.status()).isEqualTo(PeriodStatusEnum.LOCKED);
        assertThat(result.lockedAt()).isNotNull();
        assertThat(result.lockedBy()).isEqualTo("prashanth");
    }

    @Test
    void testLockPeriod_thinMode_throws409() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.CLOSED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THIN);

        assertThatThrownBy(() -> service.lock(ps.getId(), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THIN_TRANSITION_NOT_ALLOWED");
    }

    // ---- future-enterable() ----

    @Test
    void testFutureEnterable_thickMode_success() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.NOT_OPENED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THICK);

        PeriodStatusResponse result = service.futureEnterable(ps.getId(), "prashanth");

        assertThat(result.status()).isEqualTo(PeriodStatusEnum.FUTURE_ENTERABLE);
    }

    @Test
    void testFutureEnterable_thinMode_throws409() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.NOT_OPENED);
        when(repository.findById(ps.getId())).thenReturn(Optional.of(ps));
        stubLedger(FinanceMode.THIN);

        assertThatThrownBy(() -> service.futureEnterable(ps.getId(), "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THIN_TRANSITION_NOT_ALLOWED");
    }

    // ---- validatePeriodOpen() ----

    @Test
    void testValidatePeriodOpen_openPeriod_passes() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.OPEN);
        stubLedger(FinanceMode.THICK);
        when(repository.findByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId()))
                .thenReturn(Optional.of(ps));

        service.validatePeriodOpen(legalEntity.getId(), period.getId());
    }

    @Test
    void testValidatePeriodOpen_closedPeriod_throws() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.CLOSED);
        stubLedger(FinanceMode.THICK);
        when(repository.findByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId()))
                .thenReturn(Optional.of(ps));

        assertThatThrownBy(() -> service.validatePeriodOpen(legalEntity.getId(), period.getId()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PERIOD_NOT_OPEN");
    }

    @Test
    void testValidatePeriodOpen_lockedPeriod_throws() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.LOCKED);
        stubLedger(FinanceMode.THICK);
        when(repository.findByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId()))
                .thenReturn(Optional.of(ps));

        assertThatThrownBy(() -> service.validatePeriodOpen(legalEntity.getId(), period.getId()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PERIOD_NOT_OPEN");
    }

    @Test
    void testValidatePeriodOpen_notOpenedPeriod_throws() {
        PeriodStatus ps = periodStatus(PeriodStatusEnum.NOT_OPENED);
        stubLedger(FinanceMode.THICK);
        when(repository.findByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId()))
                .thenReturn(Optional.of(ps));

        assertThatThrownBy(() -> service.validatePeriodOpen(legalEntity.getId(), period.getId()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PERIOD_NOT_OPEN");
    }

    @Test
    void testValidatePeriodOpen_eventOnlyMode_passes() {
        stubLedger(FinanceMode.EVENT_ONLY);

        service.validatePeriodOpen(legalEntity.getId(), period.getId());
    }

    @Test
    void testValidatePeriodOpen_noRowExists_throwsPeriodNotOpen() {
        stubLedger(FinanceMode.THICK);
        when(repository.findByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validatePeriodOpen(legalEntity.getId(), period.getId()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "PERIOD_NOT_OPEN");
    }

    // ---- auto-create ----

    @Test
    void testAutoCreateNotOpenedRow_whenNotExists() {
        when(repository.findByLegalEntityIdAndAccountingPeriodId(legalEntity.getId(), period.getId()))
                .thenReturn(Optional.empty());
        when(legalEntityRepository.findById(legalEntity.getId())).thenReturn(Optional.of(legalEntity));
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));

        PeriodStatus result = service.getOrCreateStatus(legalEntity.getId(), period.getId(), "prashanth");

        assertThat(result.getStatus()).isEqualTo(PeriodStatusEnum.NOT_OPENED);
        assertThat(result.getLegalEntity()).isEqualTo(legalEntity);
        assertThat(result.getAccountingPeriod()).isEqualTo(period);
    }
}
