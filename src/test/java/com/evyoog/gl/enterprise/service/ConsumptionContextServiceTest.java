package com.evyoog.gl.enterprise.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.service.AuditService;
import com.evyoog.gl.common.exception.DuplicateResourceException;
import com.evyoog.gl.common.exception.ResourceNotFoundException;
import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import com.evyoog.gl.enterprise.domain.SegmentType;
import com.evyoog.gl.enterprise.dto.ConsumptionContextResponse;
import com.evyoog.gl.enterprise.dto.CreateConsumptionContextRequest;
import com.evyoog.gl.enterprise.mapper.ConsumptionContextMapper;
import com.evyoog.gl.enterprise.repository.ConsumptionContextRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumptionContextServiceTest {

    @Mock
    private ConsumptionContextRepository repository;
    @Mock
    private ConsumptionContextMapper mapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private ConsumptionContextService service;

    @Test
    void create_whenCodeIsUnique_shouldPersistAndAudit() {
        CreateConsumptionContextRequest request =
                new CreateConsumptionContextRequest(SegmentType.WORKSPACE, "CTX-001", "Coimbatore Works");
        ConsumptionContext entity = new ConsumptionContext();
        ConsumptionContext saved = ConsumptionContext.builder()
                .segmentType(SegmentType.WORKSPACE).code("CTX-001").name("Coimbatore Works").status("ACTIVE").build();
        saved.setId(UUID.randomUUID());
        ConsumptionContextResponse response = new ConsumptionContextResponse(
                saved.getId(), SegmentType.WORKSPACE, "CTX-001", "Coimbatore Works", "ACTIVE", true, Instant.now(), Instant.now());

        when(repository.existsByCode("CTX-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        ConsumptionContextResponse result = service.create(request, "prashanth");

        assertThat(result.code()).isEqualTo("CTX-001");
        verify(auditService).log(eq(AuditAction.CREATE), eq("consumption_context"), any(), any(), any(), eq("prashanth"));
    }

    @Test
    void create_whenCodeAlreadyExists_shouldThrowDuplicateResourceException() {
        CreateConsumptionContextRequest request =
                new CreateConsumptionContextRequest(SegmentType.SESSION, "CTX-001", "Trial Session");
        when(repository.existsByCode("CTX-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, "prashanth"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_CODE");
    }

    @Test
    void getById_whenNotFound_shouldThrowResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void list_shouldReturnMappedResponses() {
        ConsumptionContext entity = new ConsumptionContext();
        ConsumptionContextResponse response = new ConsumptionContextResponse(
                UUID.randomUUID(), SegmentType.WORKSPACE, "CTX-002", "Chennai Works", "ACTIVE", true, Instant.now(), Instant.now());
        when(repository.findAll()).thenReturn(List.of(entity));
        when(mapper.toResponse(entity)).thenReturn(response);

        List<ConsumptionContextResponse> result = service.list();

        assertThat(result).containsExactly(response);
    }
}
