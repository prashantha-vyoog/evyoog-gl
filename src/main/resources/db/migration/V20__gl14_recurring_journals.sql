-- ============================================================================
-- GL-14 Recurring Journals
-- recurring_journal_template did not exist in any prior migration — created
-- here in full. journal_source already has RECURRING (seeded in V9);
-- journal_category does not, so it is added here.
-- ============================================================================

-- ── gl.recurring_journal_template ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gl.recurring_journal_template (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID        NOT NULL REFERENCES gl.legal_entity(id),
    ledger_id               UUID        NOT NULL REFERENCES gl.ledger(id),
    name                    VARCHAR(255) NOT NULL,
    description             VARCHAR(500),
    frequency               VARCHAR(20) NOT NULL,
    day_of_month            INTEGER,
    start_period_id         UUID        REFERENCES gl.accounting_period(id),
    end_period_id           UUID        REFERENCES gl.accounting_period(id),
    journal_source_code     VARCHAR(30) NOT NULL,
    journal_category_code   VARCHAR(30) NOT NULL,
    reference               VARCHAR(100),
    lines                   JSONB       NOT NULL,
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recurring_template_legal_entity
    ON gl.recurring_journal_template (legal_entity_id);

-- ── gl.recurring_journal_run ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gl.recurring_journal_run (
    id                          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_id                 UUID        NOT NULL
                                            REFERENCES gl.recurring_journal_template(id),
    journal_header_id           UUID        NOT NULL
                                            REFERENCES gl.journal_header(id),
    accounting_period_id        UUID        NOT NULL
                                            REFERENCES gl.accounting_period(id),
    generated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    generated_by                VARCHAR(100) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recurring_run_template
    ON gl.recurring_journal_run (template_id);
CREATE INDEX IF NOT EXISTS idx_recurring_run_period
    ON gl.recurring_journal_run (accounting_period_id);

-- Seed RECURRING journal category — journal_source already has it from V9.
INSERT INTO gl.journal_category (code, name, description)
VALUES ('RECURRING', 'Recurring', 'Recurring journal entry')
ON CONFLICT (code) DO NOTHING;
