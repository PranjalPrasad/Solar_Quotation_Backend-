-- =====================================================================
-- Solar CRM seed data. Idempotent: every INSERT is guarded by NOT EXISTS, safe to run on every start.
-- Roles, modules, permissions and the PLATFORM tenant ONLY. NO USERS are seeded here.
-- The first Super Admin is created by AdminBootstrapRunner from the environment
-- (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD, see .env.example).
-- Module codes map to the frontend sidebar keys (QUOTATION -> quotation, USER -> user_management ...)
-- and routes are the real frontend page paths. Route NULL = no frontend page (hidden from sidebar).
-- Platform-only modules (CHANNEL_PARTNER, AUDIT, ROLE) belong to SUPER_ADMIN only: the backend refuses to grant
-- them to any other role, and this file removes any such grant that exists from older data.
-- Grants follow the frontend's role menus:
--   SUPER_ADMIN            : every module
--   CHANNEL_PARTNER_ADMIN  : dashboard, quotation, product, customer, reports, project, calculator, approval
--   CHANNEL_PARTNER_SALES  : same without approval (view/create/edit only)
-- =====================================================================

INSERT INTO channel_partner (code, name, status, partner_type, referral_percent, deleted, version, created_at)
SELECT 'PLATFORM', 'Solar CRM Platform', 'ACTIVE', 'PLATFORM', 0, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM channel_partner WHERE code = 'PLATFORM');

INSERT INTO role (code, name, description, data_scope, is_system, status, deleted, version, created_at)
SELECT 'SUPER_ADMIN', 'Super Admin', 'Built-in role', 'ALL', 1, 'ACTIVE', 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'SUPER_ADMIN');

INSERT INTO role (code, name, description, data_scope, is_system, status, deleted, version, created_at)
SELECT 'CHANNEL_PARTNER_ADMIN', 'Partner Admin', 'Built-in role', 'PARTNER', 1, 'ACTIVE', 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'CHANNEL_PARTNER_ADMIN');

INSERT INTO role (code, name, description, data_scope, is_system, status, deleted, version, created_at)
SELECT 'CHANNEL_PARTNER_SALES', 'Partner Sales', 'Built-in role', 'OWN', 1, 'ACTIVE', 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'CHANNEL_PARTNER_SALES');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'DASHBOARD', 'Dashboard', '/dashboard/dashboard.html', 0, 1, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'DASHBOARD');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'QUOTATION', 'Quotation Management', '/Quotation/quotation.html', 0, 2, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'QUOTATION');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'PRODUCT', 'Product Management', '/productM/productm.html', 0, 3, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'PRODUCT');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'CUSTOMER', 'Customer Management', '/customer/customer.html', 0, 4, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'CUSTOMER');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'REPORTS', 'Reports Management', '/reports/reports.html', 0, 5, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'REPORTS');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'PROJECT', 'Project Management', '/project/project.html', 0, 6, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'PROJECT');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'CHANNEL_PARTNER', 'Channel Partners', '/channel-partner/channel.html', 1, 7, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'CHANNEL_PARTNER');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'USER', 'User Management', '/user-management/user.html', 0, 8, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'USER');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'CALCULATOR', 'Cost Calculator', '/calc/calc.html', 0, 9, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'CALCULATOR');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'APPROVAL', 'Approval Management', '/approval/approval.html', 0, 10, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'APPROVAL');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'SETTINGS', 'Settings', '/settings/settings.html', 0, 11, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'SETTINGS');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'ROLE', 'Roles & Permissions', NULL, 1, 12, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'ROLE');

INSERT INTO module (code, name, route, platform_only, sort_order, deleted, version, created_at)
SELECT 'AUDIT', 'Audit Log', '/audit/audit.html', 1, 13, 0, 0, UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM module WHERE code = 'AUDIT');

-- existing databases: the row above was seeded earlier with no route, so the sidebar link never appeared
UPDATE module SET route = '/audit/audit.html' WHERE code = 'AUDIT' AND route IS NULL;

