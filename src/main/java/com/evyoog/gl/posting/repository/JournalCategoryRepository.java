package com.evyoog.gl.posting.repository;

import com.evyoog.gl.posting.domain.JournalCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalCategoryRepository extends JpaRepository<JournalCategory, UUID> {

    Optional<JournalCategory> findByCode(String code);
}
