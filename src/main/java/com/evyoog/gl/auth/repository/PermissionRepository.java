package com.evyoog.gl.auth.repository;

import com.evyoog.gl.auth.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
}
