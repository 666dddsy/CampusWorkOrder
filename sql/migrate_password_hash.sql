USE campus_work_order;

-- ==================================================
-- 已有数据库升级脚本：把演示账号的占位 password_hash 换成可登录的 BCrypt 哈希
-- 空数据库不需要执行本文件，直接跑 schema.sql + seed.sql 即可
-- demo 账号统一密码：campus-demo-2026（仅本地演示，禁止复用到任何真实账号）
-- ==================================================

-- 1. 执行前先确认现状（应为 demo_hash_not_for_login / len=22）
SELECT id, username, LEFT(password_hash, 20) AS hash_prefix,
       CHAR_LENGTH(password_hash) AS len, role
FROM app_user
WHERE id IN (1, 2, 3, 4);

-- 2. 更新四个演示账号
UPDATE app_user
SET password_hash = '{bcrypt}$2a$10$POdF4LnBu2p1rcFUA0z/7eQSIlQDVA4wrjm7YI/IEPPcOPAfUwZFi'
WHERE username IN ('student_001', 'student_002', 'worker_001', 'admin_001');

-- 3. 验证：期望 prefix = {bcrypt}$，len = 68
SELECT username, LEFT(password_hash, 9) AS prefix,
       CHAR_LENGTH(password_hash) AS len
FROM app_user
WHERE id IN (1, 2, 3, 4);
