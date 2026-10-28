-- Quotation history: one row per save of a quotation (create / edit / status change / delete).
-- No foreign key to `quotations` on purpose: the history must survive when a quotation is deleted.
-- MySQL syntax. For PostgreSQL use:  id BIGSERIAL PRIMARY KEY  and drop the ENGINE/CHARSET clause.

CREATE TABLE quotation_history (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id     BIGINT        NOT NULL,
    quotation_code   VARCHAR(50)   NOT NULL,
    version          INT           NOT NULL,                -- 1 = created, 2 = first change, ...
    action           VARCHAR(30)   NOT NULL,                -- CREATED | UPDATED | STATUS_CHANGED | CONVERTED | DELETED
    summary          VARCHAR(500),
    status           VARCHAR(20),                           -- quotation status after this change
    total_amount     DECIMAL(14,2),                         -- quotation total after this change
    changed_by_id    BIGINT,
    changed_by_name  VARCHAR(150),
    changed_by_role  VARCHAR(50),
    changed_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changes_json     LONGTEXT,                              -- [{"field":"...","oldValue":"...","newValue":"..."}]
    snapshot_json    LONGTEXT,                              -- full quotation as it was saved (audit / restore)
    CONSTRAINT uq_quotation_history_version UNIQUE (quotation_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_quotation_history_quotation ON quotation_history (quotation_id, version);