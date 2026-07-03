-- ============================================================================
-- GL-15 Posting Engine
-- Creates the journal + account balance tables that every posted transaction
-- flows through: gl.journal_source, gl.journal_category, gl.journal_header,
-- gl.journal_line, gl.account_balance, plus aie.sla_event_log for the
-- EVENT_ONLY finance mode branch.
-- ============================================================================

-- ── gl.journal_source ───────────────────────────────────────────────────────
CREATE TABLE gl.journal_source (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    code        VARCHAR(30) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO gl.journal_source (code, name, requires_approval) VALUES
('MANUAL',     'Manual Journal Entry',    FALSE),
('SUBLEDGER',  'Subledger Import',        FALSE),
('EXTERNAL',   'External System Import',  FALSE),
('IMPORT',     'Excel/CSV Import',        FALSE),
('RECURRING',  'Recurring Journal',       FALSE),
('REVERSAL',   'Journal Reversal',        FALSE);

-- ── gl.journal_category ─────────────────────────────────────────────────────
CREATE TABLE gl.journal_category (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    code        VARCHAR(30) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO gl.journal_category (code, name) VALUES
('PURCHASE',     'Purchase'),
('SALES',        'Sales'),
('PAYROLL',      'Payroll'),
('DEPRECIATION', 'Depreciation'),
('ACCRUAL',      'Accrual'),
('ADJUSTMENT',   'Adjustment'),
('OPENING',      'Opening Balance'),
('REVERSAL',     'Reversal'),
('INTERCOMPANY', 'Intercompany');

-- ── gl.journal_header ────────────────────────────────────────────────────────
CREATE TABLE gl.journal_header (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    journal_number          VARCHAR(30)     NOT NULL UNIQUE,
    legal_entity_id         UUID            NOT NULL REFERENCES gl.legal_entity(id),
    ledger_id               UUID            NOT NULL REFERENCES gl.ledger(id),
    accounting_period_id    UUID            NOT NULL REFERENCES gl.accounting_period(id),
    journal_source_id       UUID            NOT NULL REFERENCES gl.journal_source(id),
    journal_category_id     UUID            NOT NULL REFERENCES gl.journal_category(id),
    description             VARCHAR(500),
    gl_date                 DATE            NOT NULL,
    accounting_date         DATE            NOT NULL,
    currency_code           VARCHAR(3)         NOT NULL DEFAULT 'INR',
    exchange_rate           NUMERIC(20,10)  NOT NULL DEFAULT 1.0,
    total_debit             NUMERIC(20,2)   NOT NULL DEFAULT 0,
    total_credit            NUMERIC(20,2)   NOT NULL DEFAULT 0,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN (
                                'DRAFT','PENDING_APPROVAL','APPROVED',
                                'POSTED','REVERSED','CANCELLED','FAILED'
                            )),
    finance_mode_snapshot   VARCHAR(20)     NOT NULL
                            CHECK (finance_mode_snapshot IN ('THICK','THIN','EVENT_ONLY')),
    posted_at               TIMESTAMPTZ,
    posted_by               VARCHAR(100),
    reversal_of_id          UUID            REFERENCES gl.journal_header(id),
    is_reversal             BOOLEAN         NOT NULL DEFAULT FALSE,
    external_reference      VARCHAR(255),
    notes                   TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_jh_legal_entity   ON gl.journal_header (legal_entity_id);
CREATE INDEX idx_jh_ledger         ON gl.journal_header (ledger_id);
CREATE INDEX idx_jh_period         ON gl.journal_header (accounting_period_id);
CREATE INDEX idx_jh_status         ON gl.journal_header (status);
CREATE INDEX idx_jh_gl_date        ON gl.journal_header (gl_date);
CREATE INDEX idx_jh_journal_number ON gl.journal_header (journal_number);

-- ── gl.journal_line ──────────────────────────────────────────────────────────
CREATE TABLE gl.journal_line (
    id                          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    journal_header_id           UUID            NOT NULL REFERENCES gl.journal_header(id),
    line_number                 INTEGER         NOT NULL,
    account_combination         JSONB           NOT NULL,
    natural_account_value_id    UUID            NOT NULL REFERENCES gl.dimension_value(id),
    debit_amount                NUMERIC(20,2),
    credit_amount               NUMERIC(20,2),
    currency_code               VARCHAR(3)         NOT NULL DEFAULT 'INR',
    description                 VARCHAR(255),
    gst_applicable              BOOLEAN         NOT NULL DEFAULT FALSE,
    gst_type                    VARCHAR(10)
                                CHECK (gst_type IN ('CGST','SGST','IGST','UTGST')),
    tds_applicable              BOOLEAN         NOT NULL DEFAULT FALSE,
    tds_section                 VARCHAR(10),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_debit_or_credit
        CHECK (
            (debit_amount IS NOT NULL AND credit_amount IS NULL AND debit_amount > 0)
            OR
            (credit_amount IS NOT NULL AND debit_amount IS NULL AND credit_amount > 0)
        ),
    UNIQUE (journal_header_id, line_number)
);

CREATE INDEX idx_jl_header         ON gl.journal_line (journal_header_id);
CREATE INDEX idx_jl_account_combo  ON gl.journal_line USING GIN (account_combination);
CREATE INDEX idx_jl_natural_acct   ON gl.journal_line (natural_account_value_id);

-- ── gl.account_balance ───────────────────────────────────────────────────────
CREATE TABLE gl.account_balance (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    ledger_id               UUID            NOT NULL REFERENCES gl.ledger(id),
    legal_entity_id         UUID            NOT NULL REFERENCES gl.legal_entity(id),
    accounting_period_id    UUID            NOT NULL REFERENCES gl.accounting_period(id),
    natural_account_value_id UUID           NOT NULL REFERENCES gl.dimension_value(id),
    account_combination     JSONB           NOT NULL,
    beginning_balance       NUMERIC(20,2)   NOT NULL DEFAULT 0,
    period_to_date_dr       NUMERIC(20,2)   NOT NULL DEFAULT 0,
    period_to_date_cr       NUMERIC(20,2)   NOT NULL DEFAULT 0,
    year_to_date_dr         NUMERIC(20,2)   NOT NULL DEFAULT 0,
    year_to_date_cr         NUMERIC(20,2)   NOT NULL DEFAULT 0,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- PostgreSQL does not support UNIQUE constraints directly on JSONB columns —
-- use a functional unique index on the text representation instead.
CREATE UNIQUE INDEX idx_ab_unique_combination
    ON gl.account_balance (
        ledger_id,
        legal_entity_id,
        accounting_period_id,
        natural_account_value_id,
        (account_combination::text)
    );

CREATE INDEX idx_ab_ledger_period
    ON gl.account_balance (ledger_id, accounting_period_id);
CREATE INDEX idx_ab_natural_account
    ON gl.account_balance (natural_account_value_id);
CREATE INDEX idx_ab_legal_entity
    ON gl.account_balance (legal_entity_id);
CREATE INDEX idx_ab_account_combo
    ON gl.account_balance USING GIN (account_combination);

-- ── aie.sla_event_log ────────────────────────────────────────────────────────
-- Not present in the V1 baseline — created here for the EVENT_ONLY finance
-- mode branch of the Posting Engine.
CREATE TABLE aie.sla_event_log (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    ledger_id               UUID            NOT NULL REFERENCES gl.ledger(id),
    legal_entity_id         UUID            NOT NULL REFERENCES gl.legal_entity(id),
    accounting_period_id    UUID            REFERENCES gl.accounting_period(id),
    event_payload           JSONB,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'EMITTED',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sla_event_ledger ON aie.sla_event_log (ledger_id);
CREATE INDEX idx_sla_event_le     ON aie.sla_event_log (legal_entity_id);
