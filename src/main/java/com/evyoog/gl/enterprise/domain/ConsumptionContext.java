package com.evyoog.gl.enterprise.domain;

import com.evyoog.gl.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "consumption_context", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ConsumptionContext extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false, length = 20)
    private SegmentType segmentType;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provisioning_answers", columnDefinition = "jsonb")
    private Map<String, Object> provisioningAnswers;
}
