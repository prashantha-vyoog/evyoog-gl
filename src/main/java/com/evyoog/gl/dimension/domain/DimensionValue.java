package com.evyoog.gl.dimension.domain;

import com.evyoog.gl.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "dimension_value", schema = "gl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class DimensionValue extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finance_dimension_id", nullable = false)
    private FinanceDimension financeDimension;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_value_id")
    private DimensionValue parentValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_qualifier", length = 20)
    private AccountQualifier accountQualifier;

    @Column(name = "is_summary", nullable = false)
    private boolean isSummary;

    @Column(name = "is_postable", nullable = false)
    private boolean isPostable;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", length = 10)
    private NormalBalance normalBalance;

    @Column(name = "gst_applicable", nullable = false)
    private boolean gstApplicable;

    @Column(name = "tds_applicable", nullable = false)
    private boolean tdsApplicable;

    @Column(name = "tds_section", length = 10)
    private String tdsSection;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
