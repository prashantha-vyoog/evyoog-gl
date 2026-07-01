package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.BusinessGroup;
import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.enterprise.domain.EsMode;
import com.evyoog.gl.enterprise.dto.BusinessGroupResponse;
import com.evyoog.gl.enterprise.dto.CreateBusinessGroupRequest;
import com.evyoog.gl.enterprise.mapper.BusinessGroupMapper;
import com.evyoog.gl.enterprise.repository.BusinessGroupRepository;
import com.evyoog.gl.enterprise.repository.ConsumptionContextRepository;
import com.evyoog.gl.common.audit.service.AuditService;
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
class BusinessGroupServiceTest {

    @Mock
    private BusinessGroupRepository repository;
    @Mock
    private ConsumptionContextRepository consumptionContextRepository;
    @Mock
    private BusinessGroupMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private BusinessGroupService service;

    @Test
    void create_whenConsumptionContextMissing_shouldThrow404() {
        UUID contextId = UUID.randomUUID();
        CreateBusinessGroupRequest request = new CreateBusinessGroupRequest(contextId, "BG-001", "Coimbatore Group", EsMode.THICK_ES, "INR");
        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void create_whenCodeAlreadyExistsInContext_shouldThrowDuplicateResourceException() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = new ConsumptionContext();
        context.setId(contextId);
        CreateBusinessGroupRequest request = new CreateBusinessGroupRequest(contextId, "BG-001", "Coimbatore Group", EsMode.THICK_ES, "INR");

        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(repository.existsByConsumptionContextIdAndCode(contextId, "BG-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_CODE");
    }

    @Test
    void create_whenValid_shouldPersist() {
        UUID contextId = UUID.randomUUID();
        ConsumptionContext context = new ConsumptionContext();
        context.setId(contextId);
        CreateBusinessGroupRequest request = new CreateBusinessGroupRequest(contextId, "BG-002", "Chennai Group", EsMode.THIN_ES, "INR");
        BusinessGroup entity = new BusinessGroup();
        BusinessGroup saved = BusinessGroup.builder().code("BG-002").name("Chennai Group").esMode(EsMode.THIN_ES).defaultCurrency("INR").build();
        saved.setId(UUID.randomUUID());
        BusinessGroupResponse response = new BusinessGroupResponse(
                saved.getId(), contextId, "BG-002", "Chennai Group", EsMode.THIN_ES, "INR", true, Instant.now(), Instant.now());

        when(consumptionContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(repository.existsByConsumptionContextIdAndCode(contextId, "BG-002")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        BusinessGroupResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("BG-002");
        assertThat(result.esMode()).isEqualTo(EsMode.THIN_ES);
    }
}
