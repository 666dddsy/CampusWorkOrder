       # CampusWorkOrder 校园工单系统

CampusWorkOrder 是一个基于 Spring Boot、MyBatis 和 MySQL 实现的校园报修工单后端项目。

项目围绕校园报修场景，划分报修人、维修人员和管理员三种角色，通过工单状态和状态变化日志记录报修处理过程。

当前版本已经打通：

```text
创建工单
→ 查询工单
→ 报修人查看自己的工单
→ 管理员分配维修人员
→ 维修人员查看被分配的工单
```

项目仍在持续开发，目前重点是完成完整工单状态流、权限认证、分页筛选和部署。

---

## 当前功能

当前版本已经实现：

- 报修人创建工单。
- 按工单 ID 查询工单详情。
- 报修人查询自己创建的工单列表。
- 管理员为 `CREATED` 工单分配维修人员。
- 维修人员查询自己被分配的工单列表。
- 管理员和维修人员的基础角色校验。
- 请求参数校验和字段级错误响应。
- 业务异常的统一处理。
- 创建工单与写入创建日志的事务一致性。
- 分配工单与写入状态日志的事务一致性。
- 基于条件更新的重复分配和并发状态保护。
- 报修人和维修人员列表的数据隔离。
- Controller MockMvc 切片测试。
- Service 真实数据库集成测试。
- 事务失败回滚测试。
- 真实 HTTP 请求与数据库结果验证。

尚未实现：

- 维修人员开始处理工单。
- 维修人员标记工单已经解决。
- 报修人确认工单关闭。
- 管理员查看全部工单。
- Spring Security、JWT 和正式登录认证。
- 分页、筛选和生产环境部署。

---

## 工单状态流

完整工单状态设计如下：

```text
CREATED
   ↓ 管理员分配维修人员
ASSIGNED
   ↓ 维修人员开始处理
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
| `PROCESSING` | 维修人员正在处理 | 尚未实现状态更新接口 |
| `RESOLVED` | 维修完成，等待报修人确认 | 尚未实现状态更新接口 |
| `CLOSED` | 报修人确认问题解决，工单关闭 | 尚未实现状态更新接口 |

当前真正实现的业务状态变化为：

```text
CREATED → ASSIGNED
```

`seed.sql` 中包含五种状态的演示数据，用于数据库查询和页面展示验证，但这不代表所有状态变化接口都已经实现。

状态不允许重复操作或跨级变化。例如：

- 已经处于 `ASSIGNED` 状态的工单不能再次分配。
- 当前版本不能直接把 `CREATED` 工单改为 `PROCESSING`。
- 状态变化必须由对应业务方法执行，不能由客户端任意提交新状态。

---

## 用户角色

| 角色 | 计划支持的功能 | 当前实现情况 |
|---|---|---|
| `REQUESTER` | 创建工单、查看自己的工单、确认关闭 | 已实现创建和个人列表查询 |
| `WORKER` | 查看被分配的工单、开始处理、标记已解决 | 已实现被分配工单列表查询，状态处理尚未实现 |
| `ADMIN` | 查看全部工单、分配维修人员 | 已实现分配维修人员，全部工单查询尚未实现 |

当前没有实现 Spring Security、JWT 或正式登录功能。

接口暂时通过请求头传递用户 ID：

```http
X-User-Id: 1
```

`X-User-Id` 只是开发阶段用于打通业务链路的临时身份参数，可以由客户端自行填写，不能视为真正的用户认证。

当前角色校验能够证明：

```text
数据库中的这个用户 ID 对应某一种角色
```

但不能证明：

```text
请求发送者真实拥有这个用户 ID
```

正式认证将在后续接入 Spring Security 后实现。

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
- 触发参数校验。
- 调用 Service。
- 将实体对象转换为响应 DTO。
- 返回合适的 HTTP 状态码。

Controller 不直接访问 Mapper，也不负责实现事务和业务状态规则。

### Service

Service 负责：

- 用户角色校验。
- 工单状态校验。
- 业务规则判断。
- 事务边界。
- 调用多个 Mapper 完成一个完整业务操作。
- 将数据库受影响行数转换为业务结果或异常。
- 保证工单状态与状态日志的一致性。

### Mapper

Mapper 负责：

- 执行数据库查询。
- 插入工单和状态日志。
- 更新工单状态和维修人员。
- 将数据库记录映射为 Java 实体对象。

Mapper 不负责决定 HTTP 状态码，也不负责完整业务规则。

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

主要字段：

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

主要字段：

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

主要字段：

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
```

