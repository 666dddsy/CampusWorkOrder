# CampusWorkOrder 校园工单系统

CampusWorkOrder 是一个基于 Spring Boot、MyBatis 和 MySQL 实现的校园报修工单后端项目。

项目围绕校园报修场景，划分报修人、维修人员和管理员三种角色，通过工单当前状态和状态变化日志记录完整的报修处理过程。

当前版本已经完成核心 MVP 状态闭环：

```text
报修人创建工单
→ 报修人查看自己的工单
→ 管理员分配维修人员
→ 维修人员查看被分配的工单
→ 维修人员开始处理工单
→ 维修人员提交维修结果
→ 报修人确认问题解决并关闭工单
```

对应状态流为：

```text
CREATED → ASSIGNED → PROCESSING → RESOLVED → CLOSED
```

管理员分页筛选、业务权限边界和 Spring Security 最小认证过滤链已经实现；后续将把认证主体连接到数据库用户，移除对客户端自填用户 ID 的信任，并继续完善接口文档和部署。

---

## 当前功能

当前版本已经实现：

- 只有数据库角色为 `REQUESTER` 的用户可以创建工单。
- 按工单 ID 查询详情，并按 `ADMIN`、创建人或当前维修人员校验查看权限。
- 报修人查询自己创建的工单列表。
- 管理员为 `CREATED` 工单分配维修人员。
- 维修人员查询自己被分配的工单列表。
- 当前维修人员将自己的工单从 `ASSIGNED` 改为 `PROCESSING`。
- 当前维修人员提交必填维修结果，将工单从 `PROCESSING` 改为 `RESOLVED`。
- 工单创建人确认问题已经解决，将工单从 `RESOLVED` 改为 `CLOSED`。
- `REQUESTER`、`WORKER` 和 `ADMIN` 的基础角色校验。
- 工单创建人和当前维修人员的数据归属校验。
- Spring Security 最小认证过滤链：所有请求必须先通过 HTTP Basic 认证才能进入 Controller。
- 请求参数校验和字段级错误响应。
- 业务异常的统一处理。
- 创建、分配、开始处理、提交维修结果和关闭工单的事务一致性。
- 每次状态变化写入 `ticket_log` 审计日志。
- 基于条件更新的重复分配、越权处理、重复解决、重复关闭和并发状态保护。
- Controller MockMvc 切片测试。
- Service 真实数据库集成测试。
- 事务失败回滚测试。
- 真实 HTTP 请求和数据库结果验证。
- 仅 `ADMIN` 可访问的管理员分页列表，支持状态、创建人、维修人员筛选、默认参数、边界校验、稳定排序和真实 HTTP 验证。

尚未实现：

- 数据库用户登录、密码哈希验证、JWT 和正式身份认证。
- 统一业务错误码。
- OpenAPI/Swagger 接口文档。
- 生产环境部署。

---

## 工单状态流

完整工单状态流如下：

```text
CREATED
   ↓ 管理员分配维修人员
ASSIGNED
   ↓ 当前维修人员开始处理
PROCESSING
   ↓ 当前维修人员提交维修结果
RESOLVED
   ↓ 工单创建人确认问题解决
CLOSED
```

| 状态 | 含义 | 当前业务实现情况 |
|---|---|---|
| `CREATED` | 报修人已经创建工单，等待管理员分配 | 已实现 |
| `ASSIGNED` | 管理员已经分配维修人员 | 已实现 |
| `PROCESSING` | 维修人员正在处理 | 已实现 |
| `RESOLVED` | 维修人员已经完成维修，等待报修人确认 | 已实现 |
| `CLOSED` | 报修人确认问题解决，工单关闭 | 已实现 |

当前真正实现的业务状态变化为：

```text
CREATED → ASSIGNED → PROCESSING → RESOLVED → CLOSED
```

状态不允许重复操作、回退或跨级变化。例如：

- 已经处于 `ASSIGNED` 状态的工单不能再次分配。
- 只有工单当前的维修人员才能开始处理。
- `CREATED` 工单不能跳过分配直接进入 `PROCESSING`。
- 已经处于 `PROCESSING` 状态的工单不能重复开始处理。
- 只有当前维修人员才能把工单从 `PROCESSING` 改为 `RESOLVED`。
- 已经处于 `RESOLVED` 状态的工单不能重复提交维修结果。
- 只有工单创建人才能把工单从 `RESOLVED` 改为 `CLOSED`。
- 已经处于 `CLOSED` 状态的工单不能重复关闭。
- 客户端不能自行提交任意新的工单状态。
- 状态变化必须通过对应的 Service 业务方法执行。

---

## 用户角色

| 角色 | 支持的功能 | 当前实现情况 |
|---|---|---|
| `REQUESTER` | 创建工单、查看自己的工单、确认关闭 | 已实现 |
| `WORKER` | 查看被分配的工单、开始处理、提交维修结果 | 已实现 |
| `ADMIN` | 查看任意工单详情、分配维修人员、分页和筛选查看全部工单 | 已实现 |

当前已经接入 Spring Security 最小认证骨架：所有请求必须先通过 HTTP Basic，未认证请求在进入 Controller 前返回 `401 Unauthorized`。

当前 HTTP Basic 使用 Spring Boot 启动时生成的临时开发用户和密码，仅用于验证过滤链。临时密码只从本次启动日志读取，不写入源码、README 或 Git；应用重启后可能变化。

以下 README 中的所有 curl 示例，在当前阶段都需要额外添加：

```powershell
-u "user:<本次启动日志中的临时密码>"
```

例如：

```powershell
curl.exe -i -u "user:<本次启动日志中的临时密码>" `
  -H "X-User-Id: 4" `
  "http://localhost:8080/tickets"
```

HTTP Basic 通过后，接口仍暂时通过请求头传递业务用户 ID：

接口暂时通过请求头传递用户 ID：

```http
X-User-Id: 1
```

`X-User-Id` 只是开发阶段用于打通业务链路的临时身份参数，可以由客户端自行填写。当前 HTTP Basic 用户与该 ID 没有关联，因此通过 Basic 后仍可伪造其他用户 ID，不能视为真正的身份认证。

当前角色查询只能证明：

```text
数据库中的这个用户 ID 对应某一种角色
```

但不能证明：

```text
请求发送者真实拥有这个用户 ID
```

下一阶段将把 Spring Security 认证主体连接到 `app_user`，使用 `PasswordEncoder` 验证密码，并从认证上下文取得业务用户 ID，逐步移除对客户端自填 `X-User-Id` 的信任。

即使接入正式认证，Service 中的数据归属校验仍需要保留。例如：

