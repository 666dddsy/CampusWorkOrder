# CampusWorkOrder 校园工单系统

CampusWorkOrder 是一个基于 Spring Boot、MyBatis 和 MySQL 实现的校园报修工单后端项目。

项目围绕校园报修场景，划分报修人、维修人员和管理员三种角色，通过工单当前状态和状态变化日志记录完整的报修处理过程。

当前版本已经打通：

```text
报修人创建工单
→ 查询工单
→ 报修人查看自己的工单
→ 管理员分配维修人员
→ 维修人员查看被分配的工单
→ 维修人员开始处理工单
```

项目仍在持续开发，下一阶段将继续完成：

```text
PROCESSING → RESOLVED → CLOSED
```

随后再接入正式身份认证、分页筛选和部署。

---

## 当前功能

当前版本已经实现：

- 报修人创建工单。
- 按工单 ID 查询工单详情。
- 报修人查询自己创建的工单列表。
- 管理员为 `CREATED` 工单分配维修人员。
- 维修人员查询自己被分配的工单列表。
- 当前维修人员将自己的工单从 `ASSIGNED` 改为 `PROCESSING`。
- 管理员和维修人员的基础角色校验。
- 工单创建人和当前维修人员的数据范围隔离。
- 请求参数校验和字段级错误响应。
- 业务异常的统一处理。
- 创建工单与写入创建日志的事务一致性。
- 分配工单与写入分配日志的事务一致性。
- 开始处理与写入处理日志的事务一致性。
- 基于条件更新的重复分配、越权处理和并发状态保护。
- Controller MockMvc 切片测试。
- Service 真实数据库集成测试。
- 事务失败回滚测试。
- 真实 HTTP 请求与数据库结果验证。

尚未实现：

- 维修人员标记工单已经解决。
- 报修人确认工单关闭。
- 管理员查看全部工单。
- Spring Security、JWT 和正式登录认证。
- 分页和条件筛选。
- 生产环境部署。

---

## 工单状态流

完整工单状态设计如下：

```text
CREATED
   ↓ 管理员分配维修人员
ASSIGNED
   ↓ 当前维修人员开始处理
PROCESSING
   ↓ 维修人员完成维修
RESOLVED
   ↓ 报修人确认解决
CLOSED
```

| 状态 | 含义 | 当前业务实现情况 |
|---|---|---|
| `CREATED` | 报修人已经创建工单，等待管理员分配 | 已实现 |
| `ASSIGNED` | 管理员已经分配维修人员 | 已实现 |
| `PROCESSING` | 维修人员正在处理 | 已实现 |
| `RESOLVED` | 维修完成，等待报修人确认 | 尚未实现状态更新接口 |
| `CLOSED` | 报修人确认问题解决，工单关闭 | 尚未实现状态更新接口 |

当前真正实现的业务状态变化为：

```text
CREATED → ASSIGNED → PROCESSING
```

`seed.sql` 中包含五种状态的演示数据，用于数据库查询和展示验证，但演示数据不代表所有状态变化接口都已经实现。

状态不允许重复操作或跨级变化。例如：

- 已经处于 `ASSIGNED` 状态的工单不能再次分配。
- 只有被分配的维修人员才能开始处理工单。
- `CREATED` 工单不能直接变为 `PROCESSING`。
- 已经处于 `PROCESSING` 状态的工单不能重复开始处理。
- 客户端不能任意提交新的工单状态。
- 状态变化必须由对应的 Service 业务方法执行。

---

## 用户角色

| 角色 | 计划支持的功能 | 当前实现情况 |
|---|---|---|
| `REQUESTER` | 创建工单、查看自己的工单、确认关闭 | 已实现创建和个人列表查询 |
| `WORKER` | 查看被分配的工单、开始处理、标记已解决 | 已实现列表查询和开始处理 |
| `ADMIN` | 查看全部工单、分配维修人员 | 已实现分配维修人员，全部工单查询尚未实现 |

当前没有实现 Spring Security、JWT 或正式登录功能。

