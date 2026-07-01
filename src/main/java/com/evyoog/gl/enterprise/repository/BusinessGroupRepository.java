package com.evyoog.gl.enterprise.repository;

import com.evyoog.gl.enterprise.domain.BusinessGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessGroupRepository extends JpaRepository<BusinessGroup, UUID> {

    List<BusinessGroup> findByConsumptionContextId(UUID consumptionContextId);

    boolean existsByConsumptionContextIdAndCode(UUID consumptionContextId, String code);
}
