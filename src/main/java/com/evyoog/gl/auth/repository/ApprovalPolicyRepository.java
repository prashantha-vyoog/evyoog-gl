package com.evyoog.gl.auth.repository;

import com.evyoog.gl.auth.domain.ApprovalPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, UUID> {

    @Query("""
            SELECT ap FROM ApprovalPolicy ap
            WHERE ap.legalEntity.id = :legalEntityId
              AND ((:businessUnitId IS NULL AND ap.businessUnit IS NULL) OR ap.businessUnit.id = :businessUnitId)
              AND ((:inventoryOrgId IS NULL AND ap.inventoryOrg IS NULL) OR ap.inventoryOrg.id = :inventoryOrgId)
              AND ap.journalSourceCode = :journalSourceCode
              AND ap.isActive = true
            """)
    Optional<ApprovalPolicy> findActivePolicy(
            @Param("legalEntityId") UUID legalEntityId,
            @Param("businessUnitId") UUID businessUnitId,
            @Param("inventoryOrgId") UUID inventoryOrgId,
            @Param("journalSourceCode") String journalSourceCode);

    List<ApprovalPolicy> findByLegalEntityId(UUID legalEntityId);
}
