-- Phase 3：Sales Order / Payment Entry 写入所需的租户配置与技术幂等表。
-- 不保存订单或收款业务事实。

ALTER TABLE erp_connection
    ADD COLUMN default_company VARCHAR(140);

CREATE TABLE idempotency_record (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenant (id),
    operation        VARCHAR(64)  NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    resource_id      VARCHAR(140),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_idempotency_tenant_op_key UNIQUE (tenant_id, operation, idempotency_key),
    CONSTRAINT idempotency_status_chk CHECK (status IN ('PENDING', 'SUCCEEDED', 'UNKNOWN'))
);

CREATE INDEX ix_idempotency_tenant_created ON idempotency_record (tenant_id, created_at DESC);
