-- ============================================================================
-- GL-01 Enterprise Setup
-- Schemas: gl, aie · Tables: capability_registry, context_capability,
-- consumption_context, business_group, legal_entity, business_unit,
-- inventory_organisation, sub_inventory, audit_log
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE SCHEMA IF NOT EXISTS gl;
CREATE SCHEMA IF NOT EXISTS aie;

-- ----------------------------------------------------------------------------
-- gl.capability_registry — activation/billing unit master
-- ----------------------------------------------------------------------------
CREATE TABLE gl.capability_registry (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    capability_code         VARCHAR(10)  NOT NULL UNIQUE,
    capability_name         VARCHAR(200) NOT NULL,
    description             TEXT,
    is_active_by_default    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- gl.consumption_context — WORKSPACE (persistent) or SESSION (ephemeral)
-- ----------------------------------------------------------------------------
CREATE TABLE gl.consumption_context (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    segment_type            VARCHAR(20)  NOT NULL CHECK (segment_type IN ('WORKSPACE', 'SESSION')),
    code                    VARCHAR(50)  NOT NULL UNIQUE,
    name                    VARCHAR(200) NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL
);

-- ----------------------------------------------------------------------------
-- gl.context_capability — which capabilities are active per context
-- ----------------------------------------------------------------------------
CREATE TABLE gl.context_capability (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    consumption_context_id  UUID         NOT NULL REFERENCES gl.consumption_context(id),
    capability_code         VARCHAR(10)  NOT NULL REFERENCES gl.capability_registry(capability_code),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    activated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (consumption_context_id, capability_code)
);

-- ----------------------------------------------------------------------------
-- gl.business_group — Thin ES / Thick ES governance root
-- ----------------------------------------------------------------------------
CREATE TABLE gl.business_group (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    consumption_context_id  UUID         NOT NULL REFERENCES gl.consumption_context(id),
    code                    VARCHAR(50)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    es_mode                 VARCHAR(20)  NOT NULL CHECK (es_mode IN ('THICK_ES', 'THIN_ES')),
    default_currency        VARCHAR(3)   NOT NULL DEFAULT 'INR',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL,
    UNIQUE (consumption_context_id, code)
);

-- ----------------------------------------------------------------------------
-- gl.legal_entity — GSTIN never attached here; TAN lives here (Rule: TDS)
-- ----------------------------------------------------------------------------
CREATE TABLE gl.legal_entity (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_group_id       UUID         NOT NULL REFERENCES gl.business_group(id),
    code                    VARCHAR(50)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    accounting_standard     VARCHAR(20)  NOT NULL DEFAULT 'IND_AS'
                                 CHECK (accounting_standard IN ('IND_AS', 'IGAAP', 'IFRS', 'US_GAAP')),
    tan                     VARCHAR(10),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL,
    UNIQUE (business_group_id, code)
);

-- ----------------------------------------------------------------------------
-- gl.business_unit — 1 BU = 1 State = 1 GSTIN
-- ----------------------------------------------------------------------------
CREATE TABLE gl.business_unit (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id         UUID         NOT NULL REFERENCES gl.legal_entity(id),
    code                    VARCHAR(50)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    gstin                   VARCHAR(15)  UNIQUE,
    state_code              VARCHAR(2),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL,
    UNIQUE (legal_entity_id, code)
);

-- ----------------------------------------------------------------------------
-- gl.inventory_organisation — optional under Thin ES
-- ----------------------------------------------------------------------------
CREATE TABLE gl.inventory_organisation (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_unit_id        UUID         NOT NULL REFERENCES gl.business_unit(id),
    code                    VARCHAR(50)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_by              VARCHAR(100) NOT NULL,
    UNIQUE (business_unit_id, code)
);

-- ----------------------------------------------------------------------------
-- gl.sub_inventory
-- ----------------------------------------------------------------------------
CREATE TABLE gl.sub_inventory (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    inventory_organisation_id   UUID         NOT NULL REFERENCES gl.inventory_organisation(id),
    code                        VARCHAR(50)  NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  VARCHAR(100) NOT NULL,
    updated_by                  VARCHAR(100) NOT NULL,
    UNIQUE (inventory_organisation_id, code)
);

-- ----------------------------------------------------------------------------
-- gl.audit_log — shared by ALL capabilities, one row per write, same txn
-- ----------------------------------------------------------------------------
CREATE TABLE gl.audit_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_name     VARCHAR(100) NOT NULL,
    entity_id       UUID         NOT NULL,
    action          VARCHAR(20)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    old_value       JSONB,
    new_value       JSONB,
    performed_by    VARCHAR(100) NOT NULL,
    performed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX idx_context_capability_context ON gl.context_capability(consumption_context_id);
CREATE INDEX idx_business_group_context ON gl.business_group(consumption_context_id);
CREATE INDEX idx_legal_entity_business_group ON gl.legal_entity(business_group_id);
CREATE INDEX idx_business_unit_legal_entity ON gl.business_unit(legal_entity_id);
CREATE INDEX idx_business_unit_gstin ON gl.business_unit(gstin);
CREATE INDEX idx_inventory_org_business_unit ON gl.inventory_organisation(business_unit_id);
CREATE INDEX idx_sub_inventory_inventory_org ON gl.sub_inventory(inventory_organisation_id);
CREATE INDEX idx_audit_log_entity ON gl.audit_log(entity_name, entity_id);
CREATE INDEX idx_audit_log_performed_at ON gl.audit_log(performed_at);

-- ----------------------------------------------------------------------------
-- Seed capability_registry for capabilities delivered by GL-01
-- ----------------------------------------------------------------------------
INSERT INTO gl.capability_registry (capability_code, capability_name, description) VALUES
    ('GL-01', 'Enterprise Setup', 'Business Group, Legal Entity, Business Unit, Inventory Organisation, Sub-Inventory hierarchy');
