package com.evyoog.gl.tds.service;

import com.evyoog.gl.common.exception.EvyoogException;
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
import com.evyoog.gl.tds.dto.TdsLineItem;
import com.evyoog.gl.tds.dto.TdsReportResponse;
import com.evyoog.gl.tds.dto.TdsSectionInfo;
import com.evyoog.gl.tds.dto.TdsSectionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TdsService {

    private static final Map<String, String> TDS_SECTION_DESCRIPTIONS = new LinkedHashMap<>();

    static {
        TDS_SECTION_DESCRIPTIONS.put("192", "Salaries");
        TDS_SECTION_DESCRIPTIONS.put("194A", "Interest other than Securities");
        TDS_SECTION_DESCRIPTIONS.put("194C", "Contractor / Subcontractor Payments");
        TDS_SECTION_DESCRIPTIONS.put("194H", "Commission and Brokerage");
        TDS_SECTION_DESCRIPTIONS.put("194I", "Rent");
        TDS_SECTION_DESCRIPTIONS.put("194J", "Professional / Technical Services");
        TDS_SECTION_DESCRIPTIONS.put("194Q", "Purchase of Goods");
        TDS_SECTION_DESCRIPTIONS.put("206C", "TCS on Sale of Goods");
    }

    private final JournalLineRepository journalLineRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final LegalEntityRepository legalEntityRepository;

    public TdsReportResponse generateTdsReport(UUID legalEntityId, UUID periodId) {
        List<JournalLine> tdsLines = tdsApplicableLines(legalEntityId, periodId);

        Map<String, List<JournalLine>> bySection = tdsLines.stream()
                .filter(l -> l.getTdsSection() != null && !l.getTdsSection().isBlank())
                .collect(Collectors.groupingBy(JournalLine::getTdsSection));

        List<TdsSectionSummary> summaries = bySection.entrySet().stream()
                .map(entry -> toSectionSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(TdsSectionSummary::tdsSection))
                .collect(Collectors.toList());

        BigDecimal grandTotal = summaries.stream()
                .map(TdsSectionSummary::totalTdsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalTx = summaries.stream()
                .mapToInt(TdsSectionSummary::transactionCount)
                .sum();

        AccountingPeriod period = period(periodId);
        LegalEntity legalEntity = legalEntity(legalEntityId);

        return TdsReportResponse.builder()
                .legalEntityId(legalEntityId)
                .legalEntityName(legalEntity.getName())
                .accountingPeriodId(periodId)
                .periodName(period.getName())
                .fiscalYear(period.getFiscalYear())
                .generatedAt(LocalDate.now())
                .sectionSummaries(summaries)
                .grandTotalPayments(grandTotal)
                .grandTotalTds(grandTotal)
                .totalTransactions(totalTx)
                .build();
    }

    public TdsDetailResponse getTdsDetail(UUID legalEntityId, UUID periodId, String tdsSection) {
        List<JournalLine> lines = journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsSectionAndJournalHeader_Status(
                        legalEntityId, periodId, tdsSection, JournalStatus.POSTED);

        List<TdsLineItem> items = lines.stream()
                .map(this::toLineItem)
                .collect(Collectors.toList());

        BigDecimal total = items.stream()
                .map(TdsLineItem::tdsAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LegalEntity legalEntity = legalEntity(legalEntityId);
        AccountingPeriod period = period(periodId);

        return TdsDetailResponse.builder()
                .legalEntityId(legalEntityId)
                .legalEntityName(legalEntity.getName())
                .periodName(period.getName())
                .tdsSection(tdsSection)
                .sectionDescription(getSectionDescription(tdsSection))
                .lines(items)
                .totalTds(total)
                .lineCount(items.size())
                .build();
    }

    public List<TdsSectionInfo> listKnownSections() {
        return TDS_SECTION_DESCRIPTIONS.entrySet().stream()
                .map(e -> TdsSectionInfo.builder().tdsSection(e.getKey()).description(e.getValue()).build())
                .collect(Collectors.toList());
    }

    private TdsSectionSummary toSectionSummary(String section, List<JournalLine> lines) {
        BigDecimal totalTds = lines.stream()
                .map(this::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rate = totalTds.compareTo(BigDecimal.ZERO) > 0
                ? totalTds.multiply(BigDecimal.valueOf(100)).divide(totalTds, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return TdsSectionSummary.builder()
                .tdsSection(section)
                .sectionDescription(getSectionDescription(section))
                .transactionCount(lines.size())
                .totalPaymentAmount(totalTds)
                .totalTdsAmount(totalTds)
                .effectiveRate(rate)
                .build();
    }

    private TdsLineItem toLineItem(JournalLine line) {
        JournalHeader header = line.getJournalHeader();
        DimensionValue account = line.getNaturalAccount();
        return TdsLineItem.builder()
                .journalNumber(header.getJournalNumber())
                .glDate(header.getGlDate())
                .tdsSection(line.getTdsSection())
                .sectionDescription(getSectionDescription(line.getTdsSection()))
                .accountCode(account.getCode())
                .accountName(account.getName())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .tdsAmount(lineAmount(line))
                .description(line.getDescription())
                .build();
    }

    private BigDecimal lineAmount(JournalLine line) {
        if (line.getDebitAmount() != null) {
            return line.getDebitAmount();
        }
        return line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;
    }

    private String getSectionDescription(String section) {
        return TDS_SECTION_DESCRIPTIONS.getOrDefault(section, "Section " + section);
    }

    private List<JournalLine> tdsApplicableLines(UUID legalEntityId, UUID periodId) {
        return journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsApplicableTrueAndJournalHeader_Status(
                        legalEntityId, periodId, JournalStatus.POSTED);
    }

    private AccountingPeriod period(UUID periodId) {
        return accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new EvyoogException("PERIOD_NOT_FOUND",
                        "Accounting period not found.", HttpStatus.NOT_FOUND));
    }

    private LegalEntity legalEntity(UUID legalEntityId) {
        return legalEntityRepository.findById(legalEntityId)
                .orElseThrow(() -> new EvyoogException("LEGAL_ENTITY_NOT_FOUND",
                        "Legal Entity not found.", HttpStatus.NOT_FOUND));
    }
}
