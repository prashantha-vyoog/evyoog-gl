package com.evyoog.gl.aie.repository;

import com.evyoog.gl.aie.domain.InterfaceBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterfaceBatchRepository extends JpaRepository<InterfaceBatch, UUID> {
}
