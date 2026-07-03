-- ============================================================================
-- GL-10 Period Status Control
-- Table: period_status
-- Each Legal Entity independently opens/closes its own accounting periods,
-- even when multiple LEs share the same Ledger. This is the GATE for journal
-- posting — GL-15's Posting Engine calls PeriodStatusService.validatePeriodOpen()
-- before every journal post.
-- ============================================================================

CREATE TABLE gl.period_status (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID            NOT NULL REFERENCES gl.legal_entity(id),
    accounting_period_id    UUID            NOT NULL REFERENCES gl.accounting_period(id),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'NOT_OPENED'
                            CHECK (status IN (
                                'NOT_OPENED',
                                'FUTURE_ENTERABLE',
                                'OPEN',
                                'CLOSED',
                                'LOCKED'
                            )),
    opened_at               TIMESTAMPTZ,
    opened_by               VARCHAR(100),
    closed_at               TIMESTAMPTZ,
    closed_by               VARCHAR(100),
    locked_at               TIMESTAMPTZ,
    locked_by               VARCHAR(100),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),

    -- Each LE has exactly one status row per period
    UNIQUE (legal_entity_id, accounting_period_id)
);

CREATE INDEX idx_period_status_le
    ON gl.period_status (legal_entity_id);
CREATE INDEX idx_period_status_period
    ON gl.period_status (accounting_period_id);
CREATE INDEX idx_period_status_le_period
    ON gl.period_status (legal_entity_id, accounting_period_id);
CREATE INDEX idx_period_status_status
    ON gl.period_status (status);
