package com.evyoog.gl.coa.service;

import com.evyoog.gl.coa.domain.ImportJobStatus;
import com.evyoog.gl.coa.dto.AccountResponse;
import com.evyoog.gl.coa.dto.CoaImportJobResponse;
import com.evyoog.gl.coa.excel.dto.ParsedAccountRow;
import com.evyoog.gl.coa.excel.service.CoaExcelParserService;
import com.evyoog.gl.coa.excel.service.CoaImportRowService;
import com.evyoog.gl.coa.mapper.CoaImportJobMapperImpl;
import com.evyoog.gl.coa.repository.CoaImportJobRepository;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionType;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.domain.FinanceDimension;
import com.evyoog.gl.dimension.repository.DimensionValueRepository;
import com.evyoog.gl.dimension.repository.FinanceDimensionRepository;
import com.evyoog.gl.ledger.domain.Ledger;
import com.evyoog.gl.ledger.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoaImportJobServiceTest {

    @Mock
    private CoaImportJobRepository repository;
    @Mock
    private CoaExcelParserService excelParserService;
    @Mock
    private CoaImportRowService coaImportRowService;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private FinanceDimensionRepository financeDimensionRepository;
    @Mock
    private DimensionValueRepository dimensionValueRepository;
    @Mock
    private AuditService auditService;

    private CoaImportJobService service;
    private UUID ledgerId;
    private FinanceDimension dimension;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = new CoaImportJobService(repository, new CoaImportJobMapperImpl(), excelParserService,
                coaImportRowService, ledgerRepository, financeDimensionRepository, dimensionValueRepository,
                auditService);

        ledgerId = UUID.randomUUID();
        Ledger ledger = Ledger.builder().code("LDG").name("Ledger").build();
        ledger.setId(ledgerId);

        dimension = FinanceDimension.builder().code("NA").name("Natural Account")
                .dimensionType(DimensionType.NATURAL_ACCOUNT).ledger(ledger).build();
        dimension.setId(UUID.randomUUID());

        file = new MockMultipartFile("file", "coa.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        lenient().when(ledgerRepository.findById(ledgerId)).thenReturn(Optional.of(ledger));
        lenient().when(financeDimensionRepository.findByLedgerIdAndDimensionTypeAndIsActiveTrue(ledgerId, DimensionType.NATURAL_ACCOUNT))
                .thenReturn(Optional.of(dimension));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void importFromExcel_ledgerNotFound_throws404() {
        when(ledgerRepository.findById(ledgerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importFromExcel(file, ledgerId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void importFromExcel_noNaturalAccountDimension_throws404() {
        when(financeDimensionRepository.findByLedgerIdAndDimensionTypeAndIsActiveTrue(ledgerId, DimensionType.NATURAL_ACCOUNT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importFromExcel(file, ledgerId, null))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "NO_NATURAL_ACCOUNT_DIM");
    }

    @Test
    void importFromExcel_noDataRows_returnsFailed() throws Exception {
        when(excelParserService.parse(file)).thenReturn(List.of());

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(response.totalRows()).isZero();
        assertThat(response.errorDetails()).isNotNull();
    }

    @Test
    void importFromExcel_allValid_returnsCompleted() throws Exception {
        ParsedAccountRow row1 = new ParsedAccountRow(2, "1000", "Assets", "ASSET", null, null, true, null, null);
        ParsedAccountRow row2 = new ParsedAccountRow(3, "1100", "Cash", "ASSET", "1000", true, false, null, null);
        when(excelParserService.parse(file)).thenReturn(List.of(row1, row2));
        when(coaImportRowService.createAccountIsolated(any(), anyString()))
                .thenReturn(accountResponse(UUID.randomUUID(), "1000"))
                .thenReturn(accountResponse(UUID.randomUUID(), "1100"));

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.COMPLETED);
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.successRows()).isEqualTo(2);
        assertThat(response.errorRows()).isZero();
        assertThat(response.errorDetails()).isNull();
    }

    @Test
    void importFromExcel_missingRequiredField_recordsError() throws Exception {
        ParsedAccountRow missingName = new ParsedAccountRow(2, "1000", null, "ASSET", null, null, null, null, null);
        when(excelParserService.parse(file)).thenReturn(List.of(missingName));

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(response.errorRows()).isEqualTo(1);
        assertThat(response.successRows()).isZero();
        assertThat(errorCodes(response)).containsExactly("MISSING_REQUIRED_FIELD");
    }

    @Test
    void importFromExcel_invalidQualifier_recordsError() throws Exception {
        ParsedAccountRow badQualifier = new ParsedAccountRow(2, "1000", "Assets", "NOT_A_QUALIFIER", null, null, null, null, null);
        when(excelParserService.parse(file)).thenReturn(List.of(badQualifier));

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(errorCodes(response)).containsExactly("INVALID_QUALIFIER");
    }

    @Test
    void importFromExcel_parentCodeNotFound_recordsError() throws Exception {
        ParsedAccountRow orphan = new ParsedAccountRow(2, "1100", "Cash", "ASSET", "9999", null, null, null, null);
        when(excelParserService.parse(file)).thenReturn(List.of(orphan));
        when(dimensionValueRepository.findByFinanceDimensionIdAndCodeAndIsActiveTrue(dimension.getId(), "9999"))
                .thenReturn(Optional.empty());

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(errorCodes(response)).containsExactly("PARENT_NOT_FOUND");
    }

    @Test
    void importFromExcel_parentCodeResolvedFromEarlierRowInSameFile() throws Exception {
        ParsedAccountRow parentRow = new ParsedAccountRow(2, "1000", "Assets", "ASSET", null, null, true, null, null);
        ParsedAccountRow childRow = new ParsedAccountRow(3, "1100", "Cash", "ASSET", "1000", true, false, null, null);
        UUID parentId = UUID.randomUUID();
        when(excelParserService.parse(file)).thenReturn(List.of(parentRow, childRow));
        when(coaImportRowService.createAccountIsolated(any(), anyString()))
                .thenReturn(accountResponse(parentId, "1000"))
                .thenReturn(accountResponse(UUID.randomUUID(), "1100"));

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.COMPLETED);
        // parentCode "1000" resolved from the in-memory map, never hitting the repository
        org.mockito.Mockito.verifyNoInteractions(dimensionValueRepository);
    }

    @Test
    void importFromExcel_duplicateCode_recordsErrorWithoutAbortingImport() throws Exception {
        ParsedAccountRow ok = new ParsedAccountRow(2, "1000", "Assets", "ASSET", null, null, true, null, null);
        ParsedAccountRow dup = new ParsedAccountRow(3, "1000", "Assets Again", "ASSET", null, null, true, null, null);
        when(excelParserService.parse(file)).thenReturn(List.of(ok, dup));
        when(coaImportRowService.createAccountIsolated(any(), anyString()))
                .thenReturn(accountResponse(UUID.randomUUID(), "1000"))
                .thenThrow(new DuplicateResourceException("DUPLICATE_DIMENSION_VALUE_CODE",
                        "A Dimension Value with code '1000' already exists for this Finance Dimension.", "code"));

        CoaImportJobResponse response = service.importFromExcel(file, ledgerId, null);

        assertThat(response.status()).isEqualTo(ImportJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(response.successRows()).isEqualTo(1);
        assertThat(response.errorRows()).isEqualTo(1);
        assertThat(errorCodes(response)).containsExactly("DUPLICATE_DIMENSION_VALUE_CODE");
    }

    @SuppressWarnings("unchecked")
    private List<String> errorCodes(CoaImportJobResponse response) {
        List<java.util.Map<String, Object>> errors =
                (List<java.util.Map<String, Object>>) response.errorDetails().get("errors");
        return errors.stream().map(e -> (String) e.get("errorCode")).toList();
    }

    private AccountResponse accountResponse(UUID id, String code) {
        return new AccountResponse(id, code, "Name", null, null, null, null, AccountQualifier.ASSET,
                false, true, null, false, false, null, null, null, null, null, null,
                null, null, false, null, 0, true, List.of(), null);
    }
}