- 某个用户确实是 `WORKER`，并不代表他可以处理所有维修人员的工单。
- 某个用户确实是 `REQUESTER`，并不代表他可以关闭其他报修人创建的工单。

---

## 技术栈

- Java 21
- Spring Boot 4.0.8
- Spring Web MVC
- Jakarta Bean Validation
- MyBatis 4.0.1
- MySQL 8
- Maven
- JUnit
- MockMvc
- Mockito

---

## 项目结构

```text
CampusWorkOrder
├─ docs
│  └─ mvp.md
├─ sql
│  ├─ schema.sql
│  └─ seed.sql
├─ src
│  ├─ main
│  │  ├─ java/com/example/campusworkorder
│  │  │  ├─ CampusWorkOrderApplication.java
│  │  │  ├─ controller
│  │  │  │  └─ TicketController.java
│  │  │  ├─ domain/enums
│  │  │  │  ├─ TicketCategory.java
│  │  │  │  ├─ TicketPriority.java
│  │  │  │  ├─ TicketStatus.java
│  │  │  │  └─ UserRole.java
│  │  │  ├─ dto
│  │  │  │  ├─ TicketAssignRequest.java
│  │  │  │  ├─ TicketCreateRequest.java
│  │  │  │  ├─ TicketResolveRequest.java
│  │  │  │  └─ TicketResponse.java
│  │  │  ├─ entity
│  │  │  │  ├─ Ticket.java
│  │  │  │  └─ TicketLog.java
│  │  │  ├─ exception
│  │  │  │  ├─ BusinessException.java
│  │  │  │  └─ GlobalExceptionHandler.java
│  │  │  ├─ mapper
│  │  │  │  ├─ AppUserMapper.java
│  │  │  │  ├─ TicketLogMapper.java
│  │  │  │  └─ TicketMapper.java
│  │  │  └─ service
│  │  │     └─ TicketService.java
│  │  └─ resources
│  │     ├─ application.yml
│  │     └─ mapper
│  │        ├─ AppUserMapper.xml
│  │        ├─ TicketLogMapper.xml
│  │        └─ TicketMapper.xml
│  └─ test
│     └─ java/com/example/campusworkorder
│        ├─ CampusWorkOrderApplicationTests.java
│        ├─ controller
│        │  └─ TicketControllerTest.java
│        └─ service
│           ├─ TicketServiceIntegrationTest.java
│           └─ TicketServiceRollbackIntegrationTest.java
├─ .gitignore
├─ pom.xml
└─ README.md
```

---

## 分层职责

### Controller

Controller 负责：

- 接收 HTTP 请求。
- 读取路径参数、请求头和 JSON 请求体。
- 触发请求参数校验。
- 调用 Service。
- 将实体对象转换为响应 DTO。
- 返回合适的 HTTP 状态码。

Controller 不直接访问 Mapper，也不负责事务和业务状态规则。

### Service

Service 负责：

- 用户角色校验。
- 工单数据归属校验。
- 工单状态校验。
- 业务规则判断。
- 事务边界。
- 调用多个 Mapper 完成一个完整业务操作。
- 将数据库受影响行数转换为业务结果或业务异常。
- 保证工单当前状态和状态变化日志的一致性。

### Mapper

Mapper 负责：

- 执行数据库查询。
- 插入工单和状态日志。
- 更新工单状态和维修人员。
- 将数据库记录映射为 Java 实体对象。

Mapper 不负责决定 HTTP 状态码，也不负责完整的业务规则。

### DTO

DTO 负责：

- 承载 HTTP 接口的输入或输出数据。
- 限制客户端可以提交的字段。
- 声明请求参数校验规则。
- 避免直接使用数据库实体作为接口请求结构。

---

## 数据库设计

项目使用三张核心表：

```text
app_user
ticket
ticket_log
```

### app_user

保存用户基础信息和角色。

| 字段 | 含义 |
|---|---|
| `id` | 用户主键 |
| `username` | 用户名 |
| `password_hash` | 密码哈希占位字段 |
| `role` | `REQUESTER`、`WORKER` 或 `ADMIN` |
| `created_at` | 创建时间 |

当前尚未实现正式登录，因此 `seed.sql` 中的 `password_hash` 只是测试占位文本。

### ticket

保存工单当前状态、报修人和维修人员。

| 字段 | 含义 |
|---|---|
| `id` | 工单主键 |
| `creator_id` | 报修人 ID |
| `assignee_id` | 当前维修人员 ID，可以为空 |
| `title` | 工单标题 |
| `description` | 问题描述 |
| `location` | 报修位置 |
| `category` | 工单分类 |
| `priority` | 优先级 |
| `status` | 当前状态 |
| `created_at` | 创建时间 |
| `updated_at` | 最后修改时间 |

支持的工单分类：

```text
PLUMBING
ELECTRICAL
NETWORK
EQUIPMENT
OTHER
```

支持的优先级：

```text
LOW
MEDIUM
HIGH
URGENT
```

支持的状态：

```text
CREATED
ASSIGNED
PROCESSING
RESOLVED
CLOSED
```

### ticket_log

保存工单创建和状态变化记录。

| 字段 | 含义 |
|---|---|
| `id` | 日志主键 |
| `ticket_id` | 对应工单 ID |
| `operator_id` | 执行操作的用户 ID |
| `from_status` | 操作前状态，创建日志可以为空 |
| `to_status` | 操作后状态 |
| `note` | 操作说明或维修结果 |
| `created_at` | 操作时间 |

工单创建时记录：

```text
from_status = null
to_status = CREATED
operator_id = creatorId
```

管理员分配时记录：

```text
from_status = CREATED
to_status = ASSIGNED
operator_id = adminId
```

维修人员开始处理时记录：

```text
from_status = ASSIGNED
to_status = PROCESSING
operator_id = 当前维修人员 ID
```

维修人员提交维修结果时记录：

```text
from_status = PROCESSING
to_status = RESOLVED
operator_id = 当前维修人员 ID
note = resolutionNote
```

报修人确认关闭时记录：

```text
from_status = RESOLVED
to_status = CLOSED
operator_id = 工单创建人 ID
note = 报修人确认问题已解决并关闭工单
```

`ticket` 保存工单的当前状态，`ticket_log` 保存每次业务动作的历史信息。

维修结果说明属于某次 `PROCESSING → RESOLVED` 状态变化，因此保存在 `ticket_log.note`，避免在 `ticket` 和日志中重复保存同一事实。

---

## 数据库索引

当前主要索引包括：

```sql
idx_ticket_status_created_at(status, created_at)
idx_ticket_assignee_status(assignee_id, status)
idx_ticket_creator_id(creator_id)
idx_ticket_created_at_id(created_at DESC, id DESC)
idx_ticket_log_ticket_time(ticket_id, created_at)
```

