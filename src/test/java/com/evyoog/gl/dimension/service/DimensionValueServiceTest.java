package com.evyoog.gl.dimension.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.common.exception.ValidationException;
import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.DimensionType;
import com.evyoog.gl.dimension.domain.DimensionValue;
import com.evyoog.gl.dimension.domain.FinanceDimension;
import com.evyoog.gl.dimension.domain.NormalBalance;
import com.evyoog.gl.dimension.dto.CreateDimensionValueRequest;
import com.evyoog.gl.dimension.dto.DimensionValueResponse;
import com.evyoog.gl.dimension.mapper.DimensionValueMapper;
import com.evyoog.gl.dimension.repository.DimensionValueRepository;
import com.evyoog.gl.dimension.repository.FinanceDimensionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DimensionValueServiceTest {

    @Mock
    private DimensionValueRepository repository;
    @Mock
    private FinanceDimensionRepository financeDimensionRepository;
    @Mock
    private DimensionValueMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private DimensionValueService service;

    private FinanceDimension financeDimension(DimensionType type) {
        FinanceDimension fd = FinanceDimension.builder().code("FD").name("Dimension").dimensionType(type).build();
        fd.setId(UUID.randomUUID());
        return fd;
    }

    private DimensionValueResponse responseFor(DimensionValue entity) {
        return new DimensionValueResponse(entity.getId(), entity.getFinanceDimension().getId(), "FD", "Dimension",
                entity.getFinanceDimension().getDimensionType(), entity.getCode(), entity.getName(),
                entity.getDescription(), null, null, null, entity.getAccountQualifier(), entity.isSummary(),
                entity.isPostable(), entity.getNormalBalance(), entity.isGstApplicable(), entity.isTdsApplicable(),
                entity.getTdsSection(), entity.getDisplayOrder(), entity.isActive(), Instant.now(), Instant.now());
    }

    @Test
    void createValue_success() {
        FinanceDimension fd = financeDimension(DimensionType.COST_CENTRE);
        CreateDimensionValueRequest request = new CreateDimensionValueRequest(
                fd.getId(), "CC-001", "Cost Centre 1", null, null, null, null, null, null, null, null, null, null);
        DimensionValue entity = new DimensionValue();
        DimensionValue saved = DimensionValue.builder().code("CC-001").name("Cost Centre 1").financeDimension(fd).build();
        saved.setId(UUID.randomUUID());

        when(financeDimensionRepository.findById(fd.getId())).thenReturn(Optional.of(fd));
        when(repository.existsByFinanceDimensionIdAndCode(fd.getId(), "CC-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(responseFor(saved));

        DimensionValueResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("CC-001");
    }

    @Test
    void createValue_duplicateCode_shouldThrow409() {
        FinanceDimension fd = financeDimension(DimensionType.COST_CENTRE);
        CreateDimensionValueRequest request = new CreateDimensionValueRequest(
                fd.getId(), "CC-001", "Cost Centre 1", null, null, null, null, null, null, null, null, null, null);

        when(financeDimensionRepository.findById(fd.getId())).thenReturn(Optional.of(fd));
        when(repository.existsByFinanceDimensionIdAndCode(fd.getId(), "CC-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_DIMENSION_VALUE_CODE");
    }

    @Test
    void createValue_naturalAccountWithoutQualifier_shouldThrow400() {
        FinanceDimension fd = financeDimension(DimensionType.NATURAL_ACCOUNT);
        CreateDimensionValueRequest request = new CreateDimensionValueRequest(
                fd.getId(), "1000", "Cash", null, null, null, null, null, null, null, null, null, null);

        when(financeDimensionRepository.findById(fd.getId())).thenReturn(Optional.of(fd));
        when(repository.existsByFinanceDimensionIdAndCode(fd.getId(), "1000")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_QUALIFIER_REQUIRED");
    }

    @Test
    void createValue_parentChildQualifierMismatch_shouldThrow409() {
        FinanceDimension fd = financeDimension(DimensionType.NATURAL_ACCOUNT);
        DimensionValue parent = DimensionValue.builder().code("1000").name("Assets").financeDimension(fd)
                .accountQualifier(AccountQualifier.ASSET).build();
        parent.setId(UUID.randomUUID());

        CreateDimensionValueRequest request = new CreateDimensionValueRequest(
                fd.getId(), "2000", "Payables", null, parent.getId(), AccountQualifier.LIABILITY,
                null, null, null, null, null, null, null);

        when(financeDimensionRepository.findById(fd.getId())).thenReturn(Optional.of(fd));
        when(repository.existsByFinanceDimensionIdAndCode(fd.getId(), "2000")).thenReturn(false);
        when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "QUALIFIER_MISMATCH");
    }

    @Test
    void autoDeriveNormalBalance_asset_returnsDR() {
        FinanceDimension fd = financeDimension(DimensionType.NATURAL_ACCOUNT);
        CreateDimensionValueRequest request = new CreateDimensionValueRequest(
                fd.getId(), "1000", "Cash", null, null, AccountQualifier.ASSET, null, null, null, null, null, null, null);
        DimensionValue entity = new DimensionValue();

        when(financeDimensionRepository.findById(fd.getId())).thenReturn(Optional.of(fd));
        when(repository.existsByFinanceDimensionIdAndCode(fd.getId(), "1000")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(any(DimensionValue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DimensionValue.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        ArgumentCaptor<DimensionValue> captor = ArgumentCaptor.forClass(DimensionValue.class);
        service.create(request, "prashanth");

        org.mockito.Mockito.verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNormalBalance()).isEqualTo(NormalBalance.DR);
    }

    @Test
    void autoDeriveNormalBalance_liability_returnsCR() {
        FinanceDimension fd = financeDimension(DimensionType.NATURAL_ACCOUNT);
        CreateDimensionValueRequest request = new CreateDimensionValueRequest(
                fd.getId(), "2000", "Payables", null, null, AccountQualifier.LIABILITY,
                null, null, null, null, null, null, null);
        DimensionValue entity = new DimensionValue();

        when(financeDimensionRepository.findById(fd.getId())).thenReturn(Optional.of(fd));
        when(repository.existsByFinanceDimensionIdAndCode(fd.getId(), "2000")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(any(DimensionValue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DimensionValue.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        ArgumentCaptor<DimensionValue> captor = ArgumentCaptor.forClass(DimensionValue.class);
        service.create(request, "prashanth");

        org.mockito.Mockito.verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNormalBalance()).isEqualTo(NormalBalance.CR);
    }

    @Test
    void deactivateValue_success() {
        FinanceDimension fd = financeDimension(DimensionType.COST_CENTRE);
        UUID id = UUID.randomUUID();
        DimensionValue entity = DimensionValue.builder().code("CC-001").name("Cost Centre 1").financeDimension(fd).build();
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(any(DimensionValue.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        service.deactivate(id, "prashanth");

        assertThat(entity.isActive()).isFalse();
    }

    @Test
    void getById_whenMissing_shouldThrowResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
