package com.evyoog.gl.posting.repository;

import com.evyoog.gl.posting.domain.JournalSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalSourceRepository extends JpaRepository<JournalSource, UUID> {

    Optional<JournalSource> findByCode(String code);

    List<JournalSource> findByIsActiveTrueOrderByNameAsc();
}
