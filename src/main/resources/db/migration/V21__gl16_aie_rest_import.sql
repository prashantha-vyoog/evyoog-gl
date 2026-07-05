-- ============================================================================
-- GL-16 AIE REST Journal Import
--
-- None of the AIE staging tables used by this capability existed before this
-- migration — the only aie.* table in the DB so far is aie.sla_event_log
-- (created in V9 for the EVENT_ONLY Posting Engine branch, columns
-- ledger_id/legal_entity_id/accounting_period_id/event_payload/status). That
-- table is owned by PostingEngine.emitEvent() and is NOT reused here — GL-16
-- writes its own acknowledgement to aie.batch_ack_log instead, to avoid
-- overloading a table with an unrelated shape and an unrelated caller.
--
-- gl.journal_source already has the 'IMPORT' code (seeded in V9).
-- gl.journal_category does not — seeded here, same pattern as V20's RECURRING.
-- ============================================================================

-- ── aie.interface_batch ──────────────────────────────────────────────────────
CREATE TABLE aie.interface_batch (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id                VARCHAR(255)    NOT NULL,
    batch_reference         VARCHAR(255),
    source_system           VARCHAR(100)    NOT NULL,
    import_transport        VARCHAR(20)     NOT NULL DEFAULT 'REST',
    legal_entity_id         UUID            NOT NULL REFERENCES gl.legal_entity(id),
    ledger_id               UUID            REFERENCES gl.ledger(id),
    accounting_period_id    UUID            REFERENCES gl.accounting_period(id),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN (
                                'PENDING','VALIDATING','VALIDATED','ENRICHED',
                                'POSTING','POSTED','FAILED','PARTIAL'
                            )),
    total_lines             INTEGER         NOT NULL DEFAULT 0,
    valid_lines             INTEGER         NOT NULL DEFAULT 0,
    error_lines             INTEGER         NOT NULL DEFAULT 0,
    error_summary           VARCHAR(500),
    journal_header_id       UUID            REFERENCES gl.journal_header(id),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100)
);

CREATE INDEX idx_interface_batch_status ON aie.interface_batch (status);
CREATE INDEX idx_interface_batch_le ON aie.interface_batch (legal_entity_id);

-- ── aie.interface_line ───────────────────────────────────────────────────────
CREATE TABLE aie.interface_line (
    id                          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id                    UUID            NOT NULL REFERENCES aie.interface_batch(id),
    line_number                 INTEGER         NOT NULL,
    account_code                VARCHAR(30),
    natural_account_value_id    UUID            REFERENCES gl.dimension_value(id),
    account_combination         JSONB,
    debit_amount                NUMERIC(20,2),
    credit_amount               NUMERIC(20,2),
    description                 VARCHAR(255),
    gst_type                    VARCHAR(10),
    gst_applicable              BOOLEAN         NOT NULL DEFAULT FALSE,
    tds_section                 VARCHAR(10),
    tds_applicable              BOOLEAN         NOT NULL DEFAULT FALSE,
    line_status                 VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    error_code                  VARCHAR(50),
    error_message               VARCHAR(500),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interface_line_batch ON aie.interface_line (batch_id);

-- ── aie.interface_error ──────────────────────────────────────────────────────
CREATE TABLE aie.interface_error (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id        UUID            NOT NULL REFERENCES aie.interface_batch(id),
    line_number     INTEGER,
    error_code      VARCHAR(50)     NOT NULL,
    error_message   VARCHAR(500)    NOT NULL,
    error_stage     VARCHAR(20)     NOT NULL,
    field_name      VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interface_error_batch ON aie.interface_error (batch_id);

-- ── aie.deduplication_log ────────────────────────────────────────────────────
CREATE TABLE aie.deduplication_log (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id        VARCHAR(255)    NOT NULL UNIQUE,
    batch_id        UUID,
    source_system   VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dedup_log_event_id ON aie.deduplication_log (event_id);

-- ── aie.batch_ack_log ─────────────────────────────────────────────────────────
-- GL-16's own POST-stage acknowledgement — distinct from aie.sla_event_log
-- (that table belongs to the EVENT_ONLY finance mode branch of PostingEngine).
CREATE TABLE aie.batch_ack_log (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id            UUID            NOT NULL REFERENCES aie.interface_batch(id),
    journal_header_id   UUID            REFERENCES gl.journal_header(id),
    event_type          VARCHAR(30)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_batch_ack_log_batch ON aie.batch_ack_log (batch_id);

-- Seed IMPORT journal category — journal_source already has it from V9.
INSERT INTO gl.journal_category (code, name, description)
VALUES ('IMPORT', 'Import', 'Imported journal entry')
ON CONFLICT (code) DO NOTHING;