用途：

- `idx_ticket_creator_id`：支持报修人查询自己创建的工单。
- `idx_ticket_assignee_status`：支持维修人员按 `assignee_id` 查询工单，也支持按照维修人员和状态进行筛选。
- `idx_ticket_status_created_at`：支持按照状态和创建时间查询。
- `idx_ticket_created_at_id`：schema 中为全量列表的稳定排序声明的候选索引，是否被分页筛选 SQL 实际采用仍需通过 `EXPLAIN` 验证。
- `idx_ticket_log_ticket_time`：支持查询一张工单的状态变化历史。

修改 `CREATE TABLE IF NOT EXISTS` 内的索引定义不会自动改变已经存在的 ticket 表。本机数据库已由用户通过单独 SQL 创建 `idx_ticket_created_at_id`；schema 中保留相同定义，用于后续空数据库初始化。索引已经存在不等于查询一定使用它，仍需针对实际分页筛选 SQL 执行 `EXPLAIN`。

联合索引：

```text
(assignee_id, status)
```

遵循最左前缀原则。

因此：

- 只按 `assignee_id` 查询可以使用该索引。
- 同时按 `assignee_id` 和 `status` 查询可以使用该索引。
- 只按 `status` 查询时，通常不能高效使用该联合索引，需要结合 `EXPLAIN` 判断实际执行计划。

列表查询使用：

```sql
ORDER BY created_at DESC, id DESC
```

`created_at DESC` 让较新的工单排在前面。

`id DESC` 用于在创建时间相同时提供稳定顺序，避免相同创建时间的记录出现不确定顺序。

---

## 环境要求

运行项目前需要：

- JDK 21
- Maven 3.9 或更高版本
- MySQL 8
- Git

检查 Java：

```powershell
java -version
```

检查 Maven：

```powershell
mvn -version
```

检查 MySQL：

```powershell
mysql --version
```

---

## 数据库初始化

### 1. 登录 MySQL

```powershell
mysql -u root -p
```

### 2. 执行表结构脚本

```sql
SOURCE D:/codejava/CampusWorkOrder/sql/schema.sql;
```

`schema.sql` 会：

- 创建 `campus_work_order` 数据库。
- 创建 `app_user` 表。
- 创建 `ticket` 表。
- 创建 `ticket_log` 表。
- 创建必要的外键、约束和索引。

### 3. 执行演示数据脚本

```sql
SOURCE D:/codejava/CampusWorkOrder/sql/seed.sql;
```

`seed.sql` 会插入：

- 2 个 `REQUESTER`。
- 1 个 `WORKER`。
- 1 个 `ADMIN`。
- 覆盖不同状态的演示工单。
- 对应的状态变化日志。

如果数据库中已经存在相同固定 ID 或用户名，重复执行 `seed.sql` 可能产生主键或唯一索引冲突。

如需重新初始化，应使用独立测试数据库，或者先手动清理旧的演示数据。

不要在包含正式数据的数据库中直接执行清空操作。

---

## 数据库连接配置

`application.yml` 使用环境变量读取数据库用户名和密码：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/campus_work_order?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### PowerShell 临时设置环境变量

```powershell
$env:DB_USER="root"
$env:DB_PASSWORD="你的MySQL密码"
```

这种设置只对当前 PowerShell 窗口有效。

检查变量：

```powershell
$env:DB_USER
$env:DB_PASSWORD
```

不要把真实密码直接写入：

- `application.yml`
- README
- Java 源代码
- Git 提交记录

---

## 启动项目

进入项目目录：

```powershell
cd D:\codejava\CampusWorkOrder
```

设置数据库环境变量：

```powershell
$env:DB_USER="root"
$env:DB_PASSWORD="你的MySQL密码"
```

运行测试：

```powershell
mvn test
```

启动项目：

```powershell
mvn spring-boot:run
```

启动成功后，服务默认运行在：

```text
http://localhost:8080
```

---

## API 接口

当前共实现九个接口：

| 方法 | URL | 功能 |
|---|---|---|
| `POST` | `/tickets` | 创建工单 |
| `GET` | `/tickets/{id}` | 按 ID 查询工单 |
| `GET` | `/tickets/mine` | 查询当前报修人创建的工单 |
| `PATCH` | `/tickets/{id}/assignment` | 管理员分配维修人员 |
| `GET` | `/tickets/assigned-to-me` | 维修人员查询被分配的工单 |
| `PATCH` | `/tickets/{id}/processing` | 当前维修人员开始处理工单 |
| `PATCH` | `/tickets/{id}/resolution` | 当前维修人员提交维修结果 |
| `PATCH` | `/tickets/{id}/closure` | 工单创建人确认问题解决并关闭工单 |
| `GET` | `/tickets` | 管理员分页查询全部工单 |

### 管理员分页查询工单

```http
GET /tickets?page=1&pageSize=10
X-User-Id: 4
```

只有 `ADMIN` 可以访问。`page` 默认值为 1，必须大于等于 1；`pageSize` 默认值为 10，范围为 1—100。查询使用数据库 `COUNT(*)`、`LIMIT/OFFSET`，排序固定为 `created_at DESC, id DESC`，因此创建时间相同的工单仍有稳定顺序。

支持以下三个可选筛选参数：

| 参数 | 类型 | 规则 |
|---|---|---|
| `status` | `TicketStatus` | 必须为 `CREATED`、`ASSIGNED`、`PROCESSING`、`RESOLVED` 或 `CLOSED` |
| `creatorId` | Long | 传入时必须大于 0 |
| `assigneeId` | Long | 传入时必须大于 0 |

三个参数均不传时查询全部工单；同时传入多个参数时使用 AND 组合，结果必须同时满足全部条件。例如：

```http
GET /tickets?page=1&pageSize=2&status=CLOSED&creatorId=1&assigneeId=3
X-User-Id: 4
```

COUNT 与分页列表复用同一段动态 WHERE 条件，因此 `total` 表示筛选后的总条数。合法但不存在的 creatorId/assigneeId 返回 `200 + items=[] + total=0`；非法枚举或非正数 ID 返回 400。

响应字段为 `items`、`total`、`page` 和 `pageSize`。超出最后一页时返回 `200`、空数组和真实 `total`，不返回 404；非法分页参数返回 400；非管理员返回 403。

示例：

```json
{
  "items": [],
  "total": 6,
  "page": 99,
  "pageSize": 2
}
```

当前已完成分页筛选自动化测试、真实 HTTP 验证和本机小表 EXPLAIN。约 6 行数据下，无筛选列表使用全表扫描并额外排序；仅 status 使用 `(status,created_at)` 索引；仅 creatorId 或 assigneeId 时优化器选择全表扫描；三条件组合使用 `index_merge` 后仍需排序。COUNT 查询可使用覆盖索引、`ref` 或 `index_merge`。这些结果只代表当前小数据量，现有索引暂时保留，后续需要使用更大且接近真实分布的数据复测。

