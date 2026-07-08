-- V23__auth01_security.sql
-- AUTH-01 Security and Access Control — auth schema: users, roles, permissions,
-- refresh tokens, and approval policy configuration. JWT-based authentication
-- and RBAC for all GL and AIE endpoints.

CREATE SCHEMA IF NOT EXISTS auth;

-- uuid-ossp and pgcrypto already enabled by V1 baseline.

-- ── USERS ──────────────────────────────────────────────────────────────────

CREATE TABLE auth.users (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255)    NOT NULL UNIQUE,
    full_name       VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    must_change_pwd BOOLEAN         NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100)
);

-- ── PERMISSIONS ────────────────────────────────────────────────────────────

CREATE TABLE auth.permissions (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(100)    NOT NULL UNIQUE,  -- e.g. gl:journal:approve
    module          VARCHAR(30)     NOT NULL,          -- e.g. gl
    function        VARCHAR(50)     NOT NULL,          -- e.g. journal
    action          VARCHAR(20)     NOT NULL,          -- e.g. approve
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── ROLES ──────────────────────────────────────────────────────────────────

CREATE TABLE auth.roles (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(50)     NOT NULL UNIQUE,   -- e.g. GL_APPROVER
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(255),
    is_system_role  BOOLEAN         NOT NULL DEFAULT FALSE,  -- seeded vs custom
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100)
);

-- ── ROLE ↔ PERMISSION ──────────────────────────────────────────────────────

