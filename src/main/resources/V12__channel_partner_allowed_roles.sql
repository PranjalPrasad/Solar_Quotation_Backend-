-- Which roles each partner (and the PLATFORM partner) may assign to its users.
CREATE TABLE IF NOT EXISTS partner_allowed_role (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel_partner_id BIGINT NOT NULL,
  role_id            BIGINT NOT NULL,
  UNIQUE KEY uk_partner_role (channel_partner_id, role_id)
);

-- Seed example: allow every active, non-ALL-scope role for every existing partner (including PLATFORM).
-- Review before running: tighten per partner if some roles (e.g. accountant) should not be available everywhere.
-- Adjust table/column names if yours differ (channel_partner, role, data_scope, status, deleted).
INSERT IGNORE INTO partner_allowed_role (channel_partner_id, role_id)
SELECT p.id, r.id
FROM channel_partner p
JOIN role r ON r.deleted = 0 AND r.status = 'ACTIVE' AND r.data_scope <> 'ALL'
WHERE p.deleted = 0;