---

## 1. 创建工单

### 请求

```http
POST /tickets
X-User-Id: 1
Content-Type: application/json
```

请求体：

```json
{
  "title": "宿舍水管漏水",
  "description": "洗手池下方持续漏水",
  "location": "学生宿舍3栋201",
  "category": "PLUMBING",
  "priority": "HIGH"
}
```

PowerShell 示例：

```powershell
curl.exe -i -X POST "http://localhost:8080/tickets" `
  -H "X-User-Id: 1" `
  -H "Content-Type: application/json" `
  -d '{\"title\":\"宿舍水管漏水\",\"description\":\"洗手池下方持续漏水\",\"location\":\"学生宿舍3栋201\",\"category\":\"PLUMBING\",\"priority\":\"HIGH\"}'
```

### 成功响应

```text
201 Created
```

```json
{
  "id": 6,
  "creatorId": 1,
  "assigneeId": null,
  "title": "宿舍水管漏水",
  "description": "洗手池下方持续漏水",
  "location": "学生宿舍3栋201",
  "category": "PLUMBING",
  "priority": "HIGH",
  "status": "CREATED",
  "createdAt": "2026-10-13T14:30:00"
}
```

创建工单时，客户端不能传入：

- 工单 ID。
- 创建人 ID。
- 维修人员 ID。
- 工单状态。
- 创建时间。

这些字段由请求头、Service 或数据库生成。

只有数据库中角色为 `REQUESTER` 的用户可以创建工单。`ADMIN`、`WORKER` 或不存在的用户 ID 返回：

```text
403 Forbidden
```

角色校验发生在 `ticket` 和 `ticket_log` 写入之前；拒绝创建不会留下工单或创建日志。`X-User-Id` 仍是可伪造的临时身份参数，不等于正式认证。

### 参数校验

| 字段 | 规则 |
|---|---|
| `title` | 不能为空，最大长度 100 |
| `description` | 不能为空，最大长度 1000 |
| `location` | 不能为空，最大长度 100 |
| `category` | 不能为空，必须是支持的枚举值 |
| `priority` | 不能为空，必须是支持的枚举值 |

字段校验失败时返回：

```text
400 Bad Request
```

示例：

```json
{
  "title": "title must not be blank"
}
```

---

## 2. 按 ID 查询工单

### 请求

```http
GET /tickets/{id}
X-User-Id: 1
```

PowerShell 示例：

```powershell
curl.exe -i "http://localhost:8080/tickets/1" `
  -H "X-User-Id: 1"
```

工单存在时返回：

```text
200 OK
```

工单不存在时返回：

```text
404 Not Found
```

详情权限规则：

- `ADMIN` 可以查看任意存在的工单。
- `REQUESTER` 只能查看 `creatorId` 等于自己的工单。
- `WORKER` 只能查看 `assigneeId` 等于自己的工单。
- 不存在的用户或存在但没有资源归属的用户返回 `403 Forbidden`。
- 合法用户查询不存在的工单返回 `404 Not Found`。
- 缺少 `X-User-Id` 请求头返回 `400 Bad Request`。

权限规则由 Service 校验；客户端仍可伪造 `X-User-Id`，因此当前只完成了业务权限边界，尚未完成安全登录认证。

---

## 3. 查询自己创建的工单

### 请求

```http
GET /tickets/mine
X-User-Id: 1
```

PowerShell 示例：

```powershell
curl.exe -i "http://localhost:8080/tickets/mine" `
  -H "X-User-Id: 1"
```

查询 SQL 根据当前用户 ID 限制：

```sql
WHERE creator_id = #{creatorId}
ORDER BY created_at DESC, id DESC
```

查询成功时返回：

```text
200 OK
```

没有符合条件的工单时返回：

```text
200 OK
```

```json
[]
```

空列表表示用户存在、查询成功，但目前没有符合条件的工单，因此不返回 404。

---

## 4. 管理员分配维修人员

### 请求

```http
PATCH /tickets/{id}/assignment
X-User-Id: 4
Content-Type: application/json
```

请求体：

```json
{
  "assigneeId": 3
}
```

PowerShell 示例：

```powershell
curl.exe -i -X PATCH "http://localhost:8080/tickets/1/assignment" `
  -H "X-User-Id: 4" `
  -H "Content-Type: application/json" `
  -d '{\"assigneeId\":3}'
```

### 成功响应

```text
200 OK
```

```json
{
  "id": 1,
  "creatorId": 1,
  "assigneeId": 3,
  "status": "ASSIGNED"
}
```

### 失败状态

| HTTP 状态 | 含义 |
|---|---|
| `400 Bad Request` | 缺少 `assigneeId`，或者目标用户不是 `WORKER` |
| `403 Forbidden` | 操作人不是 `ADMIN` |
| `404 Not Found` | 工单不存在 |
| `409 Conflict` | 工单状态不是 `CREATED`，或者并发更新失败 |

真正执行更新时，SQL 同时限制工单 ID 和旧状态：

```sql
UPDATE ticket
SET assignee_id = #{assigneeId},
    status = 'ASSIGNED'
WHERE id = #{ticketId}
  AND status = 'CREATED'
```

两个并发请求分配同一张工单时，只有一个请求能够成功更新。

---

## 5. 维修人员查询被分配的工单

### 请求

```http
GET /tickets/assigned-to-me
X-User-Id: 3
```

PowerShell 示例：

```powershell
curl.exe -i "http://localhost:8080/tickets/assigned-to-me" `
  -H "X-User-Id: 3"
```

查询成功时返回：

```text
200 OK
```

响应体示例：

```json
[
  {
    "id": 13,
    "creatorId": 1,
    "assigneeId": 3,
    "title": "Git Bash test ticket",
    "description": "Test POST transaction and log",
    "location": "Room 412",
    "category": "PLUMBING",
    "priority": "MEDIUM",
    "status": "ASSIGNED",
    "createdAt": "2026-10-09T22:13:16"
  }
]
```

没有被分配工单时返回：

```json
[]
```

当前用户不存在或角色不是 `WORKER` 时返回：

```text
403 Forbidden
```

该接口不接受客户端传入任意 `assigneeId`，而是使用当前请求头中的用户 ID：

```sql
WHERE assignee_id = #{assigneeId}
ORDER BY created_at DESC, id DESC
```

这能够实现当前开发阶段的数据范围隔离，但不能代替正式身份认证。

---

## 6. 维修人员开始处理工单

### 请求

```http
PATCH /tickets/{id}/processing
X-User-Id: 3
```

该接口不需要 JSON 请求体。

客户端不能自行提交：

```json
{
  "status": "PROCESSING"
}
```

新状态由“开始处理”这个业务动作决定，防止客户端任意选择或跳过状态。

PowerShell 示例：

```powershell
curl.exe -i -X PATCH "http://localhost:8080/tickets/13/processing" `
  -H "X-User-Id: 3"
```

