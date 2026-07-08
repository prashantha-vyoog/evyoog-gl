package com.evyoog.gl.auth.repository;

import com.evyoog.gl.auth.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByUserIdAndLegalEntityId(UUID userId, UUID legalEntityId);

    List<UserRole> findByLegalEntityId(UUID legalEntityId);

    @Query("""
            SELECT ur.legalEntity.id FROM UserRole ur
            WHERE ur.user.id = :userId
            ORDER BY ur.assignedAt ASC
            """)
    List<UUID> findLegalEntityIdsByUserId(@Param("userId") UUID userId);

    default Optional<UUID> findFirstLegalEntityByUserId(UUID userId) {
        return findLegalEntityIdsByUserId(userId).stream().findFirst();
    }

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);
}
