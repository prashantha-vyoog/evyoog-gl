-- ============================================================================
-- GL-08 Accounting Calendar
-- Table: accounting_calendar
-- Every Ledger has exactly one Accounting Calendar, defining the fiscal year
-- structure that all periods (GL-09) and every subledger transaction cascade
-- from. India default is April-start (fiscal year 2025-26 = Apr 2025-Mar 2026).
-- ============================================================================

CREATE TABLE gl.accounting_calendar (
    id                       UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    ledger_id                UUID         NOT NULL UNIQUE REFERENCES gl.ledger(id),
    name                     VARCHAR(255) NOT NULL,
    description              VARCHAR(500),
    fiscal_year_start_month  INTEGER      NOT NULL DEFAULT 4
                                 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    fiscal_year_start_day    INTEGER      NOT NULL DEFAULT 1
                                 CHECK (fiscal_year_start_day BETWEEN 1 AND 31),
    period_type              VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY'
                                 CHECK (period_type IN ('MONTHLY', 'QUARTERLY', 'FISCAL_4_4_5')),
    periods_per_year         INTEGER      NOT NULL DEFAULT 12,
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(100) NOT NULL,
    updated_by               VARCHAR(100) NOT NULL
);

-- One calendar per Ledger enforced by the UNIQUE constraint on ledger_id
CREATE INDEX idx_accounting_calendar_ledger ON gl.accounting_calendar (ledger_id);