接口暂时通过请求头传递用户 ID：

```http
X-User-Id: 1
```

`X-User-Id` 只是开发阶段用于打通业务链路的临时身份参数，可以由客户端自行填写，不能视为真正的身份认证。

当前角色查询只能证明：

```text
数据库中的这个用户 ID 对应某一种角色
```

但不能证明：

```text
请求发送者真实拥有这个用户 ID
```

正式认证将在后续接入 Spring Security 后实现。

即使接入正式认证，Service 中的数据归属校验仍然需要保留。例如，某个用户确实是 `WORKER`，并不代表他可以处理所有维修人员的工单。

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

Controller 不直接访问 Mapper，也不负责实现事务和业务状态规则。

### Service

Service 负责：

- 用户角色校验。
- 工单数据归属校验。
- 工单状态校验。
- 业务规则判断。
- 事务边界。
- 调用多个 Mapper 完成一个完整业务操作。
- 将数据库受影响行数转换为业务结果或业务异常。
- 保证工单当前状态与状态日志的一致性。

### Mapper

Mapper 负责：

- 执行数据库查询。
- 插入工单和状态日志。
- 更新工单状态和维修人员。
- 将数据库记录映射为 Java 实体对象。

Mapper 不负责决定 HTTP 状态码，也不负责完整的业务规则。

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
| `note` | 操作说明 |
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

---

## 数据库索引

当前主要索引包括：

```sql
idx_ticket_status_created_at(status, created_at)
idx_ticket_assignee_status(assignee_id, status)
idx_ticket_creator_id(creator_id)
idx_ticket_log_ticket_time(ticket_id, created_at)
```

用途：

- `idx_ticket_creator_id`：支持报修人查询自己创建的工单。
- `idx_ticket_assignee_status`：支持维修人员按 `assignee_id` 查询工单，也支持按照维修人员和状态执行条件更新或筛选。
- `idx_ticket_status_created_at`：支持按状态和创建时间查询。
- `idx_ticket_log_ticket_time`：支持查询一张工单的状态变化历史。

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
- 创建必要的约束和索引。

### 3. 执行演示数据脚本

```sql
SOURCE D:/codejava/CampusWorkOrder/sql/seed.sql;
```

`seed.sql` 会插入：

- 2 个 `REQUESTER`。
- 1 个 `WORKER`。
- 1 个 `ADMIN`。
- 5 张覆盖不同状态的演示工单。
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

当前共实现六个接口：

| 方法 | URL | 功能 |
|---|---|---|
| `POST` | `/tickets` | 创建工单 |
| `GET` | `/tickets/{id}` | 按 ID 查询工单 |
| `GET` | `/tickets/mine` | 查询当前报修人创建的工单 |
| `PATCH` | `/tickets/{id}/assignment` | 管理员分配维修人员 |
| `GET` | `/tickets/assigned-to-me` | 维修人员查询被分配的工单 |
| `PATCH` | `/tickets/{id}/processing` | 当前维修人员开始处理工单 |

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
```

PowerShell 示例：

```powershell
curl.exe -i "http://localhost:8080/tickets/1"
```

工单存在时返回：

```text
200 OK
```

工单不存在时返回：

```text
404 Not Found
```

当前详情接口尚未进行用户身份和工单归属校验。

知道工单 ID 的请求者目前可以查询该工单，这属于当前版本的明确限制。

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

业务异常响应格式：

```json
{
  "message": "具体错误信息"
}
```

真正执行更新时，SQL 同时限制工单 ID 和旧状态：

```sql
UPDATE ticket
SET assignee_id = #{assigneeId},
    status = 'ASSIGNED'
WHERE id = #{ticketId}
  AND status = 'CREATED'
