package com.evyoog.gl.recurring.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LedgerRepository;
import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.repository.AccountingPeriodRepository;
import com.evyoog.gl.posting.domain.JournalCategory;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalSource;
import com.evyoog.gl.posting.dto.PostingLineRequest;
import com.evyoog.gl.posting.dto.PostingRequest;
import com.evyoog.gl.posting.dto.PostingResult;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.evyoog.gl.posting.service.PostingEngine;
import com.evyoog.gl.recurring.domain.RecurringFrequency;
import com.evyoog.gl.recurring.domain.RecurringJournalRun;
import com.evyoog.gl.recurring.domain.RecurringJournalTemplate;
import com.evyoog.gl.recurring.domain.RecurringTemplateLine;
import com.evyoog.gl.recurring.dto.CreateRecurringTemplateRequest;
import com.evyoog.gl.recurring.dto.DeactivateTemplateRequest;
import com.evyoog.gl.recurring.dto.GenerateRecurringJournalRequest;
import com.evyoog.gl.recurring.dto.GenerateRecurringJournalResponse;
import com.evyoog.gl.recurring.dto.RecurringLineRequest;
import com.evyoog.gl.recurring.dto.RecurringLineResponse;
import com.evyoog.gl.recurring.dto.RecurringRunResponse;
import com.evyoog.gl.recurring.dto.RecurringTemplateResponse;
import com.evyoog.gl.recurring.repository.RecurringJournalRunRepository;
import com.evyoog.gl.recurring.repository.RecurringJournalTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * GL-14 Recurring Journals. Depends on GL-11's JournalHeader lifecycle and the
 * PostingEngine (GL-15) — generate() posts a brand-new journal built from the
 * template's stored lines via {@link PostingEngine#post(PostingRequest)}.
 * Recurring Journals are THICK-mode only, mirroring the GL-12/GL-13 gates.
 * The period-open gate is enforced by the PostingEngine itself — this service
 * does not re-check it.
 */
@Service
@RequiredArgsConstructor
public class RecurringJournalService {

    private final RecurringJournalTemplateRepository templateRepository;
    private final RecurringJournalRunRepository runRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final LedgerRepository ledgerRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final JournalSourceRepository journalSourceRepository;
    private final JournalCategoryRepository journalCategoryRepository;
    private final JournalHeaderRepository journalHeaderRepository;
    private final PostingEngine postingEngine;
    private final AuditService auditService;

    @Transactional
    public RecurringTemplateResponse createTemplate(CreateRecurringTemplateRequest request) {
        LegalEntity legalEntity = legalEntityRepository.findById(request.legalEntityId())
                .orElseThrow(() -> new EvyoogException("LE_NOT_FOUND",
                        "Legal Entity not found: " + request.legalEntityId(), HttpStatus.NOT_FOUND));
        Ledger ledger = ledgerRepository.findById(request.ledgerId())
                .orElseThrow(() -> new EvyoogException("LEDGER_NOT_FOUND",
                        "Ledger not found: " + request.ledgerId(), HttpStatus.NOT_FOUND));

        if (ledger.getFinanceMode() != FinanceMode.THICK) {
            throw new EvyoogException("THICK_MODE_ONLY",
                    "Recurring Journals are only available for THICK mode ledgers.");
        }

        RecurringFrequency frequency = parseFrequency(request.frequency());
        validateBalance(request.lines());

        String sourceCode = request.journalSourceCode() != null ? request.journalSourceCode() : "RECURRING";
        String categoryCode = request.journalCategoryCode() != null ? request.journalCategoryCode() : "RECURRING";
        journalSourceRepository.findByCode(sourceCode)
                .orElseThrow(() -> new EvyoogException("JOURNAL_SOURCE_NOT_CONFIGURED",
                        "No '" + sourceCode + "' Journal Source configured.", HttpStatus.NOT_FOUND));
        journalCategoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new EvyoogException("JOURNAL_CATEGORY_NOT_CONFIGURED",
                        "No '" + categoryCode + "' Journal Category configured.", HttpStatus.NOT_FOUND));

        AccountingPeriod startPeriod = request.startPeriodId() != null ? findPeriod(request.startPeriodId(), "START_PERIOD_NOT_FOUND") : null;
        AccountingPeriod endPeriod = request.endPeriodId() != null ? findPeriod(request.endPeriodId(), "END_PERIOD_NOT_FOUND") : null;

        List<RecurringTemplateLine> lines = request.lines().stream()
                .map(this::toTemplateLine)
                .toList();

        RecurringJournalTemplate template = RecurringJournalTemplate.builder()
                .legalEntity(legalEntity)
                .ledger(ledger)
                .name(request.name())
                .description(request.description())
                .frequency(frequency)
                .dayOfMonth(request.dayOfMonth())
                .startPeriod(startPeriod)
                .endPeriod(endPeriod)
                .journalSourceCode(sourceCode)
                .journalCategoryCode(categoryCode)
                .reference(request.reference())
                .lines(lines)
                .createdBy(request.createdBy())
                .updatedBy(request.createdBy())
                .build();

