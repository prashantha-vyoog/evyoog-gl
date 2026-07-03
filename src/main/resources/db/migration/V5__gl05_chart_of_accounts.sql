-- ============================================================================
-- GL-05 Chart of Accounts Management
-- Adds Intercompany / Cost Centre / date-range / budget metadata to
-- gl.dimension_value (the table that already carries the Natural Account
-- hierarchy, created in GL-04), plus provisioning templates and a scaffold
-- table for the GL-06 Excel import job.
-- ============================================================================

-- ── Intercompany support ──────────────────────────────────────────────────
-- Links an INTERCOMPANY dimension value to its counterparty Legal Entity.
-- Mandatory when dimensionType = INTERCOMPANY.
-- Used for IC balance validation (Phase 2) and elimination entries (Phase 3).
ALTER TABLE gl.dimension_value
ADD COLUMN counterparty_legal_entity_id UUID
    REFERENCES gl.legal_entity(id);

COMMENT ON COLUMN gl.dimension_value.counterparty_legal_entity_id IS
'Populated only for INTERCOMPANY dimension type values.
Links this IC value to its counterparty Legal Entity.
Phase 2: IC balance validation. Phase 3: elimination journal generation.';

-- ── Cost Centre metadata ──────────────────────────────────────────────────
-- Informational in Phase 1. Enables budget control routing in Phase 2.
ALTER TABLE gl.dimension_value
ADD COLUMN cc_manager_name     VARCHAR(255),
ADD COLUMN cc_manager_email    VARCHAR(255),
ADD COLUMN cc_department       VARCHAR(100);

COMMENT ON COLUMN gl.dimension_value.cc_manager_name IS
'Cost Centre manager — informational Phase 1, approval routing Phase 2.';

-- ── Date range (all dimension types) ─────────────────────────────────────
-- Time-bounds any dimension value. Useful for Cost Centres, IC entities,
-- discontinued product lines, completed projects.
ALTER TABLE gl.dimension_value
ADD COLUMN valid_from   DATE,
ADD COLUMN valid_to     DATE;

COMMENT ON COLUMN gl.dimension_value.valid_from IS
'Start date from which this dimension value is valid. Null = no start restriction.';
COMMENT ON COLUMN gl.dimension_value.valid_to IS
'End date after which this dimension value is inactive. Null = no end restriction.';

-- ── Budget control flag ───────────────────────────────────────────────────
-- Phase 1: stored but not enforced.
-- Phase 2: Posting Engine checks budget before allowing posting to this CC.
ALTER TABLE gl.dimension_value
ADD COLUMN budget_controlled   BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN gl.dimension_value.budget_controlled IS
'Phase 1: flag only. Phase 2: Posting Engine rejects postings that exceed
the budget for this dimension value (typically Cost Centres).';

-- ── Index for IC counterparty lookups ────────────────────────────────────
CREATE INDEX idx_dv_counterparty_le
    ON gl.dimension_value (counterparty_legal_entity_id)
    WHERE counterparty_legal_entity_id IS NOT NULL;

-- ── Index for date-range queries ─────────────────────────────────────────
CREATE INDEX idx_dv_valid_from ON gl.dimension_value (valid_from)
    WHERE valid_from IS NOT NULL;
CREATE INDEX idx_dv_valid_to   ON gl.dimension_value (valid_to)
    WHERE valid_to IS NOT NULL;

-- ----------------------------------------------------------------------------
-- gl.provisioning_template — seeded industry Chart of Accounts templates,
-- applied to a Ledger's NATURAL_ACCOUNT dimension via POST .../apply.
-- ----------------------------------------------------------------------------
CREATE TABLE gl.provisioning_template (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    code                VARCHAR(30)     NOT NULL UNIQUE,
    name                VARCHAR(255)    NOT NULL,
    description         VARCHAR(500),
    industry_type       VARCHAR(30)     NOT NULL
                        CHECK (industry_type IN (
                            'MANUFACTURING','TRADING','SERVICES','MIXED'
                        )),
    finance_mode        VARCHAR(20)     NOT NULL
                        CHECK (finance_mode IN ('THICK','THIN','EVENT_ONLY')),
    template_data       JSONB           NOT NULL,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provisioning_template_industry
    ON gl.provisioning_template (industry_type);

-- ----------------------------------------------------------------------------
-- gl.coa_import_job — scaffold for GL-06 Excel import. GL-05 exposes GET only;
-- GL-06 adds the POST that creates and processes these jobs.
-- ----------------------------------------------------------------------------
CREATE TABLE gl.coa_import_job (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    ledger_id               UUID            NOT NULL REFERENCES gl.ledger(id),
    finance_dimension_id    UUID            NOT NULL REFERENCES gl.finance_dimension(id),
    status                  VARCHAR(30)     NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN (
                                'PENDING','PROCESSING','COMPLETED',
                                'COMPLETED_WITH_ERRORS','FAILED'
                            )),
    file_name               VARCHAR(255),
    total_rows              INTEGER         DEFAULT 0,
    processed_rows          INTEGER         DEFAULT 0,
    success_rows            INTEGER         DEFAULT 0,
    error_rows              INTEGER         DEFAULT 0,
    error_details           JSONB,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              UUID
);

