-- V17__gl28_tds_recording.sql
-- GL-28 TDS/TCS Recording

CREATE TABLE gl.tds_summary (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID        NOT NULL REFERENCES gl.legal_entity(id),
    accounting_period_id    UUID        NOT NULL REFERENCES gl.accounting_period(id),
    tds_section             VARCHAR(10) NOT NULL,
    transaction_count       INTEGER     NOT NULL DEFAULT 0,
    total_payment_amount    NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_tds_amount        NUMERIC(20,2) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (legal_entity_id, accounting_period_id, tds_section)
);

CREATE INDEX idx_tds_summary_le_period
    ON gl.tds_summary (legal_entity_id, accounting_period_id);
CREATE INDEX idx_tds_summary_section
    ON gl.tds_summary (tds_section);

CREATE OR REPLACE VIEW gl.v_tds_summary AS
SELECT
    ts.legal_entity_id,
    le.code                         AS legal_entity_code,
    le.name                         AS legal_entity_name,
    ts.accounting_period_id,
    ap.name                         AS period_name,
    ap.fiscal_year,
    ts.tds_section,
    ts.transaction_count,
    ts.total_payment_amount,
    ts.total_tds_amount
FROM gl.tds_summary ts
JOIN gl.legal_entity        le ON le.id = ts.legal_entity_id
JOIN gl.accounting_period   ap ON ap.id = ts.accounting_period_id;