        RecurringJournalTemplate saved = templateRepository.save(template);
        auditService.log(AuditAction.CREATE, "recurring_journal_template", saved.getId(), null,
                templateAuditSnapshot(saved), request.createdBy());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecurringTemplateResponse getTemplate(UUID id) {
        return toResponse(findTemplate(id));
    }

    @Transactional(readOnly = true)
    public List<RecurringTemplateResponse> listTemplates(UUID legalEntityId) {
        return templateRepository.findByLegalEntityIdAndIsActiveTrue(legalEntityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RecurringTemplateResponse deactivateTemplate(UUID id, DeactivateTemplateRequest request) {
        RecurringJournalTemplate template = findTemplate(id);
        Map<String, Object> before = templateAuditSnapshot(template);

        template.setActive(false);
        template.setUpdatedBy(request.deactivatedBy());
        RecurringJournalTemplate saved = templateRepository.save(template);

        auditService.log(AuditAction.UPDATE, "recurring_journal_template", saved.getId(), before,
                templateAuditSnapshot(saved), request.deactivatedBy());

        return toResponse(saved);
    }

    @Transactional
    public GenerateRecurringJournalResponse generate(UUID templateId, GenerateRecurringJournalRequest request) {
        RecurringJournalTemplate template = findTemplate(templateId);

        if (!template.isActive()) {
            throw new EvyoogException("TEMPLATE_INACTIVE", "Cannot generate from an inactive template.");
        }
        if (template.getLedger().getFinanceMode() != FinanceMode.THICK) {
            throw new EvyoogException("THICK_MODE_ONLY",
                    "Recurring Journals are only available for THICK mode ledgers.");
        }
        if (runRepository.existsByTemplateIdAndAccountingPeriodId(templateId, request.targetPeriodId())) {
            throw new EvyoogException("ALREADY_GENERATED",
                    "A journal has already been generated from this template for the target period.");
        }

        AccountingPeriod targetPeriod = findPeriod(request.targetPeriodId(), "PERIOD_NOT_FOUND");

        PostingResult result = postingEngine.post(buildPostingRequest(template, request));
        JournalHeader generated = journalHeaderRepository.findById(result.getJournalHeaderId())
                .orElseThrow(() -> new EvyoogException("JOURNAL_NOT_FOUND", "Generated journal not found.", HttpStatus.NOT_FOUND));

        Instant generatedAt = Instant.now();
        RecurringJournalRun run = RecurringJournalRun.builder()
                .template(template)
                .journalHeader(generated)
                .accountingPeriod(targetPeriod)
                .generatedAt(generatedAt)
                .generatedBy(request.generatedBy())
                .build();
        RecurringJournalRun savedRun = runRepository.save(run);

        auditService.log(AuditAction.CREATE, "recurring_journal_run", savedRun.getId(), null,
                runAuditSnapshot(savedRun), request.generatedBy());

        return GenerateRecurringJournalResponse.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .journalHeaderId(generated.getId())
                .journalNumber(generated.getJournalNumber())
                .targetPeriodId(targetPeriod.getId())
                .targetPeriodName(targetPeriod.getName())
                .journalStatus(generated.getStatus())
                .generatedAt(generatedAt)
                .message("Journal generated and posted successfully.")
                .build();
    }

    @Transactional(readOnly = true)
    public List<RecurringRunResponse> getRuns(UUID templateId) {
        findTemplate(templateId);
        return runRepository.findByTemplateIdOrderByGeneratedAtDesc(templateId).stream()
                .map(this::toRunResponse)
                .toList();
    }

    private PostingRequest buildPostingRequest(RecurringJournalTemplate template, GenerateRecurringJournalRequest request) {
        JournalSource source = journalSourceRepository.findByCode(template.getJournalSourceCode())
                .orElseThrow(() -> new EvyoogException("JOURNAL_SOURCE_NOT_CONFIGURED",
                        "No '" + template.getJournalSourceCode() + "' Journal Source configured.", HttpStatus.NOT_FOUND));
        JournalCategory category = journalCategoryRepository.findByCode(template.getJournalCategoryCode())
                .orElseThrow(() -> new EvyoogException("JOURNAL_CATEGORY_NOT_CONFIGURED",
                        "No '" + template.getJournalCategoryCode() + "' Journal Category configured.", HttpStatus.NOT_FOUND));

        List<PostingLineRequest> lines = template.getLines().stream()
                .map(line -> PostingLineRequest.builder()
                        .accountCombination(line.getAccountCombination())
                        .naturalAccountValueId(line.getNaturalAccountValueId())
                        .debitAmount(line.getDebitAmount())
                        .creditAmount(line.getCreditAmount())
                        .description(line.getDescription())
                        .build())
                .toList();

        LocalDate today = LocalDate.now();
        String description = "Recurring: " + template.getName()
                + (template.getReference() != null ? " (" + template.getReference() + ")" : "");

        return PostingRequest.builder()
                .legalEntityId(template.getLegalEntity().getId())
                .accountingPeriodId(request.targetPeriodId())
                .journalSourceId(source.getId())
                .journalCategoryId(category.getId())
                .description(description)
                .glDate(today)
                .accountingDate(today)
                .currencyCode("INR")
                .exchangeRate(BigDecimal.ONE)
                .lines(lines)
                .performedBy(request.generatedBy())
                .build();
    }

    private RecurringTemplateLine toTemplateLine(RecurringLineRequest line) {
        return RecurringTemplateLine.builder()
                .accountCombination(line.accountCombination())
                .naturalAccountValueId(line.naturalAccountValueId())
                .debitAmount(line.debitAmount())
                .creditAmount(line.creditAmount())
                .description(line.description())
                .build();
    }

    private RecurringFrequency parseFrequency(String frequency) {
        try {
            return RecurringFrequency.valueOf(frequency.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new EvyoogException("INVALID_FREQUENCY",
                    "Frequency must be one of MONTHLY, QUARTERLY, ANNUAL. Got: " + frequency,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateBalance(List<RecurringLineRequest> lines) {
        BigDecimal totalDr = sum(lines, RecurringLineRequest::debitAmount);
        BigDecimal totalCr = sum(lines, RecurringLineRequest::creditAmount);
        if (totalDr.compareTo(totalCr) != 0) {
            throw new EvyoogException("UNBALANCED_TEMPLATE",
                    "Template lines do not balance. DR: " + totalDr + " CR: " + totalCr,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private BigDecimal sum(List<RecurringLineRequest> lines, Function<RecurringLineRequest, BigDecimal> extractor) {
        return lines.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AccountingPeriod findPeriod(UUID id, String errorCode) {
        return accountingPeriodRepository.findById(id)
                .orElseThrow(() -> new EvyoogException(errorCode, "Accounting period not found: " + id, HttpStatus.NOT_FOUND));
    }

    private RecurringJournalTemplate findTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new EvyoogException("TEMPLATE_NOT_FOUND",
                        "Recurring template not found: " + id, HttpStatus.NOT_FOUND));
    }

    private RecurringTemplateResponse toResponse(RecurringJournalTemplate template) {
        List<RecurringLineResponse> lines = template.getLines().stream()
                .map(l -> RecurringLineResponse.builder()
                        .accountCombination(l.getAccountCombination())
                        .naturalAccountValueId(l.getNaturalAccountValueId())
                        .debitAmount(l.getDebitAmount())
                        .creditAmount(l.getCreditAmount())
                        .description(l.getDescription())
                        .build())
                .toList();

        return RecurringTemplateResponse.builder()
                .id(template.getId())
                .legalEntityId(template.getLegalEntity().getId())
                .legalEntityName(template.getLegalEntity().getName())
                .ledgerId(template.getLedger().getId())
                .ledgerName(template.getLedger().getName())
                .name(template.getName())
                .description(template.getDescription())
                .frequency(template.getFrequency().name())
                .dayOfMonth(template.getDayOfMonth())
                .startPeriodId(template.getStartPeriod() != null ? template.getStartPeriod().getId() : null)
                .endPeriodId(template.getEndPeriod() != null ? template.getEndPeriod().getId() : null)
                .journalSourceCode(template.getJournalSourceCode())
                .journalCategoryCode(template.getJournalCategoryCode())
                .reference(template.getReference())
                .lines(lines)
                .isActive(template.isActive())
                .createdAt(template.getCreatedAt())
                .build();
    }

    private RecurringRunResponse toRunResponse(RecurringJournalRun run) {
        return RecurringRunResponse.builder()
                .id(run.getId())
                .templateId(run.getTemplate().getId())
                .templateName(run.getTemplate().getName())
                .journalHeaderId(run.getJournalHeader().getId())
                .journalNumber(run.getJournalHeader().getJournalNumber())
                .accountingPeriodId(run.getAccountingPeriod().getId())
                .periodName(run.getAccountingPeriod().getName())
                .generatedAt(run.getGeneratedAt())
                .generatedBy(run.getGeneratedBy())
                .build();
    }

    private Map<String, Object> templateAuditSnapshot(RecurringJournalTemplate template) {
        return Map.of(
                "name", template.getName(),
                "frequency", template.getFrequency(),
                "isActive", template.isActive());
    }

    private Map<String, Object> runAuditSnapshot(RecurringJournalRun run) {
        return Map.of(
                "templateId", run.getTemplate().getId(),
                "journalHeaderId", run.getJournalHeader().getId(),
                "generatedAt", run.getGeneratedAt());
    }
}
