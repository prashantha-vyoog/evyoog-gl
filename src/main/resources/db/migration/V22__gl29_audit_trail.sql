-- V22__gl29_audit_trail.sql
-- GL-29 Audit Trail API — adds query indexes to the existing gl.audit_log table.
-- gl.audit_log already exists (V1 baseline) with idx_audit_log_entity
-- (entity_name, entity_id) and idx_audit_log_performed_at (performed_at).
-- This migration adds the remaining indexes needed for the new filter/query
-- endpoints: by action, by user, and combined entity+recency lookups.

CREATE INDEX IF NOT EXISTS idx_audit_log_action
    ON gl.audit_log (action);

CREATE INDEX IF NOT EXISTS idx_audit_log_performed_by
    ON gl.audit_log (performed_by);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity_performed_at
    ON gl.audit_log (entity_name, performed_at DESC);