### 成功响应

```text
200 OK
```

```json
{
  "id": 13,
  "creatorId": 1,
  "assigneeId": 3,
  "title": "Git Bash test ticket",
  "description": "Test POST transaction and log",
  "location": "Room 412",
  "category": "PLUMBING",
  "priority": "MEDIUM",
  "status": "PROCESSING",
  "createdAt": "2026-10-09T22:13:16"
}
```

### 失败状态

| HTTP 状态 | 含义 |
|---|---|
| `403 Forbidden` | 当前用户不是 `WORKER`，或者工单不属于当前维修人员 |
| `404 Not Found` | 工单不存在 |
| `409 Conflict` | 工单当前状态不是 `ASSIGNED`，或者条件更新因并发修改而失败 |

### 条件更新

```sql
UPDATE ticket
SET status = 'PROCESSING'
WHERE id = #{ticketId}
  AND assignee_id = #{assigneeId}
  AND status = 'ASSIGNED'
```

条件更新可以防止：

- 非当前处理人操作他人的工单。
- `CREATED` 工单跳过分配直接进入处理状态。
- 已经处于 `PROCESSING` 状态的工单重复开始处理。
- 前置查询完成后，工单被另一个并发请求修改。
- 两个请求同时处理并重复写入状态日志。

### 真实 HTTP 验证

工单 id=13 已完成真实验证：

1. 用户 3 查询被分配工单，id=13 初始状态为 `ASSIGNED`。
2. 无权限用户请求 processing，返回 403。
3. 当前维修人员用户 3 请求 processing，返回 200。
4. 响应和数据库状态均变为 `PROCESSING`。
5. `assigneeId` 保持为 3。
6. 数据库只新增一条 `ASSIGNED → PROCESSING` 日志。

---

## 7. 维修人员提交维修结果

### 请求

```http
PATCH /tickets/{id}/resolution
X-User-Id: 3
Content-Type: application/json
```

请求体：

```json
{
  "resolutionNote": "Replaced damaged cable and restored network connection."
}
```

`resolutionNote` 不能为空，最长 500 个字符。

维修结果写入本次 `PROCESSING → RESOLVED` 状态日志的 `note`，不会重复写入 `ticket` 表。

PowerShell 示例：

```powershell
$body = @{
  resolutionNote = "Replaced damaged cable and restored network connection."
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Patch `
  -Uri "http://localhost:8080/tickets/13/resolution" `
  -Headers @{"X-User-Id" = "3"} `
  -ContentType "application/json" `
  -Body $body
```

### 成功响应

```text
200 OK
```

响应中的：

```text
status = RESOLVED
assigneeId = 原维修人员 ID
```

### 失败状态

| HTTP 状态 | 含义 |
|---|---|
| `400 Bad Request` | `resolutionNote` 为空或长度超过 500 |
| `403 Forbidden` | 当前用户不是 `WORKER`，或者工单不属于当前维修人员 |
| `404 Not Found` | 工单不存在 |
| `409 Conflict` | 工单当前状态不是 `PROCESSING`，或者条件更新因并发修改而失败 |

### 条件更新

```sql
UPDATE ticket
SET status = 'RESOLVED'
WHERE id = #{ticketId}
  AND assignee_id = #{assigneeId}
  AND status = 'PROCESSING'
```

只有成功更新状态的请求，才会把自己的 `resolutionNote` 写入状态日志。

两个请求携带不同的维修结果同时提交时：

- 只有一个请求能够更新成功。
- 只写入一条 `RESOLVED` 日志。
- 保存成功更新请求的 `resolutionNote`。
- 另一个请求返回 409。

### 真实 HTTP 验证

工单 id=13 已完成真实验证：

1. 用户 3 请求 resolution，返回 200。
2. 响应和 `ticket` 表均显示 `assignee_id=3`、`status=RESOLVED`。
3. `ticket_log` 只新增一条 `PROCESSING → RESOLVED` 日志。
4. 日志 `operator_id=3`。
5. 日志 `note` 保存请求中的维修结果说明。
6. 对同一工单重复请求返回 409，不增加第二条 `RESOLVED` 日志。

---

## 8. 报修人确认关闭工单

### 请求

```http
PATCH /tickets/{id}/closure
X-User-Id: 1
```

该接口不需要 JSON 请求体。

客户端不能自行提交：

```json
{
  "status": "CLOSED"
}
```

新状态由“报修人确认问题已解决并关闭工单”这个业务动作固定为 `CLOSED`。

PowerShell 示例：

```powershell
curl.exe -i -X PATCH "http://localhost:8080/tickets/13/closure" `
  -H "X-User-Id:1"
```

### 成功响应

```text
200 OK
```

响应体示例：

```json
{
  "id": 13,
  "creatorId": 1,
  "assigneeId": 3,
  "title": "Git Bash test ticket",
  "description": "Test POST transaction and log",
  "location": "Room 412",
  "category": "PLUMBING",
  "priority": "MEDIUM",
  "status": "CLOSED",
  "createdAt": "2026-10-09T22:13:16"
}
```

关闭工单不会修改：

- `creatorId`
- `assigneeId`
- 工单标题和描述
- 已有状态日志

### 失败状态

| HTTP 状态 | 含义 |
|---|---|
| `403 Forbidden` | 当前用户不是 `REQUESTER`，或者工单不属于当前报修人 |
| `404 Not Found` | 工单不存在 |
| `409 Conflict` | 工单当前状态不是 `RESOLVED`，或者条件更新因并发修改而失败 |

403 响应示例：

```json
{
  "message": "You don't have the right to close this ticket."
}
```

409 响应示例：

```json
{
  "message": "The ticket is not resolved or has already been closed."
}
```

### 条件更新

```sql
UPDATE ticket
SET status = 'CLOSED'
WHERE id = #{ticketId}
  AND creator_id = #{creatorId}
  AND status = 'RESOLVED'
