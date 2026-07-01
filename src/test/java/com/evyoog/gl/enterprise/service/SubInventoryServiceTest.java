package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.InventoryOrganisation;
import com.evyoog.gl.enterprise.domain.SubInventory;
import com.evyoog.gl.enterprise.dto.CreateSubInventoryRequest;
import com.evyoog.gl.enterprise.dto.SubInventoryResponse;
import com.evyoog.gl.enterprise.mapper.SubInventoryMapper;
import com.evyoog.gl.enterprise.repository.InventoryOrganisationRepository;
import com.evyoog.gl.enterprise.repository.SubInventoryRepository;
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
class SubInventoryServiceTest {

    @Mock
    private SubInventoryRepository repository;
    @Mock
    private InventoryOrganisationRepository inventoryOrganisationRepository;
    @Mock
    private SubInventoryMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private SubInventoryService service;

    @Test
    void create_whenInventoryOrganisationMissing_shouldThrowResourceNotFoundException() {
        UUID inventoryOrganisationId = UUID.randomUUID();
        CreateSubInventoryRequest request = new CreateSubInventoryRequest(inventoryOrganisationId, "SI-001", "Raw Materials Bin");
        when(inventoryOrganisationRepository.findById(inventoryOrganisationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_whenDuplicateCode_shouldThrowDuplicateResourceException() {
        UUID inventoryOrganisationId = UUID.randomUUID();
        InventoryOrganisation inventoryOrganisation = new InventoryOrganisation();
        inventoryOrganisation.setId(inventoryOrganisationId);
        CreateSubInventoryRequest request = new CreateSubInventoryRequest(inventoryOrganisationId, "SI-001", "Raw Materials Bin");

        when(inventoryOrganisationRepository.findById(inventoryOrganisationId)).thenReturn(Optional.of(inventoryOrganisation));
        when(repository.existsByInventoryOrganisationIdAndCode(inventoryOrganisationId, "SI-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_CODE");
    }

    @Test
    void create_whenValid_shouldPersist() {
        UUID inventoryOrganisationId = UUID.randomUUID();
        InventoryOrganisation inventoryOrganisation = new InventoryOrganisation();
        inventoryOrganisation.setId(inventoryOrganisationId);
        CreateSubInventoryRequest request = new CreateSubInventoryRequest(inventoryOrganisationId, "SI-001", "Raw Materials Bin");
        SubInventory entity = new SubInventory();
        SubInventory saved = SubInventory.builder().code("SI-001").name("Raw Materials Bin").build();
        saved.setId(UUID.randomUUID());
        SubInventoryResponse response = new SubInventoryResponse(
                saved.getId(), inventoryOrganisationId, "SI-001", "Raw Materials Bin", true, Instant.now(), Instant.now());

        when(inventoryOrganisationRepository.findById(inventoryOrganisationId)).thenReturn(Optional.of(inventoryOrganisation));
        when(repository.existsByInventoryOrganisationIdAndCode(inventoryOrganisationId, "SI-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        SubInventoryResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("SI-001");
    }
}