CREATE INDEX idx_coa_import_job_ledger ON gl.coa_import_job (ledger_id);
CREATE INDEX idx_coa_import_job_status ON gl.coa_import_job (status);

-- ----------------------------------------------------------------------------
-- Seed three provisioning templates
-- ----------------------------------------------------------------------------
INSERT INTO gl.provisioning_template
    (code, name, description, industry_type, finance_mode, template_data)
VALUES

('MANUFACTURING_THICK',
 'Manufacturing — Full GL',
 'Standard Chart of Accounts for discrete manufacturing companies using full double-entry GL.',
 'MANUFACTURING', 'THICK',
 '{
   "accounts": [
     {"code":"1000","name":"Assets","qualifier":"ASSET","isSummary":true,"parentCode":null},
     {"code":"1100","name":"Current Assets","qualifier":"ASSET","isSummary":true,"parentCode":"1000"},
     {"code":"1110","name":"Cash and Bank","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100"},
     {"code":"1120","name":"Accounts Receivable","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100","gstApplicable":true},
     {"code":"1130","name":"GST Input Credit - CGST","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100","gstApplicable":true},
     {"code":"1140","name":"GST Input Credit - SGST","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100","gstApplicable":true},
     {"code":"1150","name":"GST Input Credit - IGST","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100","gstApplicable":true},
     {"code":"1160","name":"Inventory - Raw Material","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100"},
     {"code":"1170","name":"Inventory - WIP","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100"},
     {"code":"1180","name":"Inventory - Finished Goods","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100"},
     {"code":"1200","name":"Fixed Assets","qualifier":"ASSET","isSummary":true,"parentCode":"1000"},
     {"code":"1210","name":"Plant and Machinery - Gross","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1200"},
     {"code":"1220","name":"Plant and Machinery - Depreciation","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1200"},
     {"code":"2000","name":"Liabilities","qualifier":"LIABILITY","isSummary":true,"parentCode":null},
     {"code":"2100","name":"Current Liabilities","qualifier":"LIABILITY","isSummary":true,"parentCode":"2000"},
     {"code":"2110","name":"Accounts Payable","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100"},
     {"code":"2120","name":"GST Payable - CGST","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","gstApplicable":true},
     {"code":"2130","name":"GST Payable - SGST","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","gstApplicable":true},
     {"code":"2140","name":"GST Payable - IGST","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","gstApplicable":true},
     {"code":"2150","name":"TDS Payable","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","tdsApplicable":true},
     {"code":"2160","name":"Advance from Customers","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100"},
     {"code":"3000","name":"Equity","qualifier":"EQUITY","isSummary":true,"parentCode":null},
     {"code":"3100","name":"Share Capital","qualifier":"EQUITY","isSummary":false,"isPostable":true,"parentCode":"3000"},
     {"code":"3200","name":"Retained Earnings","qualifier":"EQUITY","isSummary":false,"isPostable":true,"parentCode":"3000"},
     {"code":"4000","name":"Revenue","qualifier":"REVENUE","isSummary":true,"parentCode":null},
     {"code":"4100","name":"Sales - Domestic","qualifier":"REVENUE","isSummary":false,"isPostable":true,"parentCode":"4000","gstApplicable":true},
     {"code":"4200","name":"Sales - Export","qualifier":"REVENUE","isSummary":false,"isPostable":true,"parentCode":"4000"},
     {"code":"5000","name":"Expenses","qualifier":"EXPENSE","isSummary":true,"parentCode":null},
     {"code":"5100","name":"Raw Material Consumed","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"},
     {"code":"5200","name":"Employee Costs","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000","tdsApplicable":true},
     {"code":"5300","name":"Manufacturing Overhead","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"},
     {"code":"5400","name":"Depreciation","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"},
     {"code":"5500","name":"Selling and Distribution","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"},
     {"code":"5600","name":"Administration Expenses","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"}
   ]
 }'::jsonb),

