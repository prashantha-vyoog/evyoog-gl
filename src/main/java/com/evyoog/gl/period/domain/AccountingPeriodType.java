package com.evyoog.gl.period.domain;

public enum AccountingPeriodType {
    REGULAR,     // Normal monthly/quarterly period
    ADJUSTMENT,  // Adjustment period — extra entries at year end
    YEAR_END     // Year-end closing period
}
