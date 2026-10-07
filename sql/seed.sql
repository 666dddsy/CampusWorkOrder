USE campus_work_order;

-- ==================================================
-- 1. 演示用户
-- 当前未实现登录，因此 password_hash 只是测试占位文本
-- ==================================================

INSERT INTO app_user
(id, username, password_hash, role, created_at)
VALUES
    (1, 'student_001', 'demo_hash_not_for_login', 'requester', '2026-10-06 09:00:00'),
    (2, 'student_002', 'demo_hash_not_for_login', 'requester', '2026-10-06 09:01:00'),
    (3, 'worker_001',  'demo_hash_not_for_login', 'worker',    '2026-10-06 09:02:00'),
    (4, 'admin_001',   'demo_hash_not_for_login', 'admin',     '2026-10-06 09:03:00');


-- ==================================================
-- 2. 演示工单
-- 五张工单分别覆盖五种状态
-- ==================================================

INSERT INTO ticket
(id, creator_id, assignee_id, title, description, location,
 category, priority, status, created_at, updated_at)
VALUES
-- created：尚未分配维修人员
(1, 1, NULL,
 '宿舍水龙头漏水',
 '水龙头关闭后仍然持续滴水',
 '东苑3栋201',
 'repair',
 'normal',
 'created',
 '2026-10-06 10:00:00',
 '2026-10-06 10:00:00'),

-- assigned：管理员已经分配维修人员
(2, 2, 3,
 '教室投影仪无法启动',
 '按下电源按钮后没有反应',
 '明德楼201',
 'equipment',
 'high',
 'assigned',
 '2026-10-06 10:10:00',
 '2026-10-06 10:20:00'),

-- processing：维修人员正在处理
(3, 1, 3,
 '校园网无法连接',
 '电脑能够连接校园网，但无法访问互联网',
 '图书馆三楼',
 'network',
 'urgent',
 'processing',
 '2026-10-06 11:00:00',
 '2026-10-06 11:30:00'),

-- resolved：维修完成，等待报修人确认
(4, 2, 3,
 '宿舍灯管损坏',
 '灯管闪烁后完全熄灭',
 '西苑5栋406',
 'repair',
 'normal',
 'resolved',
 '2026-10-06 12:00:00',
 '2026-10-06 14:00:00'),

-- closed：报修人已经确认关闭
(5, 1, 3,
 '公共打印机卡纸',
 '打印机内部卡纸，无法继续打印',
 '图书馆一楼',
 'equipment',
 'low',
 'closed',
 '2026-10-05 09:00:00',
 '2026-10-06 15:00:00');


-- ==================================================
-- 3. 工单状态变化日志
-- ==================================================

INSERT INTO ticket_log
(ticket_id, operator_id, from_status, to_status, note, created_at)
VALUES
-- 工单1：创建
(1, 1, NULL, 'created',
 '报修人创建工单',
 '2026-10-06 10:00:00'),

-- 工单2：创建 -> 分配
(2, 2, NULL, 'created',
 '报修人创建工单',
 '2026-10-06 10:10:00'),

(2, 4, 'created', 'assigned',
 '管理员将工单分配给维修人员',
 '2026-10-06 10:20:00'),

-- 工单3：创建 -> 分配 -> 处理中
(3, 1, NULL, 'created',
 '报修人创建工单',
 '2026-10-06 11:00:00'),

(3, 4, 'created', 'assigned',
 '管理员分配网络维修人员',
 '2026-10-06 11:10:00'),

(3, 3, 'assigned', 'processing',
 '维修人员开始检查网络连接',
 '2026-10-06 11:30:00'),

-- 工单4：创建 -> 分配 -> 处理中 -> 已解决
(4, 2, NULL, 'created',
 '报修人创建工单',
 '2026-10-06 12:00:00'),

(4, 4, 'created', 'assigned',
 '管理员分配维修人员',
 '2026-10-06 12:10:00'),

(4, 3, 'assigned', 'processing',
 '维修人员开始更换灯管',
 '2026-10-06 13:00:00'),

(4, 3, 'processing', 'resolved',
 '灯管已经更换，照明恢复正常',
 '2026-10-06 14:00:00'),

-- 工单5：完整状态流程
(5, 1, NULL, 'created',
 '报修人创建工单',
 '2026-10-05 09:00:00'),

(5, 4, 'created', 'assigned',
 '管理员分配维修人员',
 '2026-10-05 09:10:00'),

(5, 3, 'assigned', 'processing',
 '维修人员开始处理打印机卡纸',
 '2026-10-05 10:00:00'),

(5, 3, 'processing', 'resolved',
 '卡纸已经清除，打印测试正常',
 '2026-10-05 10:30:00'),

(5, 1, 'resolved', 'closed',
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
from ticket t
join app_user creator on t.creator_id = creator.id
left join app_user worker on t.assignee_id=worker.id
order by t.created_at DESC,t.id DESC ;

SELECT
    status,
    COUNT(*) AS ticket_count
FROM ticket
GROUP BY status
ORDER BY FIELD(
                 status,
                 'created',
                 'assigned',
                 'processing',
                 'resolved',
                 'closed'
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
         JOIN app_user worker
              ON t.assignee_id = worker.id
WHERE worker.role = 'worker'
  AND t.status IN ('assigned', 'processing')
ORDER BY t.created_at ASC, t.id ASC;