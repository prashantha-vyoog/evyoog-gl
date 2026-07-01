package com.evyoog.gl.enterprise.repository;

import com.evyoog.gl.enterprise.domain.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {

    List<LegalEntity> findByBusinessGroupId(UUID businessGroupId);

    long countByBusinessGroupId(UUID businessGroupId);

    boolean existsByBusinessGroupIdAndCode(UUID businessGroupId, String code);
}
