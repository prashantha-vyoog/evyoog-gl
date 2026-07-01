package com.evyoog.gl.enterprise.repository;

import com.evyoog.gl.enterprise.domain.ConsumptionContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsumptionContextRepository extends JpaRepository<ConsumptionContext, UUID> {

    Optional<ConsumptionContext> findByCode(String code);

    boolean existsByCode(String code);
}