```

条件更新同时限制：

- 工单 ID。
- 工单创建人 ID。
- 合法旧状态 `RESOLVED`。

这样可以防止：

- `WORKER` 或 `ADMIN` 关闭工单。
- 其他 `REQUESTER` 关闭不属于自己的工单。
- `PROCESSING` 工单跳过维修完成直接关闭。
- 已经处于 `CLOSED` 状态的工单重复关闭。
- 两个请求同时关闭并重复写入关闭日志。
- 前置查询通过后，工单状态被另一个请求修改。

### 关闭日志

成功关闭后写入：

```text
from_status = RESOLVED
to_status = CLOSED
operator_id = 工单创建人 ID
note = 报修人确认问题已解决并关闭工单
```

### 真实 HTTP 验证

工单 id=13 已完成真实验证：

1. 维修人员用户 3 请求 `/tickets/13/closure`，返回 403。
2. 工单创建人用户 1 请求 `/tickets/13/closure`，返回 200。
3. 响应中 `creatorId=1`、`assigneeId=3`、`status=CLOSED`。
4. `ticket` 表中的工单状态变为 `CLOSED`。
5. `ticket_log` 新增一条 `RESOLVED → CLOSED` 日志。
6. 日志 `operator_id=1`。
7. 对同一工单重复请求返回 409。
8. 重复请求不会增加第二条 `CLOSED` 日志。

---

## 创建工单事务

创建一张工单需要执行两次数据库写入：

```text
INSERT ticket
INSERT ticket_log
```

业务流程：

```text
Controller 接收并校验请求
→ Service 创建 Ticket
→ Mapper 插入 ticket
→ MySQL 回填生成的工单 ID
→ Mapper 插入 CREATED 日志
→ Service 返回生成的工单 ID
→ Controller 查询完整工单
→ 返回 201 Created
```

如果创建日志插入失败，已经插入的工单必须回滚。

数据库中不能留下：

```text
存在 ticket
但没有对应的 CREATED 日志
```

---

## 管理员分配事务

管理员分配工单时，需要在一个事务中完成：

1. 查询操作人角色。
2. 确认操作人为 `ADMIN`。
3. 查询目标用户角色。
4. 确认目标用户为 `WORKER`。
5. 查询工单是否存在。
6. 确认工单当前状态为 `CREATED`。
7. 使用条件更新写入 `assignee_id` 并把状态改为 `ASSIGNED`。
8. 写入一条 `CREATED → ASSIGNED` 状态日志。
9. 返回更新后的工单。

如果日志写入失败，工单更新也必须回滚。

回滚后保持：

```text
status = CREATED
assignee_id = null
```

---

## 维修人员开始处理事务

维修人员开始处理工单时，需要在一个事务中完成：

1. 查询当前用户角色。
2. 确认当前用户为 `WORKER`。
3. 查询目标工单。
4. 确认工单属于当前维修人员。
5. 确认工单状态为 `ASSIGNED`。
6. 使用条件更新把状态改为 `PROCESSING`。
7. 重新查询更新后的工单。
8. 写入一条 `ASSIGNED → PROCESSING` 状态日志。
9. 返回更新后的完整工单。

如果 PROCESSING 日志写入失败，工单状态更新必须回滚。

回滚后保持：

```text
status = ASSIGNED
assignee_id = 原维修人员 ID
```

---

## 维修人员提交维修结果事务

维修人员提交维修结果时，需要在一个事务中完成：

1. 查询当前用户角色。
2. 确认当前用户为 `WORKER`。
3. 查询目标工单。
4. 确认工单属于当前维修人员。
5. 确认工单状态为 `PROCESSING`。
6. 使用条件更新把状态改为 `RESOLVED`。
7. 重新查询更新后的工单。
8. 写入一条 `PROCESSING → RESOLVED` 状态日志。
9. 把 `resolutionNote` 保存到日志 `note`。
10. 返回更新后的完整工单。

如果 RESOLVED 日志写入失败，状态更新必须回滚。

回滚后保持：

```text
status = PROCESSING
assignee_id = 原维修人员 ID
没有新增 RESOLVED 日志
```

---

## 报修人确认关闭事务

报修人确认关闭工单时，需要在一个事务中完成：

1. 查询当前用户角色。
2. 确认当前用户为 `REQUESTER`。
3. 查询目标工单。
4. 确认当前用户是工单创建人。
5. 确认工单状态为 `RESOLVED`。
6. 使用带工单 ID、创建人 ID 和旧状态的条件更新，把状态改为 `CLOSED`。
7. 重新查询更新后的工单。
8. 写入一条 `RESOLVED → CLOSED` 状态日志。
9. 返回更新后的完整工单。

如果 CLOSED 日志写入失败，状态更新必须回滚。

回滚后保持：

```text
status = RESOLVED
creator_id = 原报修人 ID
assignee_id = 原维修人员 ID
没有新增 CLOSED 日志
```

---

## MyBatis 参数绑定

SQL 参数使用 MyBatis `#{}` 绑定：

```sql
WHERE assignee_id = #{assigneeId}
```

参数通过预编译方式传入，而不是直接拼接到 SQL 字符串中。

项目不使用 `${}` 拼接普通用户输入，避免输入内容改变 SQL 结构。

---

## 下划线与驼峰映射

`application.yml` 中启用了：

```yaml
mybatis:
  configuration:
    map-underscore-to-camel-case: true
```

因此数据库字段：

```text
creator_id
assignee_id
created_at
updated_at
```

可以映射到 Java 属性：

```text
creatorId
assigneeId
createdAt
updatedAt
```

---

## HTTP 状态码语义

项目当前主要使用：

| 状态码 | 含义 |
|---|---|
| `200 OK` | 查询或状态操作成功 |
| `201 Created` | 工单创建成功 |
| `400 Bad Request` | 请求格式或字段校验失败 |
| `403 Forbidden` | 当前用户身份已知，但没有执行该操作的权限 |
| `404 Not Found` | 目标工单不存在 |
| `409 Conflict` | 工单存在，但当前状态不允许执行该操作 |

业务异常统一返回：

```json
{
  "message": "具体错误信息"
}
```

字段校验异常返回字段名与错误信息：

```json
{
  "title": "title must not be blank"
}
```

---

## 测试

在项目根目录执行：

```powershell
mvn test
```

当前源码共有 **70 条测试**：

- 40 条 `TicketController` MockMvc 测试。
- 24 条 Service 真实数据库集成测试。
- 5 条事务失败回滚测试。
- 1 条 Spring 上下文启动测试。

用户已在本机执行完整 `mvn test`，确认当前 70 条测试全部通过。

### Controller 测试

当前共有 40 条 Controller MockMvc 测试：

