CREATE OR REPLACE VIEW gl.v_trial_balance AS
SELECT
    ab.ledger_id,
    ab.legal_entity_id,
    le.code                         AS legal_entity_code,
    le.name                         AS legal_entity_name,
    ab.accounting_period_id,
    ap.name                         AS period_name,
    ap.fiscal_year,
    ap.quarter_number,
    dv.code                         AS account_code,
    dv.name                         AS account_name,
    dv.account_qualifier,
    dv.normal_balance,
    dv.display_order,
    ab.beginning_balance,
    ab.period_to_date_dr            AS ptd_dr,
    ab.period_to_date_cr            AS ptd_cr,
    ab.year_to_date_dr              AS ytd_dr,
    ab.year_to_date_cr              AS ytd_cr,
    (ab.beginning_balance
        + ab.period_to_date_dr
        - ab.period_to_date_cr)     AS ending_balance,
    CASE
        WHEN dv.normal_balance = 'DR'
        THEN (ab.beginning_balance
                + ab.period_to_date_dr
                - ab.period_to_date_cr)
        ELSE 0
    END                             AS debit_balance,
    CASE
        WHEN dv.normal_balance = 'CR'
        THEN ABS(ab.beginning_balance
                + ab.period_to_date_dr
                - ab.period_to_date_cr)
        ELSE 0
    END                             AS credit_balance
FROM gl.account_balance ab
JOIN gl.legal_entity        le ON le.id = ab.legal_entity_id
JOIN gl.accounting_period   ap ON ap.id = ab.accounting_period_id
JOIN gl.dimension_value     dv ON dv.id = ab.natural_account_value_id
WHERE dv.is_postable = TRUE
  AND dv.is_active   = TRUE
  AND (ab.period_to_date_dr != 0
    OR ab.period_to_date_cr != 0
    OR ab.beginning_balance  != 0);