```

两个并发请求分配同一张工单时，预期只有一个请求更新成功。

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

该接口不接受客户端通过查询参数传入任意 `assigneeId`，而是使用当前请求头中的用户 ID：

```sql
WHERE assignee_id = #{assigneeId}
ORDER BY created_at DESC, id DESC
```

这能够实现当前开发阶段的业务数据隔离，但不能代替正式身份认证。

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

成功时返回：

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

403 响应示例：

```json
{
  "message": "You don't have the right to process this ticket."
}
```

409 响应示例：

```json
{
  "message": "The status of ticket isn't assigned"
}
```

### 数据归属和状态保护

开始处理时不能只按照工单 ID 更新。

真正执行的条件更新同时限制：

- 工单 ID。
- 当前维修人员 ID。
- 工单旧状态。

```sql
UPDATE ticket
SET status = 'PROCESSING'
WHERE id = #{ticketId}
  AND assignee_id = #{assigneeId}
  AND status = 'ASSIGNED'
```

这样能够防止：

- 非当前处理人操作他人的工单。
- `CREATED` 工单跳过分配直接进入处理状态。
- 已经处于 `PROCESSING` 状态的工单重复开始处理。
- 前置查询完成后，工单被另一个并发请求修改。
- 两个请求同时开始处理并重复写入状态日志。

条件更新必须影响 1 行。

如果影响行数不是 1，Service 会将其转换为业务冲突，不继续写入 PROCESSING 日志。

### 真实 HTTP 验证

当前已经使用工单 id=13 进行了真实验证：

1. 用户 3 查询 `/tickets/assigned-to-me`，返回 200，id=13 的初始状态为 `ASSIGNED`。
2. 无权限用户 2 请求 `/tickets/13/processing`，返回 403。
3. 实际处理人用户 3 请求 `/tickets/13/processing`，返回 200。
4. 响应中 id=13 的状态变为 `PROCESSING`，`assigneeId` 仍为 3。

“另一个 WORKER 操作他人工单”的情况已经由真实数据库 Service 集成测试覆盖。

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

状态日志内容：

```text
from_status = ASSIGNED
to_status = PROCESSING
operator_id = 当前维修人员 ID
```

如果 PROCESSING 日志写入失败，工单状态更新必须回滚。

回滚后保持：

```text
status = ASSIGNED
assignee_id = 原维修人员 ID
```

这样可以避免工单已经进入处理中状态，但数据库中没有对应的状态变化记录。

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

## 测试

在项目根目录执行：

```powershell
mvn test
```

当前共有 30 条测试：

- 19 条 `TicketController` MockMvc 测试。
- 7 条 Service 真实数据库集成测试。
- 3 条事务失败回滚测试。
- 1 条 Spring 上下文启动测试。

用户已经在本机执行全部测试并确认通过。

### Controller 测试

当前共有 19 条 Controller MockMvc 测试：

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

Controller 测试使用模拟的 Service，不连接真实数据库，主要验证：

- HTTP 方法和 URL。
- 请求头和请求体。
- 参数校验。
- HTTP 状态码。
- JSON 响应结构。
- Controller 是否正确调用 Service。

### Service 集成测试

Service 集成测试连接真实 MySQL，当前共有 7 条测试：

1. 创建工单时同时插入工单和创建日志，并正确回填数据库生成的主键。
2. 管理员分配后，工单状态变为 `ASSIGNED`、`assignee_id` 正确保存，并写入分配日志。
3. 重复分配已经处于 `ASSIGNED` 状态的工单时返回冲突，并且不会写入第二条分配日志。
4. 两个维修人员存在不同工单时，只能查询到分配给自己的工单，并按 `created_at DESC, id DESC` 排序。
5. 当前维修人员开始处理工单后，状态变为 `PROCESSING`，assigneeId 不变，并写入一条处理日志。
6. 另一个 `WORKER` 尝试处理不属于自己的工单时返回 403，状态和日志均不变化。
7. 尝试处理 `CREATED` 或已经处于 `PROCESSING` 状态的工单时返回 409，并且不会新增处理日志。

### 事务回滚测试

当前共有 3 条事务回滚测试：

1. 创建日志写入失败时，已经插入的工单整体回滚。
2. 分配日志写入失败时，工单状态和维修人员字段整体回滚。
3. PROCESSING 日志写入失败时，工单状态从 `PROCESSING` 回滚为 `ASSIGNED`，维修人员 ID 保持不变。

### 测试分层

Controller 测试、Service 集成测试、回滚测试和真实 HTTP 请求关注的范围不同，不能完全互相替代。

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
- [x] 实现创建工单与创建日志
- [x] 实现创建事务及失败回滚
- [x] 实现请求参数校验和字段级错误响应
- [x] 实现业务异常统一处理
- [x] 实现 `POST /tickets`
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
- [x] 完成分配接口 Controller MockMvc 测试
- [x] 实现 `GET /tickets/assigned-to-me`
- [x] 完成维修人员列表的 Controller MockMvc 测试
- [x] 完成维修人员列表的真实数据隔离验证
- [x] 实现 `PATCH /tickets/{id}/processing`
- [x] 实现 `ASSIGNED → PROCESSING`
- [x] 实现维修人员归属校验
- [x] 实现开始处理的条件更新
- [x] 实现 PROCESSING 状态日志
- [x] 实现开始处理事务及失败回滚
- [x] 完成 processing 接口真实 HTTP 验证
- [x] 完成 processing 接口 Controller MockMvc 测试
- [x] 完成其他 WORKER 越权处理测试
- [x] 完成非法状态处理测试
- [x] 完成 19 条 Controller MockMvc 测试
- [x] 完成 7 条 Service 真实数据库集成测试
- [x] 完成 3 条事务失败回滚测试
- [ ] 实现管理员查看全部工单
- [ ] 实现 `PROCESSING → RESOLVED`
- [ ] 实现 `RESOLVED → CLOSED`
- [ ] 实现正式登录和权限认证
- [ ] 增加分页和筛选
- [ ] 完成部署

---

## 当前限制

当前版本存在以下明确限制：

- `X-User-Id` 可以被客户端伪造，不是真正的身份认证。
- 创建工单接口还没有校验请求人是否为 `REQUESTER`。
- 工单详情接口尚未进行归属和角色权限校验。
- 尚未实现管理员查看全部工单。
- 维修人员已经可以开始处理工单，但尚未实现标记维修完成。
- 报修人尚未实现确认关闭。
- 状态流目前实现到 `CREATED → ASSIGNED → PROCESSING`。
- 列表接口没有分页、筛选和数量限制。
- 状态更新使用条件更新进行基础并发保护，但尚未实现版本号或完整的乐观锁机制。
- 尚未实现统一的业务错误码。
- 尚未进行生产环境部署。

这些内容属于后续计划，不能作为已经完成的项目能力写入简历。

---

## 第一版暂不实现

为了避免项目范围失控，第一版暂不实现：

- 前端页面。
- Redis。
- 消息队列。
- 文件上传。
- 短信和邮件通知。
- 通用工作流引擎。
- 微服务拆分。
- 分布式事务。
- Kubernetes。
- 复杂组织架构和多租户系统。

第一版优先完成：

```text
状态流闭环
→ 权限认证
→ 自动化测试
→ 分页筛选
→ 部署
```

---

## 后续计划

后续按照以下顺序继续开发：

1. 实现维修人员将工单从 `PROCESSING` 改为 `RESOLVED`。
2. 实现报修人将工单从 `RESOLVED` 改为 `CLOSED`。
3. 为每次状态变化增加条件更新、状态日志和事务回滚测试。
4. 实现管理员查看全部工单。
5. 修复工单详情接口的越权查询问题。
6. 接入 Spring Security 和正式登录认证。
7. 增加分页、状态筛选、分类筛选和优先级筛选。
8. 增加统一业务错误码。
9. 完成项目部署。
10. 整理项目演示、简历描述和面试问答。

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
- 工单当前状态和状态变化日志必须保持一致。
- 列表无数据时返回空数组，不返回 `null` 或 `404`。
- 用户只能查询和操作业务规则允许的数据。
- 测试数据使用唯一值，并避免污染演示数据。
- 已完成能力与计划能力在 README 中明确区分。