管理员分配时记录：

```text
from_status = CREATED
to_status = ASSIGNED
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
- `idx_ticket_assignee_status`：支持维修人员按 `assignee_id` 查询被分配工单，也为后续按维修人员和状态筛选做准备。
- `idx_ticket_status_created_at`：支持按状态和创建时间查询。
- `idx_ticket_log_ticket_time`：支持查询一张工单的状态变化历史。

列表查询使用：

```sql
ORDER BY created_at DESC, id DESC
```

`created_at DESC` 让较新的工单排在前面。

`id DESC` 用于在创建时间相同时提供稳定顺序，避免后续分页时同一条记录在相邻页面之间不稳定移动。

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

输入密码后进入 MySQL。

### 2. 执行表结构脚本

在 MySQL 客户端中执行：

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

如需重新初始化，应先使用独立测试数据库或手动清理旧演示数据，不要在包含正式数据的数据库中直接删除。

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

这种写法只对当前 PowerShell 窗口有效。

关闭该窗口后，环境变量不会继续保留。

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

启动项目：

```powershell
mvn spring-boot:run
```

启动成功后，服务默认运行在：

```text
http://localhost:8080
```

如果修改了代码，也可以先执行：

```powershell
mvn clean test
```

然后启动：

```powershell
mvn spring-boot:run
```

---

## API 接口

当前共实现五个接口：

| 方法 | URL | 功能 |
|---|---|---|
| `POST` | `/tickets` | 创建工单 |
| `GET` | `/tickets/{id}` | 按 ID 查询工单 |
| `GET` | `/tickets/mine` | 查询当前报修人创建的工单 |
| `PATCH` | `/tickets/{id}/assignment` | 管理员分配维修人员 |
| `GET` | `/tickets/assigned-to-me` | 维修人员查询被分配的工单 |

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

PowerShell 请求示例：

```powershell
curl.exe -i -X POST "http://localhost:8080/tickets" `
  -H "X-User-Id: 1" `
  -H "Content-Type: application/json" `
  -d '{\"title\":\"宿舍水管漏水\",\"description\":\"洗手池下方持续漏水\",\"location\":\"学生宿舍3栋201\",\"category\":\"PLUMBING\",\"priority\":\"HIGH\"}'
```

### 成功响应

成功时返回：

```text
201 Created
```

响应体示例：

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
  "createdAt": "2026-10-12T14:30:00"
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

### 工单存在

返回：

```text
200 OK
```

### 工单不存在

返回：

```text
404 Not Found
```

当前详情接口尚未进行用户身份和工单归属校验，知道工单 ID 的请求者可以查询该工单。

这属于当前版本限制，后续需要增加角色和数据归属判断。

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

### 查询成功

返回：

```text
200 OK
```

响应体示例：

```json
[
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
    "createdAt": "2026-10-12T14:30:00"
  }
]
```

没有符合条件的工单时返回：

```text
200 OK
```

```json
[]
```

空列表表示：

```text
用户存在，但目前没有符合查询条件的工单
```

因此不返回 `404 Not Found`。

该接口已经使用不同用户 ID 进行了真实数据隔离验证，只返回对应 `creator_id` 的工单。

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

其中：

- URL 中的 `{id}` 是需要分配的工单 ID。
- `X-User-Id` 是执行分配操作的管理员 ID。
- 请求体中的 `assigneeId` 是目标维修人员 ID。

PowerShell 示例：

```powershell
curl.exe -i -X PATCH "http://localhost:8080/tickets/1/assignment" `
  -H "X-User-Id: 4" `
  -H "Content-Type: application/json" `
  -d '{\"assigneeId\":3}'
```

### 成功响应

成功时返回：

```text
200 OK
```

响应体中的关键字段：

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
| `404 Not Found` | 工单、操作人或目标用户不存在 |
| `409 Conflict` | 工单状态不是 `CREATED`，或者并发更新失败 |

业务异常响应格式：

```json
{
  "message": "具体错误信息"
}
```

### 重复分配保护

真正执行更新时，SQL 不只按工单 ID 更新，还要求旧状态是 `CREATED`：

```sql
UPDATE ticket
SET assignee_id = #{assigneeId},
    status = 'ASSIGNED'
