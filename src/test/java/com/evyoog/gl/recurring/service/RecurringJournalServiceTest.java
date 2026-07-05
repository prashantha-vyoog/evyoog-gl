package com.evyoog.gl.recurring.service;

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
import com.evyoog.gl.posting.domain.JournalStatus;
import com.evyoog.gl.posting.repository.JournalCategoryRepository;
import com.evyoog.gl.posting.repository.JournalHeaderRepository;
import com.evyoog.gl.posting.repository.JournalSourceRepository;
import com.evyoog.gl.posting.dto.PostingResult;
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
import com.evyoog.gl.recurring.dto.RecurringRunResponse;
import com.evyoog.gl.recurring.dto.RecurringTemplateResponse;
import com.evyoog.gl.recurring.repository.RecurringJournalRunRepository;
import com.evyoog.gl.recurring.repository.RecurringJournalTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringJournalServiceTest {

    @Mock private RecurringJournalTemplateRepository templateRepository;
    @Mock private RecurringJournalRunRepository runRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private JournalSourceRepository journalSourceRepository;
    @Mock private JournalCategoryRepository journalCategoryRepository;
    @Mock private JournalHeaderRepository journalHeaderRepository;
    @Mock private PostingEngine postingEngine;
    @Mock private AuditService auditService;

    private RecurringJournalService service;

    private LegalEntity legalEntity;
    private Ledger thickLedger;
    private JournalSource recurringSource;
    private JournalCategory recurringCategory;
    private UUID legalEntityId;
    private UUID ledgerId;

    @BeforeEach
    void setUp() {
        service = new RecurringJournalService(templateRepository, runRepository, legalEntityRepository,
                ledgerRepository, accountingPeriodRepository, journalSourceRepository, journalCategoryRepository,
                journalHeaderRepository, postingEngine, auditService);

        legalEntityId = UUID.randomUUID();
        ledgerId = UUID.randomUUID();

        legalEntity = LegalEntity.builder().id(legalEntityId).code("LE1").name("LE One").build();
        thickLedger = Ledger.builder().code("LDG").name("Primary").financeMode(FinanceMode.THICK).functionalCurrency("INR").build();
        thickLedger.setId(ledgerId);
        recurringSource = JournalSource.builder().id(UUID.randomUUID()).code("RECURRING").name("Recurring").requiresApproval(false).build();
        recurringCategory = JournalCategory.builder().id(UUID.randomUUID()).code("RECURRING").name("Recurring").build();
    }

    private CreateRecurringTemplateRequest balancedRequest() {
        RecurringLineRequest line1 = new RecurringLineRequest(Map.of(), UUID.randomUUID(), new BigDecimal("500.00"), null, "Rent expense");
        RecurringLineRequest line2 = new RecurringLineRequest(Map.of(), UUID.randomUUID(), null, new BigDecimal("500.00"), "Rent payable");
        return new CreateRecurringTemplateRequest(legalEntityId, ledgerId, "Monthly Rent", "Rent accrual",
                "MONTHLY", 1, null, null, null, null, "REF-1", List.of(line1, line2), "creator1");
    }

    private RecurringJournalTemplate templateEntity(boolean active) {
        RecurringTemplateLine l1 = RecurringTemplateLine.builder().accountCombination(Map.of())
                .naturalAccountValueId(UUID.randomUUID()).debitAmount(new BigDecimal("500.00")).build();
        RecurringTemplateLine l2 = RecurringTemplateLine.builder().accountCombination(Map.of())
                .naturalAccountValueId(UUID.randomUUID()).creditAmount(new BigDecimal("500.00")).build();

        RecurringJournalTemplate template = RecurringJournalTemplate.builder()
                .legalEntity(legalEntity)
                .ledger(thickLedger)
                .name("Monthly Rent")
                .frequency(RecurringFrequency.MONTHLY)
                .journalSourceCode("RECURRING")
                .journalCategoryCode("RECURRING")
                .lines(List.of(l1, l2))
                .createdBy("creator1")
                .updatedBy("creator1")
                .build();
        template.setId(UUID.randomUUID());
        template.setActive(active);
        return template;
    }

    // ---- createTemplate() ----

    @Test
    void testCreateTemplate_thickMode_success() {
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
        when(ledgerRepository.findById(ledgerId)).thenReturn(Optional.of(thickLedger));
        when(journalSourceRepository.findByCode("RECURRING")).thenReturn(Optional.of(recurringSource));
        when(journalCategoryRepository.findByCode("RECURRING")).thenReturn(Optional.of(recurringCategory));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            RecurringJournalTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        RecurringTemplateResponse response = service.createTemplate(balancedRequest());

        assertThat(response.name()).isEqualTo("Monthly Rent");
        assertThat(response.frequency()).isEqualTo("MONTHLY");
        assertThat(response.lines()).hasSize(2);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void testCreateTemplate_thinMode_throws409() {
        Ledger thinLedger = Ledger.builder().code("LDG").name("Primary").financeMode(FinanceMode.THIN).functionalCurrency("INR").build();
        thinLedger.setId(ledgerId);

        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
        when(ledgerRepository.findById(ledgerId)).thenReturn(Optional.of(thinLedger));

        assertThatThrownBy(() -> service.createTemplate(balancedRequest()))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THICK_MODE_ONLY");

        verify(templateRepository, never()).save(any());
    }

    @Test
    void testCreateTemplate_unbalancedLines_throws400() {
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
        when(ledgerRepository.findById(ledgerId)).thenReturn(Optional.of(thickLedger));

        RecurringLineRequest line1 = new RecurringLineRequest(Map.of(), UUID.randomUUID(), new BigDecimal("500.00"), null, null);
        RecurringLineRequest line2 = new RecurringLineRequest(Map.of(), UUID.randomUUID(), null, new BigDecimal("300.00"), null);
        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest(legalEntityId, ledgerId, "Unbalanced", null,
                "MONTHLY", 1, null, null, null, null, null, List.of(line1, line2), "creator1");

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "UNBALANCED_TEMPLATE");

        verify(templateRepository, never()).save(any());
    }

    @Test
    void testCreateTemplate_invalidFrequency_throws400() {
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Optional.of(legalEntity));
        when(ledgerRepository.findById(ledgerId)).thenReturn(Optional.of(thickLedger));

        RecurringLineRequest line1 = new RecurringLineRequest(Map.of(), UUID.randomUUID(), new BigDecimal("500.00"), null, null);
        RecurringLineRequest line2 = new RecurringLineRequest(Map.of(), UUID.randomUUID(), null, new BigDecimal("500.00"), null);
        CreateRecurringTemplateRequest request = new CreateRecurringTemplateRequest(legalEntityId, ledgerId, "Bad Freq", null,
                "WEEKLY", null, null, null, null, null, null, List.of(line1, line2), "creator1");

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_FREQUENCY");

        verify(templateRepository, never()).save(any());
    }

    // ---- generate() ----

    @Test
    void testGenerate_success_journalPosted() {
        RecurringJournalTemplate template = templateEntity(true);
        UUID targetPeriodId = UUID.randomUUID();
        AccountingPeriod period = AccountingPeriod.builder().id(targetPeriodId).name("APR-2026").fiscalYear("2026-27").build();
        JournalHeader generated = JournalHeader.builder().journalNumber("JE-2618-00001").status(JournalStatus.POSTED).build();
        generated.setId(UUID.randomUUID());

        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(runRepository.existsByTemplateIdAndAccountingPeriodId(template.getId(), targetPeriodId)).thenReturn(false);
        when(accountingPeriodRepository.findById(targetPeriodId)).thenReturn(Optional.of(period));
        when(journalSourceRepository.findByCode("RECURRING")).thenReturn(Optional.of(recurringSource));
        when(journalCategoryRepository.findByCode("RECURRING")).thenReturn(Optional.of(recurringCategory));
        when(postingEngine.post(any())).thenReturn(PostingResult.posted(generated.getId(), generated.getJournalNumber(), FinanceMode.THICK));
        when(journalHeaderRepository.findById(generated.getId())).thenReturn(Optional.of(generated));
        when(runRepository.save(any())).thenAnswer(inv -> {
            RecurringJournalRun r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        GenerateRecurringJournalResponse response = service.generate(template.getId(),
                new GenerateRecurringJournalRequest(targetPeriodId, "generator1"));

        assertThat(response.templateId()).isEqualTo(template.getId());
        assertThat(response.journalHeaderId()).isEqualTo(generated.getId());
        assertThat(response.journalStatus()).isEqualTo(JournalStatus.POSTED);
        verify(runRepository).save(any());
    }

    @Test
    void testGenerate_inactiveTemplate_throws409() {
        RecurringJournalTemplate template = templateEntity(false);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.generate(template.getId(),
                new GenerateRecurringJournalRequest(UUID.randomUUID(), "generator1")))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_INACTIVE");

        verify(postingEngine, never()).post(any());
    }

    @Test
    void testGenerate_alreadyGeneratedForPeriod_throws409() {
        RecurringJournalTemplate template = templateEntity(true);
        UUID targetPeriodId = UUID.randomUUID();

        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(runRepository.existsByTemplateIdAndAccountingPeriodId(template.getId(), targetPeriodId)).thenReturn(true);

        assertThatThrownBy(() -> service.generate(template.getId(),
                new GenerateRecurringJournalRequest(targetPeriodId, "generator1")))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "ALREADY_GENERATED");

        verify(postingEngine, never()).post(any());
    }

    @Test
    void testGenerate_thinMode_throws409() {
        Ledger thinLedger = Ledger.builder().code("LDG").name("Primary").financeMode(FinanceMode.THIN).functionalCurrency("INR").build();
        thinLedger.setId(ledgerId);

        RecurringJournalTemplate template = RecurringJournalTemplate.builder()
                .legalEntity(legalEntity)
                .ledger(thinLedger)
                .name("Monthly Rent")
                .frequency(RecurringFrequency.MONTHLY)
                .journalSourceCode("RECURRING")
                .journalCategoryCode("RECURRING")
                .lines(List.of())
                .createdBy("creator1")
                .updatedBy("creator1")
                .build();
        template.setId(UUID.randomUUID());
        template.setActive(true);

        UUID targetPeriodId = UUID.randomUUID();
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.generate(template.getId(),
                new GenerateRecurringJournalRequest(targetPeriodId, "generator1")))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THICK_MODE_ONLY");

        verify(postingEngine, never()).post(any());
    }

    // ---- deactivateTemplate() ----

    @Test
    void testDeactivateTemplate_success() {
        RecurringJournalTemplate template = templateEntity(true);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecurringTemplateResponse response = service.deactivateTemplate(template.getId(),
                new DeactivateTemplateRequest("deactivator1"));

        assertThat(response.isActive()).isFalse();
        assertThat(template.isActive()).isFalse();
        assertThat(template.getUpdatedBy()).isEqualTo("deactivator1");
    }

    // ---- getRuns() ----

    @Test
    void testGetRuns_returnsHistory() {
        RecurringJournalTemplate template = templateEntity(true);
        JournalHeader j1 = JournalHeader.builder().journalNumber("JE-1").status(JournalStatus.POSTED).build();
        j1.setId(UUID.randomUUID());
        AccountingPeriod period = AccountingPeriod.builder().id(UUID.randomUUID()).name("APR-2026").fiscalYear("2026-27").build();

        RecurringJournalRun run = RecurringJournalRun.builder()
                .template(template).journalHeader(j1).accountingPeriod(period)
                .generatedAt(java.time.Instant.now()).generatedBy("generator1").build();
        run.setId(UUID.randomUUID());

        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(runRepository.findByTemplateIdOrderByGeneratedAtDesc(template.getId())).thenReturn(List.of(run));

        List<RecurringRunResponse> runs = service.getRuns(template.getId());

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).journalHeaderId()).isEqualTo(j1.getId());
        assertThat(runs.get(0).templateId()).isEqualTo(template.getId());
    }
}
