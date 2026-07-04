package com.evyoog.gl.gst.service;

import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.BusinessUnit;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.BusinessUnitRepository;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.gst.dto.CreateGstrExportRequest;
import com.evyoog.gl.gst.dto.GstTransactionDetail;
import com.evyoog.gl.gst.dto.Gstr1Response;
import com.evyoog.gl.gst.dto.GstrExportResponse;
import com.evyoog.gl.gst.dto.GstrSummaryResponse;
import com.evyoog.gl.gst.repository.GstrExportJobRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.repository.JournalLineRepository;
import com.evyoog.gl.common.audit.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GstServiceTest {

    @Mock private JournalLineRepository journalLineRepository;
    @Mock private BusinessUnitRepository businessUnitRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private GstrExportJobRepository gstrExportJobRepository;
    @Mock private AuditService auditService;

    private GstService service;

    private UUID legalEntityId;
    private UUID periodId;
    private BusinessUnit businessUnit;
    private LegalEntity legalEntity;
    private AccountingPeriod period;

    @BeforeEach
    void setUp() {
        service = new GstService(journalLineRepository, businessUnitRepository, accountingPeriodRepository,
                legalEntityRepository, gstrExportJobRepository, auditService, new ObjectMapper());

        legalEntityId = UUID.randomUUID();
        periodId = UUID.randomUUID();

        legalEntity = LegalEntity.builder().id(legalEntityId).code("LE1").name("Legal Entity 1").build();
        period = AccountingPeriod.builder().id(periodId).name("APR-2025").fiscalYear("2025-26").build();
        businessUnit = BusinessUnit.builder().id(UUID.randomUUID())
                .code("BU1").name("Business Unit 1").gstin("33AABCE1234F1Z5").stateCode("33").build();
    }

    private DimensionValue account(String code, AccountQualifier qualifier) {
        return DimensionValue.builder().id(UUID.randomUUID()).code(code).name(code)
                .accountQualifier(qualifier).isPostable(true).isSummary(false).build();
    }

    private JournalLine gstLine(DimensionValue account, String gstType, BigDecimal debit, BigDecimal credit) {
        JournalHeader header = JournalHeader.builder()
                .id(UUID.randomUUID())
                .journalNumber("JRN-" + UUID.randomUUID())
                .glDate(LocalDate.of(2025, 4, 10))
                .status(JournalStatus.POSTED)
                .build();
        return JournalLine.builder()
                .id(UUID.randomUUID())
                .journalHeader(header)
                .naturalAccount(account)
                .debitAmount(debit)
                .creditAmount(credit)
                .gstApplicable(true)
                .gstType(gstType)
                .tdsApplicable(false)
                .build();
    }

    private void stubReferenceData(List<JournalLine> lines) {
        when(journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndGstApplicableTrueAndJournalHeader_Status(
                        legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(lines);
        lenient().when(businessUnitRepository.findFirstByLegalEntityIdAndIsActiveTrue(legalEntityId))
                .thenReturn(Optional.of(businessUnit));
        lenient().when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        lenient().when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
    }

    @Test
    void testGenerateGstr3b_cgstCollected_correctAmount() {
        DimensionValue gstPayable = account("2210", AccountQualifier.LIABILITY);
        stubReferenceData(List.of(gstLine(gstPayable, "CGST", null, new BigDecimal("90.00"))));

        GstrSummaryResponse response = service.generateGstr3b(legalEntityId, periodId);

        assertThat(response.totalCgstCollected()).isEqualByComparingTo("90.00");
        assertThat(response.gstin()).isEqualTo("33AABCE1234F1Z5");
    }

    @Test
    void testGenerateGstr3b_netPayable_collectedMinusInput() {
        DimensionValue gstPayable = account("2210", AccountQualifier.LIABILITY);
        DimensionValue gstInput = account("1310", AccountQualifier.ASSET);
        stubReferenceData(List.of(
                gstLine(gstPayable, "CGST", null, new BigDecimal("100.00")),
                gstLine(gstInput, "CGST", new BigDecimal("40.00"), null)));

        GstrSummaryResponse response = service.generateGstr3b(legalEntityId, periodId);

        assertThat(response.netCgstPayable()).isEqualByComparingTo("60.00");
        assertThat(response.netTaxPayable()).isEqualByComparingTo("60.00");
    }

    @Test
    void testGenerateGstr3b_igst_interStateSeparated() {
        DimensionValue gstPayable = account("2220", AccountQualifier.LIABILITY);
        stubReferenceData(List.of(gstLine(gstPayable, "IGST", null, new BigDecimal("180.00"))));

        GstrSummaryResponse response = service.generateGstr3b(legalEntityId, periodId);

        assertThat(response.totalIgstCollected()).isEqualByComparingTo("180.00");
        assertThat(response.totalCgstCollected()).isEqualByComparingTo("0");
        assertThat(response.totalSgstCollected()).isEqualByComparingTo("0");
    }

    @Test
    void testGenerateGstr3b_noGstLines_returnsZeroTotals() {
        stubReferenceData(List.of());

        GstrSummaryResponse response = service.generateGstr3b(legalEntityId, periodId);

        assertThat(response.totalGstCollected()).isEqualByComparingTo("0");
        assertThat(response.netTaxPayable()).isEqualByComparingTo("0");
    }

    @Test
    void testGenerateGstr1_outwardSuppliesOnly() {
        DimensionValue gstPayable = account("2210", AccountQualifier.LIABILITY);
        DimensionValue gstInput = account("1310", AccountQualifier.ASSET);
        stubReferenceData(List.of(
                gstLine(gstPayable, "CGST", null, new BigDecimal("50.00")),
                gstLine(gstInput, "CGST", new BigDecimal("20.00"), null)));

        Gstr1Response response = service.generateGstr1(legalEntityId, periodId);

        assertThat(response.outwardSupplies()).hasSize(1);
        assertThat(response.outwardSupplies().get(0).cgstAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void testGenerateGstr1_transactionCount_correct() {
        DimensionValue gstPayable = account("2210", AccountQualifier.LIABILITY);
        stubReferenceData(List.of(
                gstLine(gstPayable, "CGST", null, new BigDecimal("50.00")),
                gstLine(gstPayable, "SGST", null, new BigDecimal("50.00"))));

        Gstr1Response response = service.generateGstr1(legalEntityId, periodId);

        assertThat(response.transactionCount()).isEqualTo(2);
        assertThat(response.totalTax()).isEqualByComparingTo("100.00");
    }

    @Test
    void testListTransactions_filtersByGstType() {
        DimensionValue gstPayable = account("2210", AccountQualifier.LIABILITY);
        stubReferenceData(List.of(
                gstLine(gstPayable, "CGST", null, new BigDecimal("50.00")),
                gstLine(gstPayable, "SGST", null, new BigDecimal("50.00"))));

        List<GstTransactionDetail> details = service.listTransactions(legalEntityId, periodId, "CGST");

        assertThat(details).hasSize(1);
        assertThat(details.get(0).gstType()).isEqualTo("CGST");
        assertThat(details.get(0).transactionType()).isEqualTo("OUTWARD");
    }

    @Test
    void testGenerateGstr3b_noBusinessUnit_throwsNotFound() {
        when(businessUnitRepository.findFirstByLegalEntityIdAndIsActiveTrue(legalEntityId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateGstr3b(legalEntityId, periodId))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "NO_BUSINESS_UNIT");
    }

    @Test
    void testCreateExportJob_gstr3b_persistsAndAudits() {
        DimensionValue gstPayable = account("2210", AccountQualifier.LIABILITY);
        stubReferenceData(List.of(gstLine(gstPayable, "CGST", null, new BigDecimal("50.00"))));

        var job = com.evyoog.gl.gst.domain.GstrExportJob.builder()
                .id(UUID.randomUUID())
                .legalEntity(legalEntity)
                .accountingPeriod(period)
                .returnType("GSTR3B")
                .status("COMPLETED")
                .build();
        when(gstrExportJobRepository.save(any())).thenReturn(job);

        GstrExportResponse response = service.createExportJob(
                new CreateGstrExportRequest(legalEntityId, periodId, "GSTR3B"), "tester");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.returnType()).isEqualTo("GSTR3B");
    }
}
