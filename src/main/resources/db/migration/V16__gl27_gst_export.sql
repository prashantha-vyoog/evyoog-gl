-- V16__gl27_gst_export.sql
-- GL-27 GST Flagging + GSTR Export

-- GST transaction summary table — pre-computed for fast reporting
CREATE TABLE gl.gst_transaction_summary (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID        NOT NULL REFERENCES gl.legal_entity(id),
    business_unit_id        UUID        NOT NULL REFERENCES gl.business_unit(id),
    accounting_period_id    UUID        NOT NULL REFERENCES gl.accounting_period(id),
    journal_header_id       UUID        NOT NULL REFERENCES gl.journal_header(id),
    journal_line_id         UUID        NOT NULL REFERENCES gl.journal_line(id),
    gst_type                VARCHAR(10) NOT NULL
                            CHECK (gst_type IN ('CGST','SGST','IGST','UTGST')),
    transaction_type        VARCHAR(20) NOT NULL
                            CHECK (transaction_type IN ('OUTWARD','INWARD')),
    taxable_amount          NUMERIC(20,2) NOT NULL DEFAULT 0,
    cgst_amount             NUMERIC(20,2) NOT NULL DEFAULT 0,
    sgst_amount             NUMERIC(20,2) NOT NULL DEFAULT 0,
    igst_amount             NUMERIC(20,2) NOT NULL DEFAULT 0,
    utgst_amount            NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_tax_amount        NUMERIC(20,2) NOT NULL DEFAULT 0,
    gstin                   VARCHAR(15),
    place_of_supply         VARCHAR(2),
    is_reverse_charge       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (journal_line_id)
);

CREATE INDEX idx_gst_summary_le_period
    ON gl.gst_transaction_summary (legal_entity_id, accounting_period_id);
CREATE INDEX idx_gst_summary_gst_type
    ON gl.gst_transaction_summary (gst_type);
CREATE INDEX idx_gst_summary_transaction_type
    ON gl.gst_transaction_summary (transaction_type);

-- GSTR export job tracking
CREATE TABLE gl.gstr_export_job (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID        NOT NULL REFERENCES gl.legal_entity(id),
    accounting_period_id    UUID        NOT NULL REFERENCES gl.accounting_period(id),
    return_type             VARCHAR(10) NOT NULL
                            CHECK (return_type IN ('GSTR1','GSTR3B')),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PROCESSING',
                                              'COMPLETED','FAILED')),
    export_data             JSONB,
    generated_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100)
);

CREATE INDEX idx_gstr_export_le_period
    ON gl.gstr_export_job (legal_entity_id, accounting_period_id);

-- GST summary view for reporting
CREATE OR REPLACE VIEW gl.v_gst_summary AS
SELECT
    gts.legal_entity_id,
    le.code                         AS legal_entity_code,
    le.name                         AS legal_entity_name,
    gts.accounting_period_id,
    ap.name                         AS period_name,
    ap.fiscal_year,
    gts.gst_type,
    gts.transaction_type,
    COUNT(*)                        AS transaction_count,
    SUM(gts.taxable_amount)         AS total_taxable,
    SUM(gts.cgst_amount)            AS total_cgst,
    SUM(gts.sgst_amount)            AS total_sgst,
    SUM(gts.igst_amount)            AS total_igst,
    SUM(gts.utgst_amount)           AS total_utgst,
    SUM(gts.total_tax_amount)       AS total_tax
FROM gl.gst_transaction_summary gts
JOIN gl.legal_entity        le ON le.id = gts.legal_entity_id
JOIN gl.accounting_period   ap ON ap.id = gts.accounting_period_id
GROUP BY gts.legal_entity_id, le.code, le.name,
         gts.accounting_period_id, ap.name, ap.fiscal_year,
         gts.gst_type, gts.transaction_type;
