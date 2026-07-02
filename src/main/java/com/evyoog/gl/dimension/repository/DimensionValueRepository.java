package com.evyoog.gl.dimension.repository;

import com.evyoog.gl.dimension.domain.DimensionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DimensionValueRepository extends JpaRepository<DimensionValue, UUID> {

    boolean existsByFinanceDimensionIdAndCode(UUID financeDimensionId, String code);

    long countByFinanceDimensionIdAndIsActiveTrue(UUID financeDimensionId);

    List<DimensionValue> findByFinanceDimensionId(UUID financeDimensionId);

    List<DimensionValue> findByFinanceDimensionIdAndParentValueId(UUID financeDimensionId, UUID parentValueId);

    @Query("""
            select dv from DimensionValue dv
            where dv.financeDimension.ledger.id = :ledgerId
            and dv.code = :code
            """)
    List<DimensionValue> findByLedgerIdAndCode(@Param("ledgerId") UUID ledgerId, @Param("code") String code);
}
