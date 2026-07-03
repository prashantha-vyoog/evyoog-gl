package com.evyoog.gl.coa.repository;

import com.evyoog.gl.coa.domain.CoaImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoaImportJobRepository extends JpaRepository<CoaImportJob, UUID> {

    List<CoaImportJob> findByLedgerId(UUID ledgerId);
}