-- existing databases: ROLE used to be seeded as a normal module. Platform-only modules can never be granted to partner roles.
UPDATE module SET platform_only = 1 WHERE code IN ('CHANNEL_PARTNER', 'AUDIT', 'ROLE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'DASHBOARD:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'DASHBOARD' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'DASHBOARD:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'QUOTATION:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'QUOTATION' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'QUOTATION:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'QUOTATION:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'QUOTATION' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'QUOTATION:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'QUOTATION:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'QUOTATION' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'QUOTATION:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'DELETE', 'QUOTATION:DELETE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'QUOTATION' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'QUOTATION:DELETE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'PRODUCT:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PRODUCT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PRODUCT:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'PRODUCT:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PRODUCT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PRODUCT:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'PRODUCT:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PRODUCT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PRODUCT:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'DELETE', 'PRODUCT:DELETE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PRODUCT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PRODUCT:DELETE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'CUSTOMER:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CUSTOMER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CUSTOMER:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'CUSTOMER:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CUSTOMER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CUSTOMER:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'CUSTOMER:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CUSTOMER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CUSTOMER:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'DELETE', 'CUSTOMER:DELETE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CUSTOMER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CUSTOMER:DELETE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'REPORTS:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'REPORTS' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'REPORTS:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'PROJECT:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PROJECT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PROJECT:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'PROJECT:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PROJECT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PROJECT:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'PROJECT:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PROJECT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PROJECT:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'DELETE', 'PROJECT:DELETE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'PROJECT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PROJECT:DELETE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'CHANNEL_PARTNER:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CHANNEL_PARTNER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CHANNEL_PARTNER:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'CHANNEL_PARTNER:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CHANNEL_PARTNER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CHANNEL_PARTNER:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'CHANNEL_PARTNER:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CHANNEL_PARTNER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CHANNEL_PARTNER:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'USER:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'USER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'USER:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'USER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'USER:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'USER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'DELETE', 'USER:DELETE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'USER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER:DELETE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'RESET_PASSWORD', 'USER:RESET_PASSWORD', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'USER' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER:RESET_PASSWORD');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'CALCULATOR:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CALCULATOR' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CALCULATOR:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'CREATE', 'CALCULATOR:CREATE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CALCULATOR' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CALCULATOR:CREATE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'CALCULATOR:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CALCULATOR' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CALCULATOR:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'DELETE', 'CALCULATOR:DELETE', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'CALCULATOR' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'CALCULATOR:DELETE');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'APPROVAL:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'APPROVAL' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APPROVAL:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'APPROVAL:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'APPROVAL' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'APPROVAL:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'SETTINGS:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'SETTINGS' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SETTINGS:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'SETTINGS:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'SETTINGS' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'SETTINGS:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'ROLE:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'ROLE' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ROLE:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EDIT', 'ROLE:EDIT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'ROLE' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'ROLE:EDIT');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'VIEW', 'AUDIT:VIEW', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'AUDIT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AUDIT:VIEW');

INSERT INTO permission (module_id, action, code, deleted, version, created_at)
SELECT m.id, 'EXPORT', 'AUDIT:EXPORT', 0, 0, UTC_TIMESTAMP() FROM module m
WHERE m.code = 'AUDIT' AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'AUDIT:EXPORT');

-- Cleanup: no role except SUPER_ADMIN may hold a platform-only permission (e.g. ROLE:EDIT on CHANNEL_PARTNER_ADMIN).
DELETE rp FROM role_permission rp
JOIN permission p ON p.id = rp.permission_id
JOIN module m ON m.id = p.module_id
JOIN role r ON r.id = rp.role_id
WHERE m.platform_only = 1 AND r.code <> 'SUPER_ADMIN';

-- SUPER_ADMIN: every permission
INSERT INTO role_permission (role_id, permission_id, deleted, version, created_at)
SELECT r.id, p.id, 0, 0, UTC_TIMESTAMP() FROM role r, permission p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- CHANNEL_PARTNER_ADMIN
INSERT INTO role_permission (role_id, permission_id, deleted, version, created_at)
SELECT r.id, p.id, 0, 0, UTC_TIMESTAMP() FROM role r, permission p, module m
WHERE r.code = 'CHANNEL_PARTNER_ADMIN' AND p.module_id = m.id AND m.code IN ('DASHBOARD','QUOTATION','PRODUCT','CUSTOMER','REPORTS','PROJECT','CALCULATOR','APPROVAL')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- CHANNEL_PARTNER_SALES
INSERT INTO role_permission (role_id, permission_id, deleted, version, created_at)
SELECT r.id, p.id, 0, 0, UTC_TIMESTAMP() FROM role r, permission p, module m
WHERE r.code = 'CHANNEL_PARTNER_SALES' AND p.module_id = m.id AND m.code IN ('DASHBOARD','QUOTATION','PRODUCT','CUSTOMER','REPORTS','PROJECT','CALCULATOR')
  AND p.action IN ('VIEW','CREATE','EDIT')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- (No app_user insert: see AdminBootstrapRunner.)