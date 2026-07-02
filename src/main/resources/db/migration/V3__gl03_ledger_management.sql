-- ============================================================================
-- GL-03 Ledger Management
-- Tables: ledger, legal_entity_ledger
-- The Ledger carries finance_mode, which drives all downstream posting
-- behaviour (Posting Engine, Period Close, Trial Balance). A Ledger is
-- independent of Legal Entity — one Ledger may be shared across many LEs.
-- ============================================================================

CREATE TABLE gl.ledger (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code                    VARCHAR(30)  NOT NULL UNIQUE,
    name                    VARCHAR(255) NOT NULL,
    description             VARCHAR(500),
    finance_mode            VARCHAR(20)  NOT NULL
                                 CHECK (finance_mode IN ('THICK', 'THIN', 'EVENT_ONLY')),
    ledger_category         VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY'
                                 CHECK (ledger_category IN ('PRIMARY', 'SECONDARY', 'REPORTING', 'ENCUMBRANCE')),
    functional_currency     VARCHAR(3)   NOT NULL DEFAULT 'INR',
    accounting_standard     VARCHAR(20)  NOT NULL DEFAULT 'IND_AS'
                                 CHECK (accounting_standard IN ('IND_AS', 'IGAAP', 'IFRS', 'US_GAAP')),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ledger_finance_mode ON gl.ledger (finance_mode);
CREATE INDEX idx_ledger_code ON gl.ledger (code);

-- ----------------------------------------------------------------------------
-- gl.legal_entity_ledger — assignment junction between Legal Entity and Ledger
-- ----------------------------------------------------------------------------
CREATE TABLE gl.legal_entity_ledger (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID         NOT NULL REFERENCES gl.legal_entity(id),
    ledger_id               UUID         NOT NULL REFERENCES gl.ledger(id),
    ledger_category         VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY'
                                 CHECK (ledger_category IN ('PRIMARY', 'SECONDARY', 'REPORTING', 'ENCUMBRANCE')),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL
);

-- Each Legal Entity may have exactly ONE active PRIMARY Ledger
CREATE UNIQUE INDEX idx_le_ledger_primary
    ON gl.legal_entity_ledger (legal_entity_id)
    WHERE ledger_category = 'PRIMARY' AND is_active = TRUE;

-- A Ledger can be shared across Legal Entities — no unique constraint on ledger_id alone
CREATE INDEX idx_legal_entity_ledger_le ON gl.legal_entity_ledger (legal_entity_id);
CREATE INDEX idx_legal_entity_ledger_l  ON gl.legal_entity_ledger (ledger_id);
