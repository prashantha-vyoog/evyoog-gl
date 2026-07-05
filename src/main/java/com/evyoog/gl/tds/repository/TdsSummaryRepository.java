package com.evyoog.gl.tds.repository;

import com.evyoog.gl.tds.domain.TdsSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TdsSummaryRepository extends JpaRepository<TdsSummary, UUID> {
}
