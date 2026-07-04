-- V15__gl26_account_ledger.sql

-- Account Ledger view — journal lines for a specific account
CREATE OR REPLACE VIEW gl.v_account_ledger AS
SELECT
    jl.id                           AS line_id,
    jh.id                           AS journal_header_id,
    jh.journal_number,
    jh.gl_date,
    jh.accounting_date,
    jh.description                  AS journal_description,
    jl.description                  AS line_description,
    jh.status,
    jh.finance_mode_snapshot,
    jl.account_combination,
    jl.natural_account_value_id,
    dv.code                         AS account_code,
    dv.name                         AS account_name,
    dv.account_qualifier,
    dv.normal_balance,
    jl.debit_amount,
    jl.credit_amount,
    jl.gst_applicable,
    jl.gst_type,
    jl.tds_applicable,
    jl.tds_section,
    jh.legal_entity_id,
    le.code                         AS legal_entity_code,
    le.name                         AS legal_entity_name,
    jh.accounting_period_id,
    ap.name                         AS period_name,
    ap.fiscal_year,
    js.code                         AS journal_source_code,
    jc.code                         AS journal_category_code,
    jl.line_number,
    jh.created_at
FROM gl.journal_line   jl
JOIN gl.journal_header jh ON jh.id = jl.journal_header_id
JOIN gl.dimension_value dv ON dv.id = jl.natural_account_value_id
JOIN gl.legal_entity   le ON le.id = jh.legal_entity_id
JOIN gl.accounting_period ap ON ap.id = jh.accounting_period_id
JOIN gl.journal_source js ON js.id = jh.journal_source_id
JOIN gl.journal_category jc ON jc.id = jh.journal_category_id
WHERE jh.status = 'POSTED';

-- Note: gl.v_journal_listing already exists (created in V10__gl11_manual_journal_entry.sql)
-- and already covers the Journal Listing report's needs — not recreated here.