1. 合法创建请求返回 `201`。
2. 空标题返回 `400`。
3. 缺少 `X-User-Id` 返回 `400`。
4. 非法工单分类返回 `400`。
5. 工单详情存在时返回 `200`。
6. 工单详情不存在时返回 `404`。
7. 查询报修人个人工单列表返回 `200`。
8. 报修人个人工单列表为空时返回 `200 + []`。
9. 管理员成功分配工单时返回 `200`。
10. 分配请求缺少 `assigneeId` 时返回 `400`。
11. 重复分配或状态冲突时返回 `409`。
12. 非管理员执行分配时返回 `403`。
13. 维修人员查询被分配工单时返回 `200` 和正确列表。
14. 维修人员没有被分配工单时返回 `200 + []`。
15. 非维修人员调用被分配工单接口时返回 `403`。
16. 维修人员成功开始处理工单时返回 `200` 和 `PROCESSING`。
17. 开始处理的工单不存在时返回 `404`。
18. 无权限用户开始处理工单时返回 `403`。
19. 工单状态不允许开始处理时返回 `409`。
20. 维修人员成功提交维修结果时返回 `200` 和 `RESOLVED`。
21. 维修结果为空时返回 `400`，并且不调用 Service。
22. 维修完成的工单不存在时返回 `404`。
23. 无权限用户提交维修结果时返回 `403`。
24. 工单状态不允许维修完成时返回 `409`。
25. 报修人成功关闭工单时返回 `200` 和 `CLOSED`。
26. 关闭的工单不存在时返回 `404`。
27. 无权限用户关闭工单时返回 `403`。
28. 工单状态不允许关闭时返回 `409`。
29. 管理员使用默认分页参数查询返回 `200` 和分页元数据。
30. 管理员使用自定义 page/pageSize 查询返回正确页码和 items。
31. 管理员查询空页时返回空数组并保留 total。
32. 非法分页参数或非数字 page 返回 `400`，且不调用 Service。
33. 管理员列表被非管理员访问时返回 `403`。
34. status、creatorId、assigneeId 能正确绑定到分页查询 DTO 并传入 Service。
35. 非法 status、creatorId=0 和 assigneeId=-1 返回 `400`，且不调用 Service。
36. 详情 Service 抛出权限异常时返回 `403` 和对应 message。
37. 详情缺少 `X-User-Id` 时返回 `400`，且不调用 Service。
38. 非 `REQUESTER` 创建工单时返回 `403` 和对应 message。
39. 未认证访问接口时由 SecurityFilterChain 返回 `401`，且不调用 Service。
40. 模拟认证并携带现有 `X-User-Id` 时能够越过过滤链，进入 Controller 并返回 `200`。

Controller 测试使用模拟的 Service，不连接真实数据库，主要验证：

- HTTP 方法和 URL。
- 请求头和请求体。
- 参数校验。
- HTTP 状态码。
- JSON 响应结构。
- Controller 是否正确调用 Service。

### Service 集成测试

Service 集成测试连接真实 MySQL，当前共有 24 条测试：

1. 创建工单时同时插入工单和创建日志，并正确回填数据库生成的主键。
2. 管理员分配后，工单状态变为 `ASSIGNED`、`assignee_id` 正确保存，并写入分配日志。
3. 重复分配已经处于 `ASSIGNED` 状态的工单时返回冲突，并且不会写入第二条分配日志。
4. 两个维修人员存在不同工单时，只能查询分配给自己的工单，并按 `created_at DESC, id DESC` 排序。
5. 当前维修人员开始处理工单后，状态变为 `PROCESSING`，assigneeId 不变，并写入处理日志。
6. 另一个 `WORKER` 尝试处理不属于自己的工单时返回 403，状态和日志均不变化。
7. 尝试处理非 `ASSIGNED` 工单时返回 409，并且不会新增处理日志。
8. 当前维修人员提交维修结果后，状态变为 `RESOLVED`，assigneeId 不变，结果说明写入状态日志。
9. 另一个 `WORKER` 尝试解决不属于自己的工单时返回 403，状态和日志均不变化。
10. 尝试解决非 `PROCESSING` 工单时返回 409，并且不会新增 RESOLVED 日志。
11. 工单创建人成功关闭工单后，状态变为 `CLOSED`，creatorId 和 assigneeId 不变，并写入关闭日志。
12. 非 `REQUESTER` 用户尝试关闭工单时返回 403，状态和日志均不变化。
13. 其他 `REQUESTER` 尝试关闭不属于自己的工单时返回 403，状态和日志均不变化。
14. 尝试关闭 `PROCESSING` 或已经 `CLOSED` 的工单时返回 409，并且不会新增 CLOSED 日志。
15. 管理员分页查询第一页和第二页时返回稳定顺序、正确 page/pageSize 与真实 total。
16. 超出最后一页时返回空 items 但保留真实 total；相同 created_at 时按 id DESC 排序。
17. REQUESTER、WORKER 和不存在用户查询管理员列表时均返回 403，分页边界由 DTO/Controller 阶段返回 400。
18. 分别按 status、creatorId、assigneeId 单条件查询时，items 和 total 与真实数据库结果一致。
19. 同时使用 status、creatorId、assigneeId 时只返回满足全部 AND 条件的工单。
20. 组合筛选没有匹配结果时返回空 items 和 total=0。
21. 筛选结果跨两页时，两页 total 保持一致，排序正确且工单不重复。
22. `ADMIN`、工单创建人和被分配维修人员查询同一张工单详情均成功。
23. 其他 `REQUESTER`、其他 `WORKER` 和不存在用户查询详情返回 403；合法用户查询不存在工单返回 null。
24. `ADMIN`、`WORKER` 和不存在用户创建工单均返回 403，且 ticket 与 ticket_log 的总行数不增加。

### 事务回滚测试

当前共有 5 条事务回滚测试：

1. 创建日志写入失败时，已经插入的工单整体回滚。
2. 分配日志写入失败时，工单状态和维修人员字段整体回滚。
3. PROCESSING 日志写入失败时，工单状态从 `PROCESSING` 回滚为 `ASSIGNED`，维修人员 ID 保持不变。
4. RESOLVED 日志写入失败时，工单状态从 `RESOLVED` 回滚为 `PROCESSING`，维修人员 ID 保持不变。
5. CLOSED 日志写入失败时，工单状态从 `CLOSED` 回滚为 `RESOLVED`，创建人和维修人员 ID 均保持不变。

### 测试分层

Controller 测试、Service 集成测试、事务回滚测试和真实 HTTP 请求关注的范围不同，不能完全互相替代。

| 测试方式 | 主要验证内容 |
|---|---|
| Controller MockMvc | HTTP 参数、路由、状态码、JSON 和 Controller 调用 |
| Service 集成测试 | 业务规则、MyBatis、SQL 和真实数据库结果 |
| 事务回滚测试 | 多次数据库写入失败后的整体回滚 |
| 真实 HTTP 验证 | 应用启动后的完整请求链路和实际数据库结果 |

---

## 当前进度

