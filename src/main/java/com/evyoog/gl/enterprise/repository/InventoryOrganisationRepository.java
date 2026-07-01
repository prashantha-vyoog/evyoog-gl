package com.evyoog.gl.enterprise.repository;

import com.evyoog.gl.enterprise.domain.InventoryOrganisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryOrganisationRepository extends JpaRepository<InventoryOrganisation, UUID> {

    List<InventoryOrganisation> findByBusinessUnitId(UUID businessUnitId);

    boolean existsByBusinessUnitIdAndCode(UUID businessUnitId, String code);
}
