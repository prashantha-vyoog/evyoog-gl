package com.evyoog.gl.gst.repository;

import com.evyoog.gl.gst.domain.GstTransactionSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GstTransactionSummaryRepository extends JpaRepository<GstTransactionSummary, UUID> {

    List<GstTransactionSummary> findByLegalEntityIdAndAccountingPeriodId(
            UUID legalEntityId, UUID accountingPeriodId);

    List<GstTransactionSummary> findByLegalEntityIdAndAccountingPeriodIdAndGstType(
            UUID legalEntityId, UUID accountingPeriodId, String gstType);
}