- [x] 确定 MVP 功能范围
- [x] 设计用户角色和工单状态流
- [x] 设计数据库表结构和索引
- [x] 编写本地演示数据
- [x] 搭建 Spring Boot 工程
- [x] 接入 MyBatis 和 MySQL
- [x] 实现请求参数校验和字段级错误响应
- [x] 实现业务异常统一处理
- [x] 实现 `POST /tickets`
- [x] 实现创建工单和创建日志
- [x] 实现创建事务及失败回滚
- [x] 实现 `GET /tickets/{id}`
- [x] 实现 `GET /tickets/mine`
- [x] 完成报修人列表的真实多用户隔离验证
- [x] 实现 `PATCH /tickets/{id}/assignment`
- [x] 实现管理员和维修人员角色校验
- [x] 实现 `CREATED → ASSIGNED`
- [x] 实现重复分配冲突处理
- [x] 实现分配成功日志
- [x] 实现分配事务及失败回滚
- [x] 完成分配接口真实 HTTP 验证
- [x] 实现 `GET /tickets/assigned-to-me`
- [x] 完成维修人员列表的真实数据隔离验证
- [x] 实现 `PATCH /tickets/{id}/processing`
- [x] 实现 `ASSIGNED → PROCESSING`
- [x] 实现维修人员归属校验
- [x] 实现开始处理的条件更新和状态日志
- [x] 实现开始处理事务及失败回滚
- [x] 完成 processing 接口真实 HTTP 验证
- [x] 实现 `PATCH /tickets/{id}/resolution`
- [x] 实现 `PROCESSING → RESOLVED`
- [x] 实现必填维修结果说明及长度校验
- [x] 实现维修完成的条件更新和状态日志
- [x] 实现维修完成事务及失败回滚
- [x] 完成 resolution 接口真实 HTTP 与数据库验证
- [x] 实现 `PATCH /tickets/{id}/closure`
- [x] 实现 `RESOLVED → CLOSED`
- [x] 实现只有工单创建人可以确认关闭
- [x] 实现关闭工单的条件更新和状态日志
- [x] 实现关闭事务及失败回滚
- [x] 完成 closure 接口真实 HTTP 与数据库验证
- [x] 实现管理员分页查询全部工单
- [x] 完成分页参数边界、COUNT、LIMIT/OFFSET 和稳定排序
- [x] 完成分页 Controller 与 Service 真实数据库测试
- [x] 实现管理员列表的状态、创建人和维修人员筛选
- [x] 保证 COUNT 与分页列表复用同一动态 WHERE 条件
- [x] 完成筛选的单条件、组合条件、空结果和跨页测试
- [x] 完成 40 条 Controller MockMvc 测试
- [x] 完成 24 条 Service 真实数据库集成测试
- [x] 完成 5 条事务失败回滚测试
- [x] 完成核心工单状态流闭环
- [x] 修复工单详情接口的越权查询问题
- [x] 限制只有 `REQUESTER` 可以创建工单
- [x] 接入 Spring Security 最小 HTTP Basic 认证过滤链
- [ ] 将认证主体连接到数据库用户并移除对客户端自填 `X-User-Id` 的信任
- [ ] 实现密码哈希验证和正式登录认证
- [x] 使用当前小数据量完成分页筛选查询和索引的初步 EXPLAIN
- [ ] 使用更大且接近真实分布的数据复测分页筛选查询和索引
- [ ] 增加统一业务错误码
- [ ] 完成接口文档和健康检查
- [ ] 完成部署

---

## 当前限制

当前版本存在以下明确限制：

- 当前 HTTP Basic 只使用 Spring Boot 临时开发用户，尚未查询 `app_user` 或验证数据库密码哈希。
- HTTP Basic 主体和 `X-User-Id` 没有关联；客户端通过 Basic 后仍可伪造业务用户 ID。
- 当前角色和资源归属校验仍依赖客户端提供的临时用户 ID，不能防止身份冒用。
- 管理员列表已完成当前约 6 行小表的初步 EXPLAIN，但结果不能代表大数据量下的索引选择。
- 状态更新使用条件更新进行基础并发保护，但尚未实现版本号或完整的乐观锁机制。
- 尚未实现统一的业务错误码。
- 尚未生成 OpenAPI/Swagger 接口文档。
- 尚未进行生产环境部署。

核心状态流已经完整实现，但这些限制仍需要在进入正式使用或写入更完整的简历描述前继续处理。

---

## 第一版暂不实现

为了避免项目范围失控，第一版暂不实现：

- 前端页面。
- Redis。
- 消息队列。
- 文件上传。
- 短信和邮件通知。
- 工单评价和星级。
- 工单重新打开。
- 通用工作流引擎。
- 微服务拆分。
- 分布式事务。
- Kubernetes。
- 复杂组织架构和多租户系统。

当前优先级为：

```text
状态流闭环（已完成）
→ 列表筛选与小表 EXPLAIN（已完成）
→ 临时身份下的详情与创建权限（已完成）
→ Spring Security 最小认证过滤链（已完成）
→ 数据库用户认证和业务身份绑定
→ 接口文档和工程完善
→ Docker 与部署
```

---

## 后续计划

后续按照以下顺序继续开发：

1. 将 Spring Security 认证主体连接到 `app_user`，使用 `PasswordEncoder` 验证密码，并移除对客户端自填 `X-User-Id` 的信任。
2. 使用更大且接近真实分布的数据复测筛选、排序和索引选择。
3. 增加统一业务错误码。
4. 增加 OpenAPI/Swagger 接口文档。
5. 增加 Actuator 健康检查和必要的应用日志。
6. 使用 Docker Compose 启动 MySQL 和应用。
7. 完成从空环境启动的 README 复验。
8. 整理项目演示、简历描述和面试问答。

---

## 项目设计原则

当前项目遵循以下原则：

- Controller 只处理 HTTP 层职责。
- Service 负责业务规则和事务。
- Mapper 只负责数据库访问。
- 请求 DTO 与数据库实体分离。
- 客户端不能直接决定工单状态。
- 状态更新必须同时校验旧状态。
- 需要数据归属的操作必须校验当前用户 ID。
- 用户角色和资源归属是两个不同的权限条件。
- 工单当前状态和状态变化日志必须保持一致。
- 状态日志写入失败时，工单状态更新必须回滚。
- 条件更新必须检查数据库受影响行数。
- 前置查询负责生成清晰的业务错误，条件更新负责兜住并发窗口。
- 列表无数据时返回空数组，不返回 `null` 或 `404`。
- 用户只能查询和操作业务规则允许的数据。
- 测试数据使用唯一值，并避免污染演示数据。
- 已完成功能和计划功能在 README 中明确区分。
