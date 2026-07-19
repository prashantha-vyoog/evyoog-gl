-- V26__gl19_je_source_reference.sql
-- GL-19 AIE Source Reference — aie.je_source_reference does NOT exist in any
-- prior migration (verified: no migration and no live table before this one).
-- Same situation as GL-14's recurring_journal_template — created here in full,
-- rather than only adding indexes as the build spec assumed.

CREATE TABLE aie.je_source_reference (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    journal_header_id     UUID NOT NULL REFERENCES gl.journal_header(id),
    source_system         VARCHAR(30) NOT NULL,
    source_document_type  VARCHAR(30) NOT NULL,
    source_document_id    VARCHAR(100) NOT NULL,
    source_document_ref   VARCHAR(255),
    source_line_number    INTEGER,
    amount                NUMERIC(20, 4),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            VARCHAR(100)
);

CREATE INDEX idx_je_source_ref_journal_header
    ON aie.je_source_reference (journal_header_id);

CREATE INDEX idx_je_source_ref_source_lookup
    ON aie.je_source_reference (source_system, source_document_id);