('TRADING_THICK',
 'Trading — Full GL',
 'Standard Chart of Accounts for trading companies using full double-entry GL.',
 'TRADING', 'THICK',
 '{
   "accounts": [
     {"code":"1000","name":"Assets","qualifier":"ASSET","isSummary":true,"parentCode":null},
     {"code":"1100","name":"Current Assets","qualifier":"ASSET","isSummary":true,"parentCode":"1000"},
     {"code":"1110","name":"Cash and Bank","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100"},
     {"code":"1120","name":"Accounts Receivable","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100","gstApplicable":true},
     {"code":"1130","name":"GST Input Credit","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100","gstApplicable":true},
     {"code":"1140","name":"Stock in Trade","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1100"},
     {"code":"2000","name":"Liabilities","qualifier":"LIABILITY","isSummary":true,"parentCode":null},
     {"code":"2100","name":"Current Liabilities","qualifier":"LIABILITY","isSummary":true,"parentCode":"2000"},
     {"code":"2110","name":"Accounts Payable","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100"},
     {"code":"2120","name":"GST Payable - CGST","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","gstApplicable":true},
     {"code":"2130","name":"GST Payable - SGST","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","gstApplicable":true},
     {"code":"2140","name":"GST Payable - IGST","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2100","gstApplicable":true},
     {"code":"3000","name":"Equity","qualifier":"EQUITY","isSummary":true,"parentCode":null},
     {"code":"3100","name":"Share Capital","qualifier":"EQUITY","isSummary":false,"isPostable":true,"parentCode":"3000"},
     {"code":"3200","name":"Retained Earnings","qualifier":"EQUITY","isSummary":false,"isPostable":true,"parentCode":"3000"},
     {"code":"4000","name":"Revenue","qualifier":"REVENUE","isSummary":true,"parentCode":null},
     {"code":"4100","name":"Sales Revenue","qualifier":"REVENUE","isSummary":false,"isPostable":true,"parentCode":"4000","gstApplicable":true},
     {"code":"5000","name":"Expenses","qualifier":"EXPENSE","isSummary":true,"parentCode":null},
     {"code":"5100","name":"Purchases","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"},
     {"code":"5200","name":"Employee Costs","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000","tdsApplicable":true},
     {"code":"5300","name":"Operating Expenses","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"}
   ]
 }'::jsonb),

('SERVICES_THIN',
 'Services — Simple GL',
 'Minimal Chart of Accounts for services companies using Thin GL mode.',
 'SERVICES', 'THIN',
 '{
   "accounts": [
     {"code":"1000","name":"Assets","qualifier":"ASSET","isSummary":true,"parentCode":null},
     {"code":"1100","name":"Bank Account","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1000"},
     {"code":"1200","name":"Accounts Receivable","qualifier":"ASSET","isSummary":false,"isPostable":true,"parentCode":"1000","gstApplicable":true},
     {"code":"2000","name":"Liabilities","qualifier":"LIABILITY","isSummary":true,"parentCode":null},
     {"code":"2100","name":"Accounts Payable","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2000"},
     {"code":"2200","name":"GST Payable","qualifier":"LIABILITY","isSummary":false,"isPostable":true,"parentCode":"2000","gstApplicable":true},
     {"code":"4000","name":"Revenue","qualifier":"REVENUE","isSummary":true,"parentCode":null},
     {"code":"4100","name":"Service Revenue","qualifier":"REVENUE","isSummary":false,"isPostable":true,"parentCode":"4000","gstApplicable":true},
     {"code":"5000","name":"Expenses","qualifier":"EXPENSE","isSummary":true,"parentCode":null},
     {"code":"5100","name":"Operating Expenses","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000"},
     {"code":"5200","name":"Employee Costs","qualifier":"EXPENSE","isSummary":false,"isPostable":true,"parentCode":"5000","tdsApplicable":true}
   ]
 }'::jsonb);