WHERE id = #{ticketId}
  AND status = 'CREATED'
```

即使 Service 已经提前查询过状态，更新语句仍保留状态条件，用于防止：

- 同一工单被重复分配。
- 两名管理员同时分配同一工单时都成功。
- 查询状态后、执行更新前，工单被其他请求修改。

两个并发请求分配同一张 `CREATED` 工单时，预期结果为：

```text
一个请求更新 1 行并成功
另一个请求更新 0 行并返回 409
```

数据库中最终只能：

- 成功分配一次。
- 保存一个 `assignee_id`。
- 写入一条 `CREATED → ASSIGNED` 日志。

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

### 查询成功

返回：

```text
200 OK
```

响应体示例：

```json
[
  {
    "id": 2,
    "creatorId": 2,
    "assigneeId": 3,
    "title": "教室投影仪无法启动",
    "description": "按下电源按钮后没有反应",
    "location": "明德楼201",
    "category": "EQUIPMENT",
    "priority": "HIGH",
    "status": "ASSIGNED",
    "createdAt": "2026-10-06T10:10:00"
  }
]
```

没有被分配工单时返回：

```text
200 OK
```

```json
[]
```

当前用户不存在或角色不是 `WORKER` 时返回：

```text
403 Forbidden
```

示例：

```json
{
  "message": "UserRole should be 'WORKER'"
}
```

该接口不接受客户端通过查询参数传入任意 `assigneeId`，而是直接使用请求头中的当前用户 ID：

```sql
WHERE assignee_id = #{assigneeId}
ORDER BY created_at DESC, id DESC
```

这样可以在当前开发期身份方案下，避免调用者通过普通查询参数选择其他维修人员 ID。

但是，由于 `X-User-Id` 仍然可以被客户端伪造，因此这只能实现业务层数据隔离，不能代替真正的身份认证。

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

创建日志内容：

```text
from_status = null
to_status = CREATED
operator_id = creatorId
```

如果日志插入失败，已经插入的工单也必须回滚。

数据库中不能留下：

```text
存在 ticket
但没有对应创建日志
```

该事务边界位于 Service 对外业务方法上。

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
9. 重新查询并返回更新后的工单。

如果状态日志写入失败，工单更新也必须回滚。

回滚后保持：

```text
status = CREATED
assignee_id = null
```

这样可以避免工单已经分配，但状态变化日志缺失。

---

## MyBatis 参数绑定

SQL 参数使用 MyBatis `#{}` 绑定：

```sql
WHERE creator_id = #{creatorId}
```

参数通过预编译方式传入，而不是直接拼接到 SQL 字符串中。

项目不使用 `${}` 拼接普通用户输入，避免 SQL 结构被输入内容改变。

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

该配置负责处理数据库下划线命名和 Java 驼峰命名之间的转换。

---

## 测试

在项目根目录执行：

```powershell
mvn test
```

当前共有 22 条测试：

- 15 条 `TicketController` MockMvc 测试。
- 4 条 Service 真实数据库集成测试。
- 2 条事务失败回滚测试。
- 1 条 Spring 上下文启动测试。

### Controller 测试

当前覆盖：

1. 合法创建请求返回 `201`。
2. 空标题返回 `400`。
3. 缺少 `X-User-Id` 返回 `400`。
4. 非法工单分类返回 `400`。
5. 工单详情存在时返回 `200`。
6. 工单详情不存在时返回 `404`。
7. 查询报修人个人工单列表返回 `200`。
8. 报修人个人工单列表为空时返回 `200 + []`。
9. 管理员成功分配工单时返回 `200`，并返回正确的 `ASSIGNED` 状态和 `assigneeId`。
10. 分配请求缺少 `assigneeId` 时返回 `400`。
11. 重复分配或状态冲突时返回 `409`。
12. 非管理员执行分配时返回 `403`。
13. 维修人员查询被分配工单时返回 `200` 和正确列表。
14. 维修人员没有被分配工单时返回 `200 + []`。
15. 非维修人员调用被分配工单接口时返回 `403`。

Controller 测试使用模拟的 Service，不连接真实数据库，主要验证：

