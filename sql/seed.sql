USE campus_work_order;

-- ==================================================
-- 1. 演示用户
-- demo 账号统一密码：campus-demo-2026（仅本地演示，禁止复用到任何真实账号）
-- ==================================================

INSERT INTO app_user
(id, username, password_hash, role, created_at)
VALUES
    (1, 'student_001', '{bcrypt}$2a$10$POdF4LnBu2p1rcFUA0z/7eQSIlQDVA4wrjm7YI/IEPPcOPAfUwZFi', 'REQUESTER', '2026-10-06 09:00:00'),
    (2, 'student_002', '{bcrypt}$2a$10$POdF4LnBu2p1rcFUA0z/7eQSIlQDVA4wrjm7YI/IEPPcOPAfUwZFi', 'REQUESTER', '2026-10-06 09:01:00'),
    (3, 'worker_001',  '{bcrypt}$2a$10$POdF4LnBu2p1rcFUA0z/7eQSIlQDVA4wrjm7YI/IEPPcOPAfUwZFi', 'WORKER',    '2026-10-06 09:02:00'),
    (4, 'admin_001',   '{bcrypt}$2a$10$POdF4LnBu2p1rcFUA0z/7eQSIlQDVA4wrjm7YI/IEPPcOPAfUwZFi', 'ADMIN',     '2026-10-06 09:03:00');


-- ==================================================
-- 2. 演示工单
-- 五张工单分别覆盖五种状态
-- ==================================================

INSERT INTO ticket
(id, creator_id, assignee_id, title, description, location,
 category, priority, status, created_at, updated_at)
VALUES
-- CREATED：尚未分配维修人员
(1, 1, NULL,
 '宿舍水龙头漏水',
 '水龙头关闭后仍然持续滴水',
 '东苑3栋201',
 'PLUMBING',
 'MEDIUM',
 'CREATED',
 '2026-10-06 10:00:00',
 '2026-10-06 10:00:00'),

-- ASSIGNED：管理员已经分配维修人员
(2, 2, 3,
 '教室投影仪无法启动',
 '按下电源按钮后没有反应',
 '明德楼201',
 'EQUIPMENT',
 'HIGH',
 'ASSIGNED',
 '2026-10-06 10:10:00',
 '2026-10-06 10:20:00'),

-- PROCESSING：维修人员正在处理
(3, 1, 3,
 '校园网无法连接',
 '电脑能够连接校园网，但无法访问互联网',
 '图书馆三楼',
 'NETWORK',
 'URGENT',
 'PROCESSING',
 '2026-10-06 11:00:00',
 '2026-10-06 11:30:00'),

-- RESOLVED：维修完成，等待报修人确认
(4, 2, 3,
 '宿舍灯管损坏',
 '灯管闪烁后完全熄灭',
 '西苑5栋406',
 'ELECTRICAL',
 'MEDIUM',
 'RESOLVED',
 '2026-10-06 12:00:00',
 '2026-10-06 14:00:00'),

-- CLOSED：报修人已经确认关闭
(5, 1, 3,
 '公共打印机卡纸',
 '打印机内部卡纸，无法继续打印',
 '图书馆一楼',
 'EQUIPMENT',
 'LOW',
 'CLOSED',
 '2026-10-05 09:00:00',
 '2026-10-06 15:00:00');


-- ==================================================
-- 3. 工单状态变化日志
-- ==================================================

INSERT INTO ticket_log
(ticket_id, operator_id, from_status, to_status, note, created_at)
VALUES
-- 工单1：创建
(1, 1, NULL, 'CREATED',
 '报修人创建工单',
 '2026-10-06 10:00:00'),

-- 工单2：创建 -> 分配
(2, 2, NULL, 'CREATED',
 '报修人创建工单',
 '2026-10-06 10:10:00'),

(2, 4, 'CREATED', 'ASSIGNED',
 '管理员将工单分配给维修人员',
 '2026-10-06 10:20:00'),

-- 工单3：创建 -> 分配 -> 处理中
(3, 1, NULL, 'CREATED',
 '报修人创建工单',
 '2026-10-06 11:00:00'),

(3, 4, 'CREATED', 'ASSIGNED',
 '管理员分配网络维修人员',
 '2026-10-06 11:10:00'),

(3, 3, 'ASSIGNED', 'PROCESSING',
 '维修人员开始检查网络连接',
 '2026-10-06 11:30:00'),

-- 工单4：创建 -> 分配 -> 处理中 -> 已解决
(4, 2, NULL, 'CREATED',
 '报修人创建工单',
 '2026-10-06 12:00:00'),

(4, 4, 'CREATED', 'ASSIGNED',
 '管理员分配维修人员',
 '2026-10-06 12:10:00'),

(4, 3, 'ASSIGNED', 'PROCESSING',
 '维修人员开始更换灯管',
 '2026-10-06 13:00:00'),

(4, 3, 'PROCESSING', 'RESOLVED',
 '灯管已经更换，照明恢复正常',
 '2026-10-06 14:00:00'),

-- 工单5：完整状态流程
(5, 1, NULL, 'CREATED',
 '报修人创建工单',
 '2026-10-05 09:00:00'),

(5, 4, 'CREATED', 'ASSIGNED',
 '管理员分配维修人员',
 '2026-10-05 09:10:00'),

(5, 3, 'ASSIGNED', 'PROCESSING',
 '维修人员开始处理打印机卡纸',
 '2026-10-05 10:00:00'),

(5, 3, 'PROCESSING', 'RESOLVED',
 '卡纸已经清除，打印测试正常',
 '2026-10-05 10:30:00'),

(5, 1, 'RESOLVED', 'CLOSED',
 '报修人确认问题已经解决',
 '2026-10-06 15:00:00');

SELECT * FROM app_user;
SELECT * FROM ticket ORDER BY id;
SELECT * FROM ticket_log ORDER BY ticket_id, created_at;

SELECT
    t.id,
    t.title,
    creator.username AS creator_name,
    worker.username AS assignee_name,
    t.priority,
    t.status,
    t.created_at
FROM ticket t
         JOIN app_user creator ON t.creator_id = creator.id
         LEFT JOIN app_user worker ON t.assignee_id = worker.id
ORDER BY t.created_at DESC, t.id DESC;

SELECT
    status,
    COUNT(*) AS ticket_count
FROM ticket
GROUP BY status
ORDER BY FIELD(
                 status,
                 'CREATED',
                 'ASSIGNED',
                 'PROCESSING',
                 'RESOLVED',
                 'CLOSED'
         );

SELECT
    id,
    title,
    status,
    created_at
FROM ticket
ORDER BY created_at DESC, id DESC
LIMIT 2 OFFSET 0;

SELECT
    id,
    title,
    status,
    created_at
FROM ticket
ORDER BY created_at DESC, id DESC
LIMIT 2 OFFSET 2;

SELECT
    id,
    title,
    status,
    created_at
FROM ticket
ORDER BY created_at DESC, id DESC
LIMIT 2 OFFSET 4;

SELECT
    t.id,
    t.title,
    t.location,
    worker.username AS assignee_name,
    t.priority,
    t.status,
    t.created_at
FROM ticket t
         JOIN app_user worker ON t.assignee_id = worker.id
WHERE worker.role = 'WORKER'
  AND t.status IN ('ASSIGNED', 'PROCESSING')
ORDER BY t.created_at ASC, t.id ASC;

SHOW CREATE TABLE ticket;
