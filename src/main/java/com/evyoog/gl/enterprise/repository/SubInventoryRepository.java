package com.evyoog.gl.enterprise.repository;

import com.evyoog.gl.enterprise.domain.SubInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubInventoryRepository extends JpaRepository<SubInventory, UUID> {

    List<SubInventory> findByInventoryOrganisationId(UUID inventoryOrganisationId);

    boolean existsByInventoryOrganisationIdAndCode(UUID inventoryOrganisationId, String code);
}
