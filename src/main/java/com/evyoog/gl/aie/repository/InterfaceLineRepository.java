package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.InterfaceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterfaceLineRepository extends JpaRepository<InterfaceLine, UUID> {

    List<InterfaceLine> findByBatchIdOrderByLineNumberAsc(UUID batchId);
}
