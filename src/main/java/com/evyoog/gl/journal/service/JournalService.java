package com.evyoog.gl.journal.service;

import com.evyoog.gl.calendar.domain.AccountingCalendar;
import com.evyoog.gl.calendar.repository.AccountingCalendarRepository;
import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.repository.DimensionValueRepository;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.journal.dto.CreateJournalLineRequest;
import com.evyoog.gl.journal.dto.CreateJournalRequest;
import com.evyoog.gl.journal.dto.JournalResponse;
import com.evyoog.gl.journal.dto.JournalSummaryResponse;
import com.evyoog.gl.journal.dto.UpdateJournalRequest;
import com.evyoog.gl.journal.mapper.JournalMapper;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LegalEntityLedgerRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.service.AccountingPeriodService;
import com.evyoog.gl.posting.domain.JournalCategory;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalSource;
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.dto.PostingLineRequest;
import com.evyoog.gl.posting.dto.PostingRequest;
import com.evyoog.gl.posting.dto.PostingResult;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.evyoog.gl.posting.service.PostingEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST-facing entry point for GL-11 Manual Journal Entry. Owns request
 * validation and DRAFT/PENDING_APPROVAL lifecycle bookkeeping only — every
 * accounting rule (balance, period gate, account existence, dimension
 * validity, approval, currency) is enforced exclusively by
 * {@link PostingEngine#post(PostingRequest)}. This service never re-checks
 * those rules itself.
 */
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalHeaderRepository journalHeaderRepository;
    private final JournalSourceRepository journalSourceRepository;
    private final JournalCategoryRepository journalCategoryRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityLedgerRepository legalEntityLedgerRepository;
    private final AccountingCalendarRepository accountingCalendarRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final DimensionValueRepository dimensionValueRepository;
    private final PostingEngine postingEngine;
    private final AuditService auditService;
    private final JournalMapper mapper;

    @Transactional
    public JournalResponse create(CreateJournalRequest request, String performedBy) {
        validateLineStructure(request.lines());

        Ledger ledger = legalEntityLedgerRepository.findPrimaryLedgerByLegalEntityId(request.legalEntityId())
                .orElseThrow(() -> new EvyoogException("NO_PRIMARY_LEDGER",
                        "No primary Ledger found for this Legal Entity."));

        LocalDate accountingDate = request.accountingDate() != null ? request.accountingDate() : request.glDate();

        AccountingCalendar calendar = accountingCalendarRepository.findByLedgerId(ledger.getId())
                .orElseThrow(() -> new EvyoogException("NO_CALENDAR",
                        "No Accounting Calendar found for this Ledger.", HttpStatus.NOT_FOUND));

        AccountingPeriod period = accountingPeriodService.findPeriodByDate(calendar.getId(), accountingDate);

        JournalSource source = journalSourceRepository.findById(request.journalSourceId())
                .orElseThrow(() -> new ResourceNotFoundException("JournalSource", request.journalSourceId()));

        if (Boolean.TRUE.equals(source.getRequiresApproval())) {
            return createDraft(request, ledger, period, source, accountingDate, performedBy);
        }

        PostingRequest postingRequest = buildPostingRequest(request, ledger, period, accountingDate, performedBy, null);
        PostingResult result = postingEngine.post(postingRequest);
        return findById(result.getJournalHeaderId());
    }

    @Transactional(readOnly = true)
    public JournalResponse findById(UUID id) {
        return mapper.toResponse(findHeaderById(id));
    }

    @Transactional(readOnly = true)
    public Page<JournalSummaryResponse> list(UUID legalEntityId, JournalStatus status, UUID accountingPeriodId,
                                              String sourceCode, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        return journalHeaderRepository.search(legalEntityId, status, accountingPeriodId, sourceCode, fromDate, toDate, pageable)
                .map(mapper::toSummary);
    }

    @Transactional
    public JournalResponse update(UUID journalId, UpdateJournalRequest request, String performedBy) {
        JournalHeader journal = findHeaderById(journalId);
        if (journal.getStatus() != JournalStatus.DRAFT) {
            throw new EvyoogException("JOURNAL_NOT_EDITABLE",
                    "Only DRAFT journals can be edited. Current status: " + journal.getStatus());
        }
        if (request.description() != null) {
            journal.setDescription(request.description());
        }
        if (request.notes() != null) {
            journal.setNotes(request.notes());
        }
        journal.setUpdatedBy(performedBy);

        JournalHeader saved = journalHeaderRepository.save(journal);
        auditService.log(AuditAction.UPDATE, "journal_header", saved.getId(), null,
                journalAuditSnapshot(saved), performedBy);
        return mapper.toResponse(saved);
    }

    @Transactional
    public JournalResponse submit(UUID journalId, String performedBy) {
        JournalHeader journal = findHeaderById(journalId);
        if (journal.getStatus() != JournalStatus.DRAFT) {
            throw new EvyoogException("JOURNAL_NOT_SUBMITTABLE",
                    "Only DRAFT journals can be submitted. Current status: " + journal.getStatus());
        }

        if (Boolean.TRUE.equals(journal.getJournalSource().getRequiresApproval())) {
            journal.setStatus(JournalStatus.PENDING_APPROVAL);
            journal.setUpdatedBy(performedBy);
            JournalHeader saved = journalHeaderRepository.save(journal);
            auditService.log(AuditAction.UPDATE, "journal_header", saved.getId(), null,
                    journalAuditSnapshot(saved), performedBy);
            return mapper.toResponse(saved);
        }

        PostingRequest req = buildPostingRequestFromHeader(journal, performedBy, journal.getStatus());
        PostingResult result = postingEngine.post(req);
        return findById(result.getJournalHeaderId());
    }

    @Transactional
    public JournalResponse post(UUID journalId, String performedBy) {
        JournalHeader journal = findHeaderById(journalId);
        if (journal.getStatus() != JournalStatus.APPROVED) {
            throw new EvyoogException("JOURNAL_NOT_APPROVABLE",
                    "Only APPROVED journals can be posted via this endpoint. Current status: " + journal.getStatus());
        }
        PostingRequest req = buildPostingRequestFromHeader(journal, performedBy, JournalStatus.APPROVED);
        PostingResult result = postingEngine.post(req);
        return findById(result.getJournalHeaderId());
    }

    @Transactional
    public JournalResponse cancel(UUID journalId, String performedBy) {
        JournalHeader journal = findHeaderById(journalId);
        if (journal.getStatus() == JournalStatus.POSTED || journal.getStatus() == JournalStatus.REVERSED) {
            throw new EvyoogException("JOURNAL_NOT_CANCELLABLE",
                    "Posted or reversed journals cannot be cancelled.");
        }
        journal.setStatus(JournalStatus.CANCELLED);
        journal.setUpdatedBy(performedBy);
        JournalHeader saved = journalHeaderRepository.save(journal);
        auditService.log(AuditAction.UPDATE, "journal_header", saved.getId(), null,
                journalAuditSnapshot(saved), performedBy);
        return mapper.toResponse(saved);
    }

    private JournalResponse createDraft(CreateJournalRequest request, Ledger ledger, AccountingPeriod period,
                                         JournalSource source, LocalDate accountingDate, String performedBy) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.legalEntityId())
                .orElseThrow(() -> new ResourceNotFoundException("LegalEntity", request.legalEntityId()));
        JournalCategory category = journalCategoryRepository.findById(request.journalCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("JournalCategory", request.journalCategoryId()));

        JournalHeader header = JournalHeader.builder()
                .journalNumber(generateJournalNumber())
                .legalEntity(legalEntity)
                .ledger(ledger)
                .accountingPeriod(period)
                .journalSource(source)
                .journalCategory(category)
                .description(request.description())
                .glDate(request.glDate())
                .accountingDate(accountingDate)
                .currencyCode(request.currencyCode() != null ? request.currencyCode() : "INR")
                .exchangeRate(request.exchangeRate() != null ? request.exchangeRate() : BigDecimal.ONE)
                .totalDebit(sumLines(request.lines(), CreateJournalLineRequest::debitAmount))
                .totalCredit(sumLines(request.lines(), CreateJournalLineRequest::creditAmount))
                .status(JournalStatus.DRAFT)
                // IMMUTABLE — set once here, never updated after this point.
                .financeModeSnapshot(ledger.getFinanceMode())
                .externalReference(request.externalReference())
                .notes(request.notes())
                .extendedAttributes(request.extendedAttributes())
                .createdBy(performedBy)
                .updatedBy(performedBy)
                .build();

        List<JournalLine> lines = buildJournalLines(request.lines(), header);
        header.setLines(lines);

        JournalHeader saved = journalHeaderRepository.save(header);
        auditService.log(AuditAction.CREATE, "journal_header", saved.getId(), null,
                journalAuditSnapshot(saved), performedBy);
        return mapper.toResponse(saved);
    }

    private List<JournalLine> buildJournalLines(List<CreateJournalLineRequest> lineRequests, JournalHeader header) {
        List<JournalLine> lines = new ArrayList<>();
        int lineNumber = 1;
        for (CreateJournalLineRequest lineRequest : lineRequests) {
            DimensionValue naturalAccount = dimensionValueRepository.findById(lineRequest.naturalAccountValueId())
                    .orElseThrow(() -> new EvyoogException("ACCOUNT_NOT_FOUND",
                            "Account not found: " + lineRequest.naturalAccountValueId(), HttpStatus.NOT_FOUND));
            lines.add(JournalLine.builder()
                    .journalHeader(header)
                    .lineNumber(lineNumber++)
                    .accountCombination(lineRequest.accountCombination() != null
                            ? lineRequest.accountCombination() : Map.of())
                    .naturalAccount(naturalAccount)
                    .debitAmount(lineRequest.debitAmount())
                    .creditAmount(lineRequest.creditAmount())
                    .currencyCode(lineRequest.currencyCode() != null ? lineRequest.currencyCode() : "INR")
                    .description(lineRequest.description())
                    .gstApplicable(Boolean.TRUE.equals(lineRequest.gstApplicable()))
                    .gstType(lineRequest.gstType())
                    .tdsApplicable(Boolean.TRUE.equals(lineRequest.tdsApplicable()))
                    .tdsSection(lineRequest.tdsSection())
                    .extendedAttributes(lineRequest.extendedAttributes())
                    .build());
        }
        return lines;
    }

    private PostingRequest buildPostingRequest(CreateJournalRequest request, Ledger ledger, AccountingPeriod period,
                                                LocalDate accountingDate, String performedBy, JournalStatus initialStatus) {
        List<PostingLineRequest> lines = request.lines().stream()
                .map(lr -> PostingLineRequest.builder()
                        .accountCombination(lr.accountCombination())
                        .naturalAccountValueId(lr.naturalAccountValueId())
                        .debitAmount(lr.debitAmount())
                        .creditAmount(lr.creditAmount())
                        .currencyCode(lr.currencyCode())
                        .description(lr.description())
                        .gstApplicable(lr.gstApplicable())
                        .gstType(lr.gstType())
                        .tdsApplicable(lr.tdsApplicable())
                        .tdsSection(lr.tdsSection())
                        .extendedAttributes(lr.extendedAttributes())
                        .build())
                .toList();

        return PostingRequest.builder()
                .legalEntityId(request.legalEntityId())
                .accountingPeriodId(period.getId())
                .journalSourceId(request.journalSourceId())
                .journalCategoryId(request.journalCategoryId())
                .description(request.description())
                .glDate(request.glDate())
                .accountingDate(accountingDate)
                .currencyCode(request.currencyCode())
                .exchangeRate(request.exchangeRate())
                .lines(lines)
                .performedBy(performedBy)
                .externalReference(request.externalReference())
                .extendedAttributes(request.extendedAttributes())
                .initialStatus(initialStatus)
                .build();
    }

    private PostingRequest buildPostingRequestFromHeader(JournalHeader journal, String performedBy, JournalStatus initialStatus) {
        List<PostingLineRequest> lines = journal.getLines().stream()
                .map(line -> PostingLineRequest.builder()
                        .accountCombination(line.getAccountCombination())
                        .naturalAccountValueId(line.getNaturalAccount().getId())
                        .debitAmount(line.getDebitAmount())
                        .creditAmount(line.getCreditAmount())
                        .currencyCode(line.getCurrencyCode())
                        .description(line.getDescription())
                        .gstApplicable(line.getGstApplicable())
                        .gstType(line.getGstType())
                        .tdsApplicable(line.getTdsApplicable())
                        .tdsSection(line.getTdsSection())
                        .extendedAttributes(line.getExtendedAttributes())
                        .build())
                .toList();

        return PostingRequest.builder()
                .legalEntityId(journal.getLegalEntity().getId())
                .accountingPeriodId(journal.getAccountingPeriod().getId())
                .journalSourceId(journal.getJournalSource().getId())
                .journalCategoryId(journal.getJournalCategory().getId())
                .description(journal.getDescription())
                .glDate(journal.getGlDate())
                .accountingDate(journal.getAccountingDate())
                .currencyCode(journal.getCurrencyCode())
                .exchangeRate(journal.getExchangeRate())
                .lines(lines)
                .performedBy(performedBy)
                .externalReference(journal.getExternalReference())
                .extendedAttributes(journal.getExtendedAttributes())
                .initialStatus(initialStatus)
                .build();
    }

    private void validateLineStructure(List<CreateJournalLineRequest> lines) {
        if (lines == null || lines.size() < 2) {
            throw new EvyoogException("MINIMUM_TWO_LINES",
                    "A journal entry must have at least two lines.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (int i = 0; i < lines.size(); i++) {
            CreateJournalLineRequest line = lines.get(i);
            boolean hasDebit = line.debitAmount() != null && line.debitAmount().compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = line.creditAmount() != null && line.creditAmount().compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit == hasCredit) {
                throw new EvyoogException("INVALID_LINE_AMOUNTS",
                        "Line " + (i + 1) + ": exactly one of debitAmount or creditAmount must be provided and > 0.",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    private BigDecimal sumLines(List<CreateJournalLineRequest> lines, java.util.function.Function<CreateJournalLineRequest, BigDecimal> extractor) {
        return lines.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Format: JE-YYWW-NNNNN — YY = 2-digit year, WW = ISO week, NNNNN = sequence.
    // Shared numbering space with PostingEngine — uniqueness is enforced by the
    // journal_number UNIQUE constraint regardless of which path created the row.
    private String generateJournalNumber() {
        LocalDate today = LocalDate.now();
        String year = String.valueOf(today.getYear()).substring(2);
        String week = String.format("%02d", today.get(WeekFields.ISO.weekOfWeekBasedYear()));
        Instant weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long sequence = journalHeaderRepository.countByCreatedAtAfter(weekStart);
        return String.format("JE-%s%s-%05d", year, week, sequence + 1);
    }

    private Map<String, Object> journalAuditSnapshot(JournalHeader header) {
        return Map.of(
                "journalNumber", header.getJournalNumber(),
                "status", header.getStatus(),
                "financeModeSnapshot", header.getFinanceModeSnapshot(),
                "totalDebit", header.getTotalDebit(),
                "totalCredit", header.getTotalCredit());
    }

    private JournalHeader findHeaderById(UUID id) {
        return journalHeaderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalHeader", id));
    }
}
