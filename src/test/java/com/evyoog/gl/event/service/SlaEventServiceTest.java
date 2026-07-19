package com.evyoog.gl.event.service;

import com.evyoog.gl.aie.domain.SlaEventLog;
import com.evyoog.gl.aie.repository.SlaEventLogRepository;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.event.dto.SlaEventPageResponse;
import com.evyoog.gl.event.dto.SlaEventResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaEventServiceTest {

    @Mock
    private SlaEventLogRepository repository;
    @InjectMocks
    private SlaEventService service;

    @Test
    void search_noFiltersProvided_throws400() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, 0, 20))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_FILTER_REQUIRED");
    }

    @Test
    void search_byEventType_returnsFilteredResults() {
        SlaEventLog event = event("EMITTED");
        stubPage(List.of(event));

        SlaEventPageResponse response = service.search(null, null, null, "EMITTED", null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("EMITTED");
    }

    @Test
    void search_byLegalEntityId_returnsFilteredResults() {
        UUID legalEntityId = UUID.randomUUID();
        SlaEventLog event = event("EMITTED");
        event.setLegalEntityId(legalEntityId);
        stubPage(List.of(event));

        SlaEventPageResponse response = service.search(legalEntityId, null, null, null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).legalEntityId()).isEqualTo(legalEntityId);
    }

    @Test
    void search_byDateRange_returnsResults() {
        SlaEventLog event = event("EMITTED");
        stubPage(List.of(event));

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        SlaEventPageResponse response = service.search(null, null, null, null, from, to, 0, 20);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void search_paginationCorrect() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 45));

        SlaEventPageResponse response = service.search(null, null, null, "EMITTED", null, null, 1, 10);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.last()).isFalse();
    }

    @Test
    void getById_existingEvent_returnsEvent() {
        SlaEventLog event = event("EMITTED");
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        SlaEventResponse response = service.getById(event.getId());

        assertThat(response.id()).isEqualTo(event.getId());
        assertThat(response.status()).isEqualTo("EMITTED");
        assertThat(response.eventPayload()).isEqualTo(event.getEventPayload());
    }

    @Test
    void getById_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(EvyoogException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_NOT_FOUND");
    }

    @Test
    void getByLegalEntity_returnsEvents() {
        UUID legalEntityId = UUID.randomUUID();
        SlaEventLog event = event("EMITTED");
        event.setLegalEntityId(legalEntityId);
        when(repository.findByLegalEntityId(eq(legalEntityId), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(event), inv.getArgument(1), 1));

        SlaEventPageResponse response = service.getByLegalEntity(legalEntityId, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).legalEntityId()).isEqualTo(legalEntityId);
    }

    private void stubPage(List<SlaEventLog> events) {
        lenient().when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(events, inv.getArgument(1), events.size()));
    }

    private SlaEventLog event(String status) {
        return SlaEventLog.builder()
                .id(UUID.randomUUID())
                .ledgerId(UUID.randomUUID())
                .legalEntityId(UUID.randomUUID())
                .accountingPeriodId(UUID.randomUUID())
                .eventPayload(Map.of("orderId", "SO-1001"))
                .status(status)
                .createdAt(Instant.now())
                .build();
    }
}
