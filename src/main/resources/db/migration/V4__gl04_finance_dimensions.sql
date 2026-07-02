-- ============================================================================
-- GL-04 Finance Dimension Management
-- Tables: finance_dimension, dimension_value
-- Finance Dimensions are customer-defined analysis segments attached to a
-- Ledger, replacing the traditional fixed Chart of Account segment structure.
-- dimension_value also carries the Natural Account (Chart of Accounts)
-- hierarchy that GL-05 builds on top of.
-- ============================================================================

CREATE TABLE gl.finance_dimension (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    ledger_id           UUID         NOT NULL REFERENCES gl.ledger(id),
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(500),
    dimension_type      VARCHAR(30)  NOT NULL
                             CHECK (dimension_type IN (
                                 'LEGAL_ENTITY', 'NATURAL_ACCOUNT', 'COST_CENTRE',
                                 'PROFIT_CENTRE', 'INTERCOMPANY', 'PRODUCT', 'PROJECT', 'CUSTOM'
                             )),
    is_required         BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order       INTEGER      NOT NULL DEFAULT 0,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(100) NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    UNIQUE (ledger_id, code)
);

-- Exactly 1 active LEGAL_ENTITY dimension per Ledger
CREATE UNIQUE INDEX idx_fd_legal_entity_per_ledger
    ON gl.finance_dimension (ledger_id)
    WHERE dimension_type = 'LEGAL_ENTITY' AND is_active = TRUE;

-- Exactly 1 active NATURAL_ACCOUNT dimension per Ledger
CREATE UNIQUE INDEX idx_fd_natural_account_per_ledger
    ON gl.finance_dimension (ledger_id)
    WHERE dimension_type = 'NATURAL_ACCOUNT' AND is_active = TRUE;

CREATE INDEX idx_finance_dimension_ledger ON gl.finance_dimension (ledger_id);
CREATE INDEX idx_finance_dimension_type   ON gl.finance_dimension (dimension_type);

-- ----------------------------------------------------------------------------
-- gl.dimension_value — codified values for a Finance Dimension. Also home of
-- the Natural Account (Chart of Accounts) hierarchy for the NATURAL_ACCOUNT
-- dimension type.
-- ----------------------------------------------------------------------------
CREATE TABLE gl.dimension_value (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    finance_dimension_id    UUID         NOT NULL REFERENCES gl.finance_dimension(id),
    code                    VARCHAR(30)  NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    description             VARCHAR(500),

    -- Parent-child hierarchy (used by NATURAL_ACCOUNT for CoA hierarchy)
    parent_value_id         UUID         REFERENCES gl.dimension_value(id),

    -- NATURAL_ACCOUNT specific fields
    account_qualifier       VARCHAR(20)
                                 CHECK (account_qualifier IN (
                                     'ASSET', 'LIABILITY', 'EQUITY',
                                     'REVENUE', 'EXPENSE', 'BUDGETARY'
                                 )),
    is_summary              BOOLEAN      NOT NULL DEFAULT FALSE,
    is_postable              BOOLEAN     NOT NULL DEFAULT TRUE,
    normal_balance          VARCHAR(10)
                                 CHECK (normal_balance IN ('DR', 'CR')),

    -- India localisation flags
    gst_applicable          BOOLEAN      NOT NULL DEFAULT FALSE,
    tds_applicable          BOOLEAN      NOT NULL DEFAULT FALSE,
    tds_section              VARCHAR(10),

    -- Display and ordering
    display_order           INTEGER      NOT NULL DEFAULT 0,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL,
    UNIQUE (finance_dimension_id, code)
);

CREATE INDEX idx_dimension_value_fd      ON gl.dimension_value (finance_dimension_id);
CREATE INDEX idx_dimension_value_parent  ON gl.dimension_value (parent_value_id);
CREATE INDEX idx_dimension_value_type    ON gl.dimension_value (account_qualifier);
CREATE INDEX idx_dimension_value_code    ON gl.dimension_value (code);
