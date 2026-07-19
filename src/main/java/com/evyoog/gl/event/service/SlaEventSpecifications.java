package com.evyoog.gl.event.service;

import com.evyoog.gl.aie.domain.SlaEventLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SlaEventSpecifications {

    private SlaEventSpecifications() {
    }

    static Specification<SlaEventLog> withFilters(UUID legalEntityId, UUID ledgerId, UUID accountingPeriodId,
                                                    String status, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (legalEntityId != null) {
                predicates.add(cb.equal(root.get("legalEntityId"), legalEntityId));
            }
            if (ledgerId != null) {
                predicates.add(cb.equal(root.get("ledgerId"), ledgerId));
            }
            if (accountingPeriodId != null) {
                predicates.add(cb.equal(root.get("accountingPeriodId"), accountingPeriodId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
