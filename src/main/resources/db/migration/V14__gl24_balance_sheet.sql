CREATE OR REPLACE VIEW gl.v_balance_sheet AS
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
    ab.beginning_balance,
    ab.period_to_date_dr,
    ab.period_to_date_cr,
    (ab.beginning_balance
        + ab.period_to_date_dr
        - ab.period_to_date_cr)     AS ending_balance
FROM gl.account_balance ab
JOIN gl.legal_entity        le ON le.id = ab.legal_entity_id
JOIN gl.accounting_period   ap ON ap.id = ab.accounting_period_id
JOIN gl.dimension_value     dv ON dv.id = ab.natural_account_value_id
WHERE dv.account_qualifier IN ('ASSET', 'LIABILITY', 'EQUITY')
  AND dv.is_active = TRUE;
