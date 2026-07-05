package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.InterfaceError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterfaceErrorRepository extends JpaRepository<InterfaceError, UUID> {

    List<InterfaceError> findByBatchId(UUID batchId);
}
