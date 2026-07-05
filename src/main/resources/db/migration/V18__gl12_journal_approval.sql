-- V18__gl12_journal_approval.sql
-- GL-12 Journal Approval

CREATE TABLE gl.journal_approval_log (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    journal_header_id   UUID            NOT NULL REFERENCES gl.journal_header(id),
    action              VARCHAR(20)     NOT NULL
                        CHECK (action IN (
                            'SUBMITTED','APPROVED','REJECTED','RECALLED'
                        )),
    performed_by        VARCHAR(100)    NOT NULL,
    comments            VARCHAR(500),
    from_status         VARCHAR(30)     NOT NULL,
    to_status           VARCHAR(30)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_log_journal
    ON gl.journal_approval_log (journal_header_id);
CREATE INDEX idx_approval_log_action
    ON gl.journal_approval_log (action);
CREATE INDEX idx_approval_log_created
    ON gl.journal_approval_log (created_at);
