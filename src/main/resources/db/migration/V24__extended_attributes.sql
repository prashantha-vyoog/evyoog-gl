-- V24__extended_attributes.sql
-- Adds extended_attributes JSONB to key GL transactional tables.
-- Phase 1: storage only. Field definition configuration is Phase 2.

-- Journal Header — extra data on journal documents
ALTER TABLE gl.journal_header
    ADD COLUMN IF NOT EXISTS extended_attributes JSONB;

-- Journal Line — extra data on individual debit/credit lines
ALTER TABLE gl.journal_line
    ADD COLUMN IF NOT EXISTS extended_attributes JSONB;

-- Dimension Value — extra metadata on accounts and dimension values
ALTER TABLE gl.dimension_value
    ADD COLUMN IF NOT EXISTS extended_attributes JSONB;

-- GIN indexes for efficient JSONB querying
CREATE INDEX IF NOT EXISTS idx_journal_header_ext_attrs
    ON gl.journal_header USING GIN (extended_attributes)
    WHERE extended_attributes IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_journal_line_ext_attrs
    ON gl.journal_line USING GIN (extended_attributes)
    WHERE extended_attributes IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dimension_value_ext_attrs
    ON gl.dimension_value USING GIN (extended_attributes)
    WHERE extended_attributes IS NOT NULL;

COMMENT ON COLUMN gl.journal_header.extended_attributes IS
    'DFF-equivalent: stores implementation-specific extra fields as JSONB. '
    'Structure: {"context": "CONTEXT_CODE", "field1": "value1", ...}. '
    'Field definitions and validation rules configured in Phase 2.';

COMMENT ON COLUMN gl.journal_line.extended_attributes IS
    'DFF-equivalent: stores line-level extra fields as JSONB. '
    'Examples: project_code, vehicle_number, employee_id, bill_reference.';

COMMENT ON COLUMN gl.dimension_value.extended_attributes IS
    'DFF-equivalent: stores account-level metadata as JSONB. '
    'Examples: regulatory_code, budget_head, ifrs_mapping.';
