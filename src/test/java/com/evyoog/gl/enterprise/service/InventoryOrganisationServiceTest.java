package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.BusinessUnit;
import com.evyoog.gl.enterprise.domain.InventoryOrganisation;
import com.evyoog.gl.enterprise.dto.CreateInventoryOrganisationRequest;
import com.evyoog.gl.enterprise.dto.InventoryOrganisationResponse;
import com.evyoog.gl.enterprise.mapper.InventoryOrganisationMapper;
import com.evyoog.gl.enterprise.repository.BusinessUnitRepository;
import com.evyoog.gl.enterprise.repository.InventoryOrganisationRepository;
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
class InventoryOrganisationServiceTest {

    @Mock
    private InventoryOrganisationRepository repository;
    @Mock
    private BusinessUnitRepository businessUnitRepository;
    @Mock
    private InventoryOrganisationMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private InventoryOrganisationService service;

    @Test
    void create_whenBusinessUnitMissing_shouldThrowResourceNotFoundException() {
        UUID businessUnitId = UUID.randomUUID();
        CreateInventoryOrganisationRequest request = new CreateInventoryOrganisationRequest(businessUnitId, "IO-001", "Main Warehouse");
        when(businessUnitRepository.findById(businessUnitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_whenDuplicateCode_shouldThrowDuplicateResourceException() {
        UUID businessUnitId = UUID.randomUUID();
        BusinessUnit businessUnit = new BusinessUnit();
        businessUnit.setId(businessUnitId);
        CreateInventoryOrganisationRequest request = new CreateInventoryOrganisationRequest(businessUnitId, "IO-001", "Main Warehouse");

        when(businessUnitRepository.findById(businessUnitId)).thenReturn(Optional.of(businessUnit));
        when(repository.existsByBusinessUnitIdAndCode(businessUnitId, "IO-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_CODE");
    }

    @Test
    void create_whenValid_shouldPersist() {
        UUID businessUnitId = UUID.randomUUID();
        BusinessUnit businessUnit = new BusinessUnit();
        businessUnit.setId(businessUnitId);
        CreateInventoryOrganisationRequest request = new CreateInventoryOrganisationRequest(businessUnitId, "IO-001", "Main Warehouse");
        InventoryOrganisation entity = new InventoryOrganisation();
        InventoryOrganisation saved = InventoryOrganisation.builder().code("IO-001").name("Main Warehouse").build();
        saved.setId(UUID.randomUUID());
        InventoryOrganisationResponse response = new InventoryOrganisationResponse(
                saved.getId(), businessUnitId, "IO-001", "Main Warehouse", true, Instant.now(), Instant.now());

        when(businessUnitRepository.findById(businessUnitId)).thenReturn(Optional.of(businessUnit));
        when(repository.existsByBusinessUnitIdAndCode(businessUnitId, "IO-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        InventoryOrganisationResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("IO-001");
    }
}
