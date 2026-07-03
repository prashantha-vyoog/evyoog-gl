package com.evyoog.gl.periodstatus.repository;

import com.evyoog.gl.periodstatus.domain.PeriodStatus;
import com.evyoog.gl.periodstatus.domain.PeriodStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeriodStatusRepository extends JpaRepository<PeriodStatus, UUID> {

    Optional<PeriodStatus> findByLegalEntityIdAndAccountingPeriodId(UUID legalEntityId, UUID accountingPeriodId);

    boolean existsByLegalEntityIdAndAccountingPeriodId(UUID legalEntityId, UUID accountingPeriodId);

    List<PeriodStatus> findByLegalEntityId(UUID legalEntityId);

    List<PeriodStatus> findByLegalEntityIdAndStatus(UUID legalEntityId, PeriodStatusEnum status);
}
