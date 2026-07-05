package com.evyoog.gl.recurring.repository;

import com.evyoog.gl.recurring.domain.RecurringJournalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringJournalTemplateRepository extends JpaRepository<RecurringJournalTemplate, UUID> {

    List<RecurringJournalTemplate> findByLegalEntityIdAndIsActiveTrue(UUID legalEntityId);
}
