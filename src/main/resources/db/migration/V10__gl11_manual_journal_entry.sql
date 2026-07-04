-- ============================================================================
-- GL-11 Manual Journal Entry
-- No new tables — GL-15 created gl.journal_header, gl.journal_line,
-- gl.journal_source, gl.journal_category. This migration adds a single
-- convenience view for journal listing with legal entity / ledger / period
-- details joined in.
-- ============================================================================

CREATE OR REPLACE VIEW gl.v_journal_listing AS
SELECT
    jh.id,
    jh.journal_number,
    jh.description,
    jh.gl_date,
    jh.accounting_date,
    jh.status,
    jh.finance_mode_snapshot,
    jh.total_debit,
    jh.total_credit,
    jh.posted_at,
    jh.posted_by,
    jh.created_at,
    jh.created_by,
    le.id   AS legal_entity_id,
    le.code AS legal_entity_code,
    le.name AS legal_entity_name,
    l.id    AS ledger_id,
    l.code  AS ledger_code,
    l.name  AS ledger_name,
    l.finance_mode,
    ap.id   AS accounting_period_id,
    ap.name AS period_name,
    ap.fiscal_year,
    js.code AS journal_source_code,
    js.name AS journal_source_name,
    jc.code AS journal_category_code,
    jc.name AS journal_category_name
FROM gl.journal_header jh
JOIN gl.legal_entity       le ON le.id = jh.legal_entity_id
JOIN gl.ledger             l  ON l.id  = jh.ledger_id
JOIN gl.accounting_period  ap ON ap.id = jh.accounting_period_id
JOIN gl.journal_source     js ON js.id = jh.journal_source_id
JOIN gl.journal_category   jc ON jc.id = jh.journal_category_id;
