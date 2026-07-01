package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.enterprise.domain.BusinessGroup;
import com.evyoog.gl.enterprise.domain.EsMode;
import com.evyoog.gl.enterprise.domain.LegalEntity;
import com.evyoog.gl.enterprise.dto.CreateLegalEntityRequest;
import com.evyoog.gl.enterprise.dto.LegalEntityResponse;
import com.evyoog.gl.enterprise.mapper.LegalEntityMapper;
import com.evyoog.gl.enterprise.repository.BusinessGroupRepository;
import com.evyoog.gl.enterprise.repository.LegalEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalEntityServiceTest {

    @Mock
    private LegalEntityRepository repository;
    @Mock
    private BusinessGroupRepository businessGroupRepository;
    @Mock
    private LegalEntityMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private LegalEntityService service;

    @Test
    void create_whenThinEsAlreadyHasOne_shouldThrow409() {
        UUID businessGroupId = UUID.randomUUID();
        BusinessGroup bg = BusinessGroup.builder().esMode(EsMode.THIN_ES).build();
        bg.setId(businessGroupId);
        CreateLegalEntityRequest request = new CreateLegalEntityRequest(businessGroupId, "LE-002", "Second LE", null, null);

        when(businessGroupRepository.findById(businessGroupId)).thenReturn(Optional.of(bg));
        when(repository.countByBusinessGroupId(businessGroupId)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "THIN_ES_LE_LIMIT");
    }

    @Test
    void create_whenThinEsHasNoLegalEntityYet_shouldSucceed() {
        UUID businessGroupId = UUID.randomUUID();
        BusinessGroup bg = BusinessGroup.builder().esMode(EsMode.THIN_ES).build();
        bg.setId(businessGroupId);
        CreateLegalEntityRequest request = new CreateLegalEntityRequest(businessGroupId, "LE-001", "First LE", null, null);
        LegalEntity entity = new LegalEntity();
        LegalEntity saved = LegalEntity.builder().code("LE-001").name("First LE").accountingStandard(AccountingStandard.IND_AS).build();
        saved.setId(UUID.randomUUID());
        LegalEntityResponse response = new LegalEntityResponse(
                saved.getId(), businessGroupId, "LE-001", "First LE", AccountingStandard.IND_AS, null, true, Instant.now(), Instant.now());

        when(businessGroupRepository.findById(businessGroupId)).thenReturn(Optional.of(bg));
        when(repository.countByBusinessGroupId(businessGroupId)).thenReturn(0L);
        when(repository.existsByBusinessGroupIdAndCode(businessGroupId, "LE-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        LegalEntityResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("LE-001");
        assertThat(result.accountingStandard()).isEqualTo(AccountingStandard.IND_AS);
    }

    @Test
    void create_whenThickEs_shouldAllowMultipleLegalEntities() {
        UUID businessGroupId = UUID.randomUUID();
        BusinessGroup bg = BusinessGroup.builder().esMode(EsMode.THICK_ES).build();
        bg.setId(businessGroupId);
        CreateLegalEntityRequest request = new CreateLegalEntityRequest(businessGroupId, "LE-003", "Third LE", null, null);
        LegalEntity entity = new LegalEntity();
        LegalEntity saved = LegalEntity.builder().code("LE-003").name("Third LE").accountingStandard(AccountingStandard.IND_AS).build();
        saved.setId(UUID.randomUUID());
        LegalEntityResponse response = new LegalEntityResponse(
                saved.getId(), businessGroupId, "LE-003", "Third LE", AccountingStandard.IND_AS, null, true, Instant.now(), Instant.now());

        when(businessGroupRepository.findById(businessGroupId)).thenReturn(Optional.of(bg));
        when(repository.existsByBusinessGroupIdAndCode(businessGroupId, "LE-003")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        LegalEntityResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("LE-003");
    }

    @Test
    void create_whenDuplicateCode_shouldThrowDuplicateResourceException() {
        UUID businessGroupId = UUID.randomUUID();
        BusinessGroup bg = BusinessGroup.builder().esMode(EsMode.THICK_ES).build();
        bg.setId(businessGroupId);
        CreateLegalEntityRequest request = new CreateLegalEntityRequest(businessGroupId, "LE-001", "Duplicate LE", null, null);

        when(businessGroupRepository.findById(businessGroupId)).thenReturn(Optional.of(bg));
        when(repository.existsByBusinessGroupIdAndCode(businessGroupId, "LE-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_CODE");
    }

    @Test
    void create_whenBusinessGroupMissing_shouldThrowResourceNotFoundException() {
        UUID businessGroupId = UUID.randomUUID();
        CreateLegalEntityRequest request = new CreateLegalEntityRequest(businessGroupId, "LE-001", "Orphan LE", null, null);
        when(businessGroupRepository.findById(businessGroupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
