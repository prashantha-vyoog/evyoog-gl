package com.evyoog.gl.audit.service;

import com.evyoog.gl.common.audit.domain.AuditAction;
import com.evyoog.gl.common.audit.domain.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    static Specification<AuditLog> withFilters(String entityName, UUID entityId, String performedBy,
                                                AuditAction action, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityName != null) {
                predicates.add(cb.equal(root.get("entityName"), entityName));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (performedBy != null) {
                predicates.add(cb.equal(root.get("performedBy"), performedBy));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("performedAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
