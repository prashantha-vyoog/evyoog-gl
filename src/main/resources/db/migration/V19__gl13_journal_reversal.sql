-- ============================================================================
-- V19 — GL-13 Journal Reversal
--
-- is_reversal and reversal_of_id already exist on gl.journal_header (V9), and
-- the REVERSAL journal_source / journal_category codes are already seeded
-- (V9). The only thing this capability needs is a lookup index for finding
-- the reversal of a given journal.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_journal_header_reversal_of
    ON gl.journal_header (reversal_of_id)
    WHERE reversal_of_id IS NOT NULL;
