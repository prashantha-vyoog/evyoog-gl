package com.evyoog.gl.gst.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.enterprise.domain.BusinessUnit;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.BusinessUnitRepository;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.gst.domain.GstrExportJob;
import com.evyoog.gl.gst.dto.CreateGstrExportRequest;
import com.evyoog.gl.gst.dto.GstTransactionDetail;
import com.evyoog.gl.gst.dto.Gstr1LineItem;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GstService {

    private static final List<AccountQualifier> OUTWARD_QUALIFIERS =
            List.of(AccountQualifier.REVENUE, AccountQualifier.LIABILITY);

    private final JournalLineRepository journalLineRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final GstrExportJobRepository gstrExportJobRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public GstrSummaryResponse generateGstr3b(UUID legalEntityId, UUID periodId) {
        BusinessUnit businessUnit = primaryBusinessUnit(legalEntityId);
        List<JournalLine> gstLines = gstApplicableLines(legalEntityId, periodId);

        BigDecimal cgstCollected = BigDecimal.ZERO;
        BigDecimal sgstCollected = BigDecimal.ZERO;
        BigDecimal igstCollected = BigDecimal.ZERO;
        BigDecimal utgstCollected = BigDecimal.ZERO;

        BigDecimal inputCgst = BigDecimal.ZERO;
        BigDecimal inputSgst = BigDecimal.ZERO;
        BigDecimal inputIgst = BigDecimal.ZERO;
        BigDecimal inputUtgst = BigDecimal.ZERO;

        for (JournalLine line : gstLines) {
            BigDecimal amount = lineAmount(line);
            if (isOutward(line)) {
                switch (line.getGstType()) {
                    case "CGST" -> cgstCollected = cgstCollected.add(amount);
                    case "SGST" -> sgstCollected = sgstCollected.add(amount);
                    case "IGST" -> igstCollected = igstCollected.add(amount);
                    case "UTGST" -> utgstCollected = utgstCollected.add(amount);
                    default -> { }
                }
            } else {
                switch (line.getGstType()) {
                    case "CGST" -> inputCgst = inputCgst.add(amount);
                    case "SGST" -> inputSgst = inputSgst.add(amount);
                    case "IGST" -> inputIgst = inputIgst.add(amount);
                    case "UTGST" -> inputUtgst = inputUtgst.add(amount);
                    default -> { }
                }
            }
        }

        BigDecimal totalCollected = cgstCollected.add(sgstCollected).add(igstCollected).add(utgstCollected);
        BigDecimal totalInput = inputCgst.add(inputSgst).add(inputIgst).add(inputUtgst);

        AccountingPeriod period = period(periodId);
        LegalEntity legalEntity = legalEntity(legalEntityId);

        return GstrSummaryResponse.builder()
                .legalEntityId(legalEntityId)
                .legalEntityName(legalEntity.getName())
                .gstin(businessUnit.getGstin())
                .accountingPeriodId(periodId)
                .periodName(period.getName())
                .fiscalYear(period.getFiscalYear())
                .totalCgstCollected(cgstCollected)
                .totalSgstCollected(sgstCollected)
                .totalIgstCollected(igstCollected)
                .totalUtgstCollected(utgstCollected)
                .totalGstCollected(totalCollected)
                .totalInputCgst(inputCgst)
                .totalInputSgst(inputSgst)
                .totalInputIgst(inputIgst)
                .totalInputUtgst(inputUtgst)
                .totalInputTax(totalInput)
                .netCgstPayable(cgstCollected.subtract(inputCgst))
                .netSgstPayable(sgstCollected.subtract(inputSgst))
                .netIgstPayable(igstCollected.subtract(inputIgst))
                .netUtgstPayable(utgstCollected.subtract(inputUtgst))
                .netTaxPayable(totalCollected.subtract(totalInput))
                .build();
    }

    public Gstr1Response generateGstr1(UUID legalEntityId, UUID periodId) {
        BusinessUnit businessUnit = primaryBusinessUnit(legalEntityId);

        List<Gstr1LineItem> items = gstApplicableLines(legalEntityId, periodId).stream()
                .filter(this::isOutward)
                .map(line -> toGstr1LineItem(line, businessUnit))
                .toList();

        BigDecimal totalTax = items.stream()
                .map(Gstr1LineItem::totalTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountingPeriod period = period(periodId);
        LegalEntity legalEntity = legalEntity(legalEntityId);

        return Gstr1Response.builder()
                .legalEntityId(legalEntityId)
                .legalEntityName(legalEntity.getName())
                .gstin(businessUnit.getGstin())
                .accountingPeriodId(periodId)
                .periodName(period.getName())
                .fiscalYear(period.getFiscalYear())
                .outwardSupplies(items)
                .totalTax(totalTax)
                .transactionCount(items.size())
                .build();
    }

    public List<GstTransactionDetail> listTransactions(UUID legalEntityId, UUID periodId, String gstType) {
        return gstApplicableLines(legalEntityId, periodId).stream()
                .filter(line -> gstType == null || gstType.equals(line.getGstType()))
                .map(this::toTransactionDetail)
                .toList();
    }

    @Transactional
    public GstrExportResponse createExportJob(CreateGstrExportRequest request, String performedBy) {
        Object report = "GSTR1".equals(request.returnType())
                ? generateGstr1(request.legalEntityId(), request.periodId())
                : generateGstr3b(request.legalEntityId(), request.periodId());

        Map<String, Object> exportData = objectMapper.convertValue(report, new MapTypeReference());

        GstrExportJob job = GstrExportJob.builder()
                .legalEntity(legalEntity(request.legalEntityId()))
                .accountingPeriod(period(request.periodId()))
                .returnType(request.returnType())
                .status("COMPLETED")
                .exportData(exportData)
                .generatedAt(Instant.now())
                .createdBy(performedBy)
                .build();

        GstrExportJob saved = gstrExportJobRepository.save(job);

        auditService.log(AuditAction.CREATE, "gstr_export_job", saved.getId(), null,
                Map.of("returnType", saved.getReturnType(), "status", saved.getStatus(),
                        "legalEntityId", request.legalEntityId(), "periodId", request.periodId()),
                performedBy);

        return toExportResponse(saved);
    }

    public GstrExportResponse getExportJob(UUID jobId) {
        GstrExportJob job = gstrExportJobRepository.findById(jobId)
                .orElseThrow(() -> new EvyoogException("EXPORT_JOB_NOT_FOUND",
                        "GSTR export job not found.", HttpStatus.NOT_FOUND));
        return toExportResponse(job);
    }

    private GstrExportResponse toExportResponse(GstrExportJob job) {
        return GstrExportResponse.builder()
                .jobId(job.getId())
                .returnType(job.getReturnType())
                .status(job.getStatus())
                .periodName(job.getAccountingPeriod().getName())
                .gstin(primaryBusinessUnit(job.getLegalEntity().getId()).getGstin())
                .generatedAt(job.getGeneratedAt())
                .data(job.getExportData())
                .build();
    }

    private Gstr1LineItem toGstr1LineItem(JournalLine line, BusinessUnit businessUnit) {
        JournalHeader header = line.getJournalHeader();
        BigDecimal amount = lineAmount(line);

        BigDecimal cgst = "CGST".equals(line.getGstType()) ? amount : BigDecimal.ZERO;
        BigDecimal sgst = "SGST".equals(line.getGstType()) ? amount : BigDecimal.ZERO;
        BigDecimal igst = "IGST".equals(line.getGstType()) ? amount : BigDecimal.ZERO;
        BigDecimal utgst = "UTGST".equals(line.getGstType()) ? amount : BigDecimal.ZERO;

        return Gstr1LineItem.builder()
                .journalHeaderId(header.getId())
                .journalNumber(header.getJournalNumber())
                .glDate(header.getGlDate())
                .gstType(line.getGstType())
                .placeOfSupply(businessUnit.getStateCode())
                .cgstAmount(cgst)
                .sgstAmount(sgst)
                .igstAmount(igst)
                .utgstAmount(utgst)
                .totalTax(cgst.add(sgst).add(igst).add(utgst))
                .isReverseCharge(false)
                .build();
    }

    private GstTransactionDetail toTransactionDetail(JournalLine line) {
        JournalHeader header = line.getJournalHeader();
        DimensionValue account = line.getNaturalAccount();
        return GstTransactionDetail.builder()
                .journalLineId(line.getId())
                .journalHeaderId(header.getId())
                .journalNumber(header.getJournalNumber())
                .glDate(header.getGlDate())
                .accountCode(account.getCode())
                .accountName(account.getName())
                .gstType(line.getGstType())
                .transactionType(isOutward(line) ? "OUTWARD" : "INWARD")
                .amount(lineAmount(line))
                .tdsApplicable(line.getTdsApplicable())
                .tdsSection(line.getTdsSection())
                .build();
    }

    private boolean isOutward(JournalLine line) {
        AccountQualifier qualifier = line.getNaturalAccount().getAccountQualifier();
        return OUTWARD_QUALIFIERS.contains(qualifier);
    }

    private BigDecimal lineAmount(JournalLine line) {
        if (line.getCreditAmount() != null) {
            return line.getCreditAmount();
        }
        return line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
    }

    private List<JournalLine> gstApplicableLines(UUID legalEntityId, UUID periodId) {
        return journalLineRepository
                .findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndGstApplicableTrueAndJournalHeader_Status(
                        legalEntityId, periodId, JournalStatus.POSTED);
    }

    private BusinessUnit primaryBusinessUnit(UUID legalEntityId) {
        return businessUnitRepository.findFirstByLegalEntityIdAndIsActiveTrue(legalEntityId)
                .orElseThrow(() -> new EvyoogException("NO_BUSINESS_UNIT",
                        "No active Business Unit found for this Legal Entity.", HttpStatus.NOT_FOUND));
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

    private static final class MapTypeReference
            extends com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> {
    }
}
