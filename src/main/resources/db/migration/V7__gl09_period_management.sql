-- ============================================================================
-- GL-09 Period Management
-- Table: accounting_period
-- The most-referenced table in the GL module — every journal header, account
-- balance, period status row, and subledger transaction references
-- accounting_period_id. This is the calendar cascade to all subledgers.
-- ============================================================================

CREATE TABLE gl.accounting_period (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    accounting_calendar_id  UUID            NOT NULL REFERENCES gl.accounting_calendar(id),
    name                    VARCHAR(20)     NOT NULL,
    period_number           INTEGER         NOT NULL,
    fiscal_year             VARCHAR(10)     NOT NULL,
    period_type             VARCHAR(20)     NOT NULL DEFAULT 'REGULAR'
                            CHECK (period_type IN ('REGULAR', 'ADJUSTMENT', 'YEAR_END')),
    quarter_number          INTEGER         NOT NULL
                            CHECK (quarter_number BETWEEN 1 AND 4),
    start_date              DATE            NOT NULL,
    end_date                DATE            NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    UNIQUE (accounting_calendar_id, name),
    UNIQUE (accounting_calendar_id, period_number, fiscal_year),
    CHECK (end_date >= start_date)
);

CREATE INDEX idx_accounting_period_calendar     ON gl.accounting_period (accounting_calendar_id);
CREATE INDEX idx_accounting_period_fiscal_year  ON gl.accounting_period (fiscal_year);
CREATE INDEX idx_accounting_period_dates        ON gl.accounting_period (start_date, end_date);
CREATE INDEX idx_accounting_period_name         ON gl.accounting_period (name);
