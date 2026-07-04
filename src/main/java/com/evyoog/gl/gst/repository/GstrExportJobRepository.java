package com.evyoog.gl.gst.repository;

import com.evyoog.gl.gst.domain.GstrExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GstrExportJobRepository extends JpaRepository<GstrExportJob, UUID> {
}