CREATE TABLE auth.role_permissions (
    role_id         UUID            NOT NULL REFERENCES auth.roles(id),
    permission_id   UUID            NOT NULL REFERENCES auth.permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- ── USER ↔ ROLE (scoped to Legal Entity) ───────────────────────────────────

CREATE TABLE auth.user_roles (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID            NOT NULL REFERENCES auth.users(id),
    role_id         UUID            NOT NULL REFERENCES auth.roles(id),
    legal_entity_id UUID            NOT NULL REFERENCES gl.legal_entity(id),
    assigned_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    assigned_by     VARCHAR(100),
    UNIQUE (user_id, role_id, legal_entity_id)
);

-- ── REFRESH TOKENS ─────────────────────────────────────────────────────────

CREATE TABLE auth.refresh_tokens (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID            NOT NULL REFERENCES auth.users(id),
    token_hash      VARCHAR(255)    NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── APPROVAL POLICY ────────────────────────────────────────────────────────
-- Keyed at Inventory Org → BU → LE → System default (lookup chain)

CREATE TABLE auth.approval_policy (
    id                          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_entity_id             UUID            NOT NULL REFERENCES gl.legal_entity(id),
    business_unit_id            UUID            REFERENCES gl.business_unit(id),       -- nullable
    inventory_org_id            UUID            REFERENCES gl.inventory_organisation(id), -- nullable
    journal_source_code         VARCHAR(30)     NOT NULL,  -- AP, AR, INV, PAYROLL, MANUAL, IMPORT
    requires_approval           BOOLEAN         NOT NULL,
    approval_threshold_amount   NUMERIC(20,4),              -- null = all journals
    approver_role_code          VARCHAR(50),                -- role code e.g. GL_APPROVER
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(100),
    UNIQUE (legal_entity_id, business_unit_id, inventory_org_id, journal_source_code)
);

-- ── INDEXES ────────────────────────────────────────────────────────────────

CREATE INDEX idx_user_roles_user     ON auth.user_roles (user_id);
CREATE INDEX idx_user_roles_le       ON auth.user_roles (legal_entity_id);
CREATE INDEX idx_refresh_tokens_user ON auth.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash ON auth.refresh_tokens (token_hash);
CREATE INDEX idx_approval_policy_le  ON auth.approval_policy (legal_entity_id);

-- ── SEED: Permissions ───────────────────────────────────────────────────────

INSERT INTO auth.permissions (code, module, function, action, description) VALUES
-- Journal permissions
('gl:journal:view',            'gl', 'journal',       'view',    'View journals and journal lines'),
('gl:journal:create',          'gl', 'journal',       'create',  'Create journal entries'),
('gl:journal:edit',            'gl', 'journal',       'edit',    'Edit draft journals'),
('gl:journal:submit',          'gl', 'journal',       'submit',  'Submit journals for approval'),
('gl:journal:approve',         'gl', 'journal',       'approve', 'Approve pending journals'),
('gl:journal:reverse',         'gl', 'journal',       'reverse', 'Reverse posted journals'),
-- Reporting permissions
('gl:trial-balance:view',      'gl', 'trial-balance', 'view',    'View trial balance'),
('gl:trial-balance:export',    'gl', 'trial-balance', 'export',  'Export trial balance'),
('gl:pl:view',                 'gl', 'pl',            'view',    'View profit and loss'),
('gl:pl:export',               'gl', 'pl',            'export',  'Export P&L statement'),
('gl:balance-sheet:view',      'gl', 'balance-sheet', 'view',    'View balance sheet'),
('gl:balance-sheet:export',    'gl', 'balance-sheet', 'export',  'Export balance sheet'),
('gl:account-ledger:view',     'gl', 'account-ledger','view',    'View account ledger'),
('gl:account-ledger:export',   'gl', 'account-ledger','export',  'Export account ledger'),
-- Chart of Accounts
('gl:accounts:view',           'gl', 'accounts',      'view',    'View chart of accounts'),
('gl:accounts:create',         'gl', 'accounts',      'create',  'Create accounts'),
('gl:accounts:edit',           'gl', 'accounts',      'edit',    'Edit accounts'),
-- Period management (accounting calendar, accounting period, period status)
('gl:period:view',             'gl', 'period',        'view',    'View calendars, periods and period status'),
('gl:period:manage',           'gl', 'period',        'manage',  'Create calendars, generate periods, open and close periods'),
-- Recurring journals
('gl:recurring:view',          'gl', 'recurring',     'view',    'View recurring templates'),
('gl:recurring:create',        'gl', 'recurring',     'create',  'Create recurring templates and generate journals from them'),
('gl:recurring:edit',          'gl', 'recurring',     'edit',    'Edit recurring templates'),
-- AIE Import
('gl:aie:view',                'gl', 'aie',           'view',    'View import batches'),
('gl:aie:import',              'gl', 'aie',           'import',  'Import journals via AIE'),
-- GST
('gl:gst:view',                'gl', 'gst',           'view',    'View GST flagged transactions'),
('gl:gst:export',              'gl', 'gst',           'export',  'Export GSTR-1 and GSTR-3B'),
-- TDS
('gl:tds:view',                'gl', 'tds',           'view',    'View TDS recorded transactions'),
('gl:tds:export',              'gl', 'tds',           'export',  'Export TDS summary'),
-- Audit
('gl:audit:view',              'gl', 'audit',         'view',    'View audit trail'),
-- Enterprise setup (business group, legal entity, business unit, inventory org, sub-inventory, consumption context)
('gl:enterprise:view',         'gl', 'enterprise',    'view',    'View enterprise setup structures'),
('gl:enterprise:manage',       'gl', 'enterprise',    'manage',  'Create and edit enterprise setup structures'),
-- Ledger management (ledgers, legal entity ledger assignment)
('gl:ledger:view',             'gl', 'ledger',        'view',    'View ledgers and legal entity ledger assignments'),
('gl:ledger:manage',           'gl', 'ledger',        'manage',  'Create, edit ledgers and assign them to legal entities'),
-- Finance dimensions and dimension values
('gl:dimension:view',          'gl', 'dimension',     'view',    'View finance dimensions and dimension values'),
('gl:dimension:manage',        'gl', 'dimension',     'manage',  'Create and edit finance dimensions and dimension values'),
-- Account balance
('gl:balance:view',            'gl', 'balance',       'view',    'View account balances'),
('gl:balance:manage',          'gl', 'balance',       'manage',  'Carry forward account balances'),
-- Setup wizard
('gl:wizard:run',              'gl', 'wizard',        'run',     'Run the guided setup wizard'),
('gl:wizard:view',             'gl', 'wizard',        'view',    'View setup wizard status'),
-- Approval policy configuration
('gl:approval-policy:view',    'gl', 'approval-policy','view',   'View approval policy configuration'),
('gl:approval-policy:manage',  'gl', 'approval-policy','manage', 'Create, edit and delete approval policy configuration'),
-- User and role management (SYS_ADMIN only)
('gl:users:view',              'gl', 'users',         'view',    'View users'),
('gl:users:create',            'gl', 'users',         'create',  'Create users'),
('gl:users:edit',              'gl', 'users',         'edit',    'Edit users'),
('gl:roles:view',              'gl', 'roles',         'view',    'View roles'),
('gl:roles:create',            'gl', 'roles',         'create',  'Create roles'),
('gl:roles:edit',              'gl', 'roles',         'edit',    'Edit roles')
ON CONFLICT (code) DO NOTHING;

-- ── SEED: Roles ─────────────────────────────────────────────────────────────

INSERT INTO auth.roles (code, name, description, is_system_role) VALUES
('SYS_ADMIN',     'System Administrator', 'Platform-wide security and user management. Module-agnostic.', TRUE),
('GL_MANAGER',    'GL Manager',           'Full GL functional access including approval and period management.', TRUE),
('GL_ACCOUNTANT', 'GL Accountant',        'Day-to-day GL data entry, submission, and recurring journals.', TRUE),
('GL_APPROVER',   'GL Approver',          'Journal approval authority.', TRUE),
('GL_VIEWER',     'GL Viewer',            'Read-only access to all GL data and reports.', TRUE),
('GL_AUDITOR',    'GL Auditor',           'Read-only access plus full export and audit trail.', TRUE)
ON CONFLICT (code) DO NOTHING;

-- ── SEED: Role → Permission assignments ─────────────────────────────────────

-- SYS_ADMIN: every permission
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r CROSS JOIN auth.permissions p
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT DO NOTHING;

-- GL_MANAGER: all view + all export + journal approve + period/ledger/dimension/balance/enterprise manage + aie + gst + tds
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r JOIN auth.permissions p ON TRUE
WHERE r.code = 'GL_MANAGER'
  AND p.code IN (
    'gl:journal:view','gl:journal:create','gl:journal:edit','gl:journal:submit',
    'gl:journal:approve','gl:journal:reverse',
    'gl:trial-balance:view','gl:trial-balance:export',
    'gl:pl:view','gl:pl:export','gl:balance-sheet:view','gl:balance-sheet:export',
    'gl:account-ledger:view','gl:account-ledger:export',
    'gl:accounts:view','gl:accounts:create','gl:accounts:edit',
    'gl:period:view','gl:period:manage',
    'gl:recurring:view','gl:recurring:create','gl:recurring:edit',
    'gl:aie:view','gl:aie:import',
    'gl:gst:view','gl:gst:export','gl:tds:view','gl:tds:export',
    'gl:audit:view',
    'gl:enterprise:view','gl:enterprise:manage',
    'gl:ledger:view','gl:ledger:manage',
    'gl:dimension:view','gl:dimension:manage',
    'gl:balance:view','gl:balance:manage',
    'gl:wizard:run','gl:wizard:view'
  )
ON CONFLICT DO NOTHING;

-- GL_ACCOUNTANT: view + journal create/edit/submit + recurring create/edit
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r JOIN auth.permissions p ON TRUE
WHERE r.code = 'GL_ACCOUNTANT'
  AND p.code IN (
    'gl:journal:view','gl:journal:create','gl:journal:edit','gl:journal:submit',
    'gl:trial-balance:view','gl:pl:view','gl:balance-sheet:view',
    'gl:account-ledger:view','gl:accounts:view','gl:period:view',
    'gl:recurring:view','gl:recurring:create','gl:recurring:edit',
    'gl:aie:view','gl:gst:view','gl:tds:view','gl:audit:view',
    'gl:enterprise:view','gl:ledger:view','gl:dimension:view','gl:balance:view'
  )
ON CONFLICT DO NOTHING;

-- GL_APPROVER: view + journal approve
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r JOIN auth.permissions p ON TRUE
WHERE r.code = 'GL_APPROVER'
  AND p.code IN (
    'gl:journal:view','gl:journal:approve',
    'gl:trial-balance:view','gl:pl:view','gl:balance-sheet:view',
    'gl:account-ledger:view','gl:accounts:view','gl:period:view',
    'gl:audit:view'
  )
ON CONFLICT DO NOTHING;

-- GL_VIEWER: all view permissions only
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r JOIN auth.permissions p ON TRUE
WHERE r.code = 'GL_VIEWER'
  AND p.action = 'view'
ON CONFLICT DO NOTHING;

-- GL_AUDITOR: all view + all export + audit:view
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r JOIN auth.permissions p ON TRUE
WHERE r.code = 'GL_AUDITOR'
  AND (p.action IN ('view', 'export') OR p.code = 'gl:audit:view')
ON CONFLICT DO NOTHING;

-- ── SEED: SYS_ADMIN bootstrap user ─────────────────────────────────────────
-- Password: Admin@eVyoog1 (bcrypt via pgcrypto, cost 12 — must change on first login)
-- pgcrypto's crypt()/gen_salt('bf', N) produces a standard $2a$ bcrypt hash that
-- Spring Security's BCryptPasswordEncoder can verify directly with matches().

INSERT INTO auth.users (email, full_name, password_hash, must_change_pwd, created_by)
VALUES ('admin@evyoog.com', 'System Administrator',
        crypt('Admin@eVyoog1', gen_salt('bf', 12)), TRUE, 'SYSTEM')
ON CONFLICT (email) DO NOTHING;
