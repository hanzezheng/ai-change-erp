CREATE TABLE tenant (
    id          UUID PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT tenant_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    login          VARCHAR(128) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(128) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT app_user_status_chk CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uk_app_user_login_lower ON app_user (lower(login));

CREATE TABLE membership (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenant (id),
    user_id     UUID        NOT NULL REFERENCES app_user (id),
    role        VARCHAR(32) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT membership_role_chk CHECK (role IN ('OWNER', 'ADMIN', 'STAFF')),
    CONSTRAINT membership_status_chk CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT uk_membership_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX ix_membership_user ON membership (user_id);

-- 一个 SaaS Tenant 对应一个 Frappe / ERPNext Site。
-- selling_price_list / default_warehouse 是现有 Adapter 读取商品价与库存时需要的连接配置，
-- 不是第二套价格或库存事实。
CREATE TABLE erp_connection (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID         NOT NULL REFERENCES tenant (id),
    base_url               VARCHAR(512) NOT NULL,
    site_name              VARCHAR(128),
    api_key_ciphertext     TEXT         NOT NULL,
    api_secret_ciphertext  TEXT         NOT NULL,
    selling_price_list     VARCHAR(128),
    default_warehouse      VARCHAR(128),
    status                 VARCHAR(32)  NOT NULL,
    connect_timeout_ms     INTEGER      NOT NULL,
    read_timeout_ms        INTEGER      NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_erp_connection_tenant UNIQUE (tenant_id),
    CONSTRAINT erp_connection_status_chk CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT erp_connection_timeout_chk CHECK (connect_timeout_ms > 0 AND read_timeout_ms > 0)
);

CREATE UNIQUE INDEX uk_erp_connection_site
    ON erp_connection (lower(base_url), COALESCE(lower(site_name), ''));

CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id),
    tenant_id   UUID        NOT NULL REFERENCES tenant (id),
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_token_user_tenant ON refresh_token (user_id, tenant_id);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    tenant_id   UUID,
    user_id     UUID,
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id   VARCHAR(128),
    result      VARCHAR(32) NOT NULL,
    trace_id    VARCHAR(64),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT audit_log_result_chk CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_audit_log_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX ix_audit_log_action ON audit_log (action);
