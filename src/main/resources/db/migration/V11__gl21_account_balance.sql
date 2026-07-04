-- V11__gl21_account_balance.sql
-- GL-21 Account Balance Maintenance
-- No new tables — gl.account_balance exists from V9 (GL-15)

CREATE OR REPLACE VIEW gl.v_account_balance_summary AS
SELECT
    ab.id,
    ab.ledger_id,
    l.code                          AS ledger_code,
    l.name                          AS ledger_name,
    ab.legal_entity_id,
    le.code                         AS legal_entity_code,
    le.name                         AS legal_entity_name,
    ab.accounting_period_id,
    ap.name                         AS period_name,
    ap.fiscal_year,
    ap.quarter_number,
    ab.natural_account_value_id,
    dv.code                         AS account_code,
    dv.name                         AS account_name,
    dv.account_qualifier,
    dv.normal_balance,
    ab.account_combination,
    ab.beginning_balance,
    ab.period_to_date_dr,
    ab.period_to_date_cr,
    ab.year_to_date_dr,
    ab.year_to_date_cr,
    (ab.beginning_balance
        + ab.period_to_date_dr
        - ab.period_to_date_cr)     AS ending_balance,
    ab.updated_at
FROM gl.account_balance ab
JOIN gl.ledger              l  ON l.id  = ab.ledger_id
JOIN gl.legal_entity        le ON le.id = ab.legal_entity_id
JOIN gl.accounting_period   ap ON ap.id = ab.accounting_period_id
JOIN gl.dimension_value     dv ON dv.id = ab.natural_account_value_id;
