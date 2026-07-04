CREATE OR REPLACE VIEW gl.v_profit_and_loss AS
SELECT
    ab.ledger_id,
    ab.legal_entity_id,
    le.code                         AS legal_entity_code,
    le.name                         AS legal_entity_name,
    ab.accounting_period_id,
    ap.name                         AS period_name,
    ap.fiscal_year,
    dv.id                           AS account_id,
    dv.code                         AS account_code,
    dv.name                         AS account_name,
    dv.account_qualifier,
    dv.parent_value_id,
    dv.is_summary,
    dv.is_postable,
    dv.display_order,
    ab.period_to_date_dr            AS ptd_dr,
    ab.period_to_date_cr            AS ptd_cr,
    ab.year_to_date_dr              AS ytd_dr,
    ab.year_to_date_cr              AS ytd_cr,
    (ab.period_to_date_cr - ab.period_to_date_dr) AS net_amount
FROM gl.account_balance ab
JOIN gl.legal_entity        le ON le.id = ab.legal_entity_id
JOIN gl.accounting_period   ap ON ap.id = ab.accounting_period_id
JOIN gl.dimension_value     dv ON dv.id = ab.natural_account_value_id
WHERE dv.account_qualifier IN ('REVENUE', 'EXPENSE')
  AND dv.is_active = TRUE;
