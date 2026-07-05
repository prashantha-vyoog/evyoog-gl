package com.evyoog.gl.tds.service;

import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.repository.JournalLineRepository;
import com.evyoog.gl.tds.dto.TdsDetailResponse;
import com.evyoog.gl.tds.dto.TdsReportResponse;
import com.evyoog.gl.tds.dto.TdsSectionInfo;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TdsServiceTest {

    @Mock private JournalLineRepository journalLineRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private LegalEntityRepository legalEntityRepository;

    private TdsService service;

    private UUID legalEntityId;
    private UUID periodId;
    private LegalEntity legalEntity;
    private AccountingPeriod period;

    @BeforeEach
    void setUp() {
        service = new TdsService(journalLineRepository, accountingPeriodRepository, legalEntityRepository);

        legalEntityId = UUID.randomUUID();
        periodId = UUID.randomUUID();

        legalEntity = LegalEntity.builder().id(legalEntityId).code("LE1").name("Legal Entity 1").build();
        period = AccountingPeriod.builder().id(periodId).name("APR-2025").fiscalYear("2025-26").build();
    }

    private DimensionValue account(String code) {
        return DimensionValue.builder().id(UUID.randomUUID()).code(code).name(code)
                .accountQualifier(AccountQualifier.LIABILITY).isPostable(true).isSummary(false).build();
    }

    private JournalLine tdsLine(DimensionValue account, String tdsSection, BigDecimal debit, BigDecimal credit) {
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
                .tdsApplicable(true)
                .tdsSection(tdsSection)
                .build();
    }

    private void stubReportLines(List<JournalLine> lines) {
        when(journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsApplicableTrueAndJournalHeader_Status(
                        legalEntityId, periodId, JournalStatus.POSTED))
                .thenReturn(lines);
        lenient().when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        lenient().when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
    }

    @Test
    void testGenerateTdsReport_groupedBySection() {
        DimensionValue contractor = account("2310");
        stubReportLines(List.of(
                tdsLine(contractor, "194C", null, new BigDecimal("100.00")),
                tdsLine(contractor, "194C", null, new BigDecimal("50.00"))));

        TdsReportResponse response = service.generateTdsReport(legalEntityId, periodId);

        assertThat(response.sectionSummaries()).hasSize(1);
        assertThat(response.sectionSummaries().get(0).tdsSection()).isEqualTo("194C");
        assertThat(response.sectionSummaries().get(0).transactionCount()).isEqualTo(2);
        assertThat(response.sectionSummaries().get(0).totalTdsAmount()).isEqualByComparingTo("150.00");
        assertThat(response.grandTotalTds()).isEqualByComparingTo("150.00");
        assertThat(response.legalEntityName()).isEqualTo("Legal Entity 1");
    }

    @Test
    void testGenerateTdsReport_noTdsLines_returnsEmptySummaries() {
        stubReportLines(List.of());

        TdsReportResponse response = service.generateTdsReport(legalEntityId, periodId);

        assertThat(response.sectionSummaries()).isEmpty();
        assertThat(response.grandTotalTds()).isEqualByComparingTo("0");
        assertThat(response.totalTransactions()).isEqualTo(0);
    }

    @Test
    void testGenerateTdsReport_multipleSections_sortedByCode() {
        DimensionValue account = account("2310");
        stubReportLines(List.of(
                tdsLine(account, "194J", null, new BigDecimal("200.00")),
                tdsLine(account, "194C", null, new BigDecimal("100.00")),
                tdsLine(account, "192", null, new BigDecimal("300.00"))));

        TdsReportResponse response = service.generateTdsReport(legalEntityId, periodId);

        assertThat(response.sectionSummaries())
                .extracting("tdsSection")
                .containsExactly("192", "194C", "194J");
    }

    @Test
    void testGetTdsDetail_filtersBySection() {
        DimensionValue account = account("2310");
        JournalLine line = tdsLine(account, "194C", null, new BigDecimal("100.00"));
        when(journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsSectionAndJournalHeader_Status(
                        legalEntityId, periodId, "194C", JournalStatus.POSTED))
                .thenReturn(List.of(line));
        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));

        TdsDetailResponse response = service.getTdsDetail(legalEntityId, periodId, "194C");

        assertThat(response.lines()).hasSize(1);
        assertThat(response.tdsSection()).isEqualTo("194C");
        assertThat(response.totalTds()).isEqualByComparingTo("100.00");
        assertThat(response.lineCount()).isEqualTo(1);
    }

    @Test
    void testGetSectionDescription_knownSection_returnsLabel() {
        List<TdsSectionInfo> sections = service.listKnownSections();

        assertThat(sections)
                .filteredOn(s -> s.tdsSection().equals("194C"))
                .extracting(TdsSectionInfo::description)
                .containsExactly("Contractor / Subcontractor Payments");
    }

    @Test
    void testGetSectionDescription_unknownSection_returnsFallback() {
        DimensionValue account = account("2310");
        JournalLine line = tdsLine(account, "194Z", null, new BigDecimal("10.00"));
        when(journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsSectionAndJournalHeader_Status(
                        legalEntityId, periodId, "194Z", JournalStatus.POSTED))
                .thenReturn(List.of(line));
        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));

        TdsDetailResponse response = service.getTdsDetail(legalEntityId, periodId, "194Z");

        assertThat(response.sectionDescription()).isEqualTo("Section 194Z");
    }

    @Test
    void testGenerateTdsReport_legalEntityNotFound_throwsNotFound() {
        stubReportLines(List.of());
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateTdsReport(legalEntityId, periodId))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "LEGAL_ENTITY_NOT_FOUND");
    }
}
