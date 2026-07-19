-- V25__gl20_event_query_indexes.sql
-- GL-20 Event Emission API — aie.sla_event_log already exists from V9 baseline
-- (idx_sla_event_ledger, idx_sla_event_le). Adds indexes for the new query
-- filters (status, accounting_period_id, created_at) exposed by GET /api/v1/gl/events.

CREATE INDEX IF NOT EXISTS idx_sla_event_status
    ON aie.sla_event_log (status);

CREATE INDEX IF NOT EXISTS idx_sla_event_period
    ON aie.sla_event_log (accounting_period_id);

CREATE INDEX IF NOT EXISTS idx_sla_event_created_at
    ON aie.sla_event_log (created_at);
