-- ============================================================================
-- GL-02 Setup Wizard
-- Adds the JSONB column that records the wizard's answers and provisioning
-- result on gl.consumption_context. A non-null value marks the wizard as
-- already run for that context (idempotency guard).
-- ============================================================================

ALTER TABLE gl.consumption_context
    ADD COLUMN provisioning_answers JSONB;
