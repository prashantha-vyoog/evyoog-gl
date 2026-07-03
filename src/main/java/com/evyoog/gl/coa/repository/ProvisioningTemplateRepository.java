package com.evyoog.gl.coa.repository;

import com.evyoog.gl.coa.domain.ProvisioningTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProvisioningTemplateRepository extends JpaRepository<ProvisioningTemplate, UUID> {

    List<ProvisioningTemplate> findByIsActiveTrue();
}