- HTTP 方法和 URL。
- 请求头和请求体。
- 参数校验。
- HTTP 状态码。
- JSON 响应结构。
- Controller 是否正确调用 Service。

### Service 集成测试

Service 集成测试连接真实 MySQL，当前共有 4 条测试方法，覆盖：

1. 创建工单时同时插入工单和创建日志，并正确回填数据库生成的主键。
2. 管理员分配后，工单状态变为 `ASSIGNED`、`assignee_id` 正确保存，并写入状态变化日志。
3. 重复分配已经处于 `ASSIGNED` 状态的工单时操作被拒绝，并且不会写入第二条分配日志。
4. 两个维修人员存在不同工单时，只能查询到分配给自己的工单，并按 `created_at DESC, id DESC` 排序。

### 事务回滚测试

当前共有 2 条事务回滚测试：

1. 创建日志写入失败时，已经插入的工单整体回滚。
2. 分配日志写入失败时，工单状态和维修人员字段整体回滚。

分配回滚后应保持：

```text
status = CREATED
assignee_id = null
```

### 测试分层

Controller 测试、Service 集成测试和真实 HTTP 请求关注的范围不同，不能完全互相替代。

| 测试方式 | 主要验证内容 |
|---|---|
| Controller MockMvc | HTTP 参数、校验、状态码、JSON 和 Controller 调用 |
| Service 集成测试 | 业务规则、MyBatis、SQL 和真实数据库结果 |
| 事务回滚测试 | 多次数据库写入失败后的整体回滚 |
| 真实 HTTP 验证 | 应用启动后的完整请求链路和实际数据库结果 |

当前已对维修人员列表进行真实数据隔离验证：

- worker A 只能查询到 `assignee_id=A` 的工单。
- worker B 的工单不会出现在 worker A 的结果中。
- 空列表返回 `200 + []`。
- 非 `WORKER` 用户返回 `403`。

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
- [x] 完成分配接口真实 HTTP 的 `200`、`403`、`409` 验证
- [x] 完成分配接口 Controller MockMvc 测试
- [x] 实现 `GET /tickets/assigned-to-me`
- [x] 完成维修人员列表的 Controller MockMvc 测试
- [x] 完成维修人员列表的真实数据隔离验证
- [x] 完成 15 条 Controller MockMvc 测试
- [x] 完成 4 条 Service 真实数据库集成测试
- [x] 完成 2 条事务失败回滚测试
- [ ] 实现管理员查看全部工单
- [ ] 实现 `ASSIGNED → PROCESSING`
- [ ] 实现 `PROCESSING → RESOLVED`
- [ ] 实现 `RESOLVED → CLOSED`
- [ ] 实现其他非法状态变化处理
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
- 维修人员目前只能查看被分配的工单，尚未实现开始处理和标记解决。
- 状态流目前只实现了 `CREATED → ASSIGNED`。
- 列表接口没有分页、筛选和数量限制。
- 分配接口使用条件更新进行基础并发保护，但尚未实现版本号或完整的乐观锁机制。
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
- 复杂的组织架构和多租户系统。

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

1. 实现维修人员将工单从 `ASSIGNED` 改为 `PROCESSING`。
2. 实现维修人员将工单从 `PROCESSING` 改为 `RESOLVED`。
3. 实现报修人将工单从 `RESOLVED` 改为 `CLOSED`。
4. 为每次状态变化增加条件更新、状态日志和事务回滚测试。
5. 实现管理员查看全部工单。
6. 修复工单详情接口的越权查询问题。
7. 接入 Spring Security 和正式登录认证。
8. 增加分页、状态筛选、分类筛选和优先级筛选。
9. 增加统一业务错误码。
10. 完成项目部署和简历项目说明。

---

## 项目设计原则

当前项目遵循以下原则：

- Controller 只处理 HTTP 层职责。
- Service 负责业务规则和事务。
- Mapper 只负责数据库访问。
- 请求 DTO 与数据库实体分离。
- 客户端不能直接决定工单状态。
- 状态更新必须同时校验旧状态。
- 工单当前状态和状态变化日志必须保持一致。
- 列表无数据时返回空数组，不返回 `null` 或 `404`。
- 用户只能查询业务规则允许的数据。
- 测试数据使用唯一值，并避免污染演示数据。
- 已完成能力与计划能力在 README 中明确区分。