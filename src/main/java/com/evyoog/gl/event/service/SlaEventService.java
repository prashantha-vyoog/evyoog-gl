package com.evyoog.gl.event.service;

import com.evyoog.gl.aie.domain.SlaEventLog;
import com.evyoog.gl.aie.repository.SlaEventLogRepository;
import com.evyoog.gl.common.exception.EvyoogException;
import com.evyoog.gl.event.dto.SlaEventPageResponse;
import com.evyoog.gl.event.dto.SlaEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * GL-20 Event Emission — read-only query layer over aie.sla_event_log,
 * already written by PostingEngine.emitEvent() for EVENT_ONLY mode journals
 * (GL-15). No new write paths.
 */
@Service
@RequiredArgsConstructor
public class SlaEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SlaEventLogRepository repository;

    @Transactional(readOnly = true)
    public SlaEventPageResponse search(UUID legalEntityId, UUID ledgerId, UUID accountingPeriodId, String status,
                                        Instant from, Instant to, int page, int size) {
        if (legalEntityId == null && ledgerId == null && accountingPeriodId == null
                && status == null && from == null && to == null) {
            throw new EvyoogException("EVENT_FILTER_REQUIRED",
                    "At least one filter (legalEntityId, ledgerId, accountingPeriodId, status, from, to) is required.",
                    HttpStatus.BAD_REQUEST);
        }
        Specification<SlaEventLog> spec = SlaEventSpecifications.withFilters(
                legalEntityId, ledgerId, accountingPeriodId, status, from, to);
        return toPageResponse(repository.findAll(spec, buildPageable(page, size)));
    }

    @Transactional(readOnly = true)
    public SlaEventResponse getById(UUID id) {
        SlaEventLog event = repository.findById(id)
                .orElseThrow(() -> new EvyoogException("EVENT_NOT_FOUND",
                        "SLA event not found: " + id, HttpStatus.NOT_FOUND));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public SlaEventPageResponse getByLegalEntity(UUID legalEntityId, int page, int size) {
        return toPageResponse(repository.findByLegalEntityId(legalEntityId, buildPageable(page, size)));
    }

    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private SlaEventPageResponse toPageResponse(Page<SlaEventLog> page) {
        return SlaEventPageResponse.builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private SlaEventResponse toResponse(SlaEventLog event) {
        return SlaEventResponse.builder()
                .id(event.getId())
                .ledgerId(event.getLedgerId())
                .legalEntityId(event.getLegalEntityId())
                .accountingPeriodId(event.getAccountingPeriodId())
                .eventPayload(event.getEventPayload())
                .status(event.getStatus())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
