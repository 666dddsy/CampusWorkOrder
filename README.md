# CampusWorkOrder 校园工单系统

## 项目简介

CampusWorkOrder 是一个基于 Spring Boot、MyBatis 和 MySQL 实现的校园报修工单后端项目。

项目目标是实现完整的校园报修流程：

```text
报修人创建工单
→ 管理员分配维修人员
→ 维修人员开始处理
→ 维修人员标记已解决
→ 报修人确认关闭
```

系统使用 `ticket` 保存工单当前状态，使用 `ticket_log` 保存工单创建及状态变化记录。

当前版本已经实现：

- 报修人创建工单。
- 按工单 ID 查询详情。
- 报修人查询自己创建的工单列表。
- 管理员为工单分配维修人员。
- 报修人与管理员的基础角色校验。
- 请求参数校验和字段级错误响应。
- 业务异常的统一处理。
- 创建工单与写入创建日志的事务一致性。
- 分配工单与写入状态日志的事务一致性。
- 基于条件更新的重复分配和并发状态保护。
- Controller 切片测试。
- Service 真实数据库集成测试。
- 事务失败回滚测试。
- 真实 HTTP 请求验证。

维修人员处理工单、报修人确认关闭、正式登录认证和部署尚未实现。

详细需求见 [docs/mvp.md](docs/mvp.md)。

## 业务状态流

完整工单状态设计如下：

```text
CREATED → ASSIGNED → PROCESSING → RESOLVED → CLOSED
```

| 状态 | 含义 | 当前实现 |
|---|---|---|
| `CREATED` | 报修人已经创建工单，等待管理员分配 | 已实现 |
| `ASSIGNED` | 管理员已经分配维修人员 | 已实现 |
| `PROCESSING` | 维修人员正在处理 | 尚未实现 |
| `RESOLVED` | 维修完成，等待报修人确认 | 尚未实现 |
| `CLOSED` | 报修人确认问题解决，工单关闭 | 尚未实现 |

当前已实现：

```text
CREATED → ASSIGNED
```

状态不允许重复操作或跨级变化。例如，已经处于 `ASSIGNED` 状态的工单不能再次分配。

## 用户角色

| 角色 | 计划支持的功能 | 当前实现情况 |
|---|---|---|
| `REQUESTER` | 创建工单、查看自己的工单、确认关闭 | 已实现创建和个人列表查询 |
| `WORKER` | 查看被分配的工单、开始处理、标记已解决 | 已参与分配校验，业务接口尚未实现 |
| `ADMIN` | 查看全部工单、分配维修人员 | 已实现分配维修人员 |

当前没有实现 Spring Security、JWT 或正式登录功能。

接口暂时通过请求头传递用户 ID：

```http
X-User-Id: 1
```

分配工单时，Service 会根据请求头中的用户 ID 查询数据库角色，并要求操作人角色为 `ADMIN`。

需要注意：`X-User-Id` 可以被客户端伪造，只是开发阶段用于打通业务链路的身份占位方案，不属于真正的身份认证。

## 技术栈

- Java 21
- Spring Boot 4.0.8
- Spring Web MVC
- Jakarta Bean Validation
- MyBatis 4.0.1
- MySQL 8
- Maven
- JUnit 5
- Mockito
- MockMvc
- Spring Transaction

## 项目结构

```text
CampusWorkOrder
├─ docs
│  └─ mvp.md                    # MVP 范围和业务规则
├─ sql
│  ├─ schema.sql                # 数据库与表结构
│  └─ seed.sql                  # 本地演示数据
├─ src
│  ├─ main
│  │  ├─ java/com/example/campusworkorder
│  │  │  ├─ controller          # HTTP 接口
│  │  │  ├─ service             # 业务逻辑和事务边界
│  │  │  ├─ mapper              # MyBatis Mapper 接口
│  │  │  ├─ entity              # 数据库实体
│  │  │  ├─ dto                 # 请求和响应对象
│  │  │  ├─ domain/enums        # 状态、分类、优先级和角色
│  │  │  └─ exception           # 业务异常和全局异常处理
│  │  └─ resources
│  │     ├─ mapper              # MyBatis XML
│  │     └─ application.yml
│  └─ test                      # Controller 与 Service 测试
├─ pom.xml
└─ README.md
```

## 数据库设计

项目包含三张核心表。

### `app_user`

保存报修人、维修人员和管理员。

主要字段：

- `id`
- `username`
- `password_hash`
- `role`
- `created_at`

支持的角色：

```text
REQUESTER
WORKER
ADMIN
```

### `ticket`

保存工单内容、负责人和当前状态。

主要字段：

- `id`
- `creator_id`
- `assignee_id`
- `title`
- `description`
- `location`
- `category`
- `priority`
- `status`
- `created_at`
- `updated_at`

当前索引包括：

- `idx_ticket_status_created_at`
- `idx_ticket_assignee_status`
- `idx_ticket_creator_id`

其中 `idx_ticket_creator_id` 用于支持报修人查询自己的工单列表。

### `ticket_log`

保存工单创建和状态变化记录。

主要字段：

- `id`
- `ticket_id`
- `operator_id`
- `from_status`
- `to_status`
- `note`
- `created_at`

工单创建时记录：

```text
null → CREATED
```

管理员分配维修人员时记录：

```text
CREATED → ASSIGNED
```

## 核心业务实现

### 创建工单事务

创建一张工单需要完成两次数据库写入：

1. 向 `ticket` 插入工单。
2. 向 `ticket_log` 插入创建日志。

`TicketService.createTicket` 使用 `@Transactional` 保证两次写入属于同一个事务。

如果日志插入失败，已经插入的工单也会回滚，数据库中不会留下没有创建日志的不完整工单。

业务链路：

```text
POST /tickets
→ TicketCreateRequest 参数校验
→ TicketController
→ TicketService.createTicket
→ TicketMapper.insert
→ 数据库主键回填
→ TicketLogMapper.insert
→ 返回生成的工单 ID
→ TicketService.selectById
→ TicketResponse
→ 201 Created
```

创建方法返回数据库生成的工单 ID。Controller 随后重新查询完整工单，以获得数据库生成的创建时间等字段。

`createTicket` 返回时创建事务已经结束，Controller 随后的 `selectById` 是另一次 Service 调用，不属于同一个事务。

### 管理员分配工单事务

管理员分配维修人员时，系统依次执行：

1. 根据 `X-User-Id` 查询操作人角色。
2. 确认操作人角色为 `ADMIN`。
3. 根据请求体中的 `assigneeId` 查询被分配用户的角色。
4. 确认被分配用户角色为 `WORKER`。
5. 查询工单是否存在。
6. 确认工单当前状态为 `CREATED`。
7. 将工单更新为 `ASSIGNED` 并写入 `assignee_id`。
8. 向 `ticket_log` 写入状态变化记录。
9. 返回更新后的完整工单。

`TicketService.assignTicket` 使用 `@Transactional`。

如果状态日志写入失败，工单的状态和维修人员字段都会回滚，保持：

```text
status = CREATED
assignee_id = null
```

### 条件更新与重复分配保护

分配操作使用以下条件更新：

```sql
UPDATE ticket
SET status = 'ASSIGNED',
    assignee_id = #{assigneeId}
WHERE id = #{ticketId}
  AND status = 'CREATED'
```

即使业务代码已经提前查询过工单状态，真正更新时仍然要求数据库中的状态为 `CREATED`。

这样可以防止：

- 同一工单被重复分配。
- 两名管理员同时分配同一工单时，两次操作都成功。
- 查询和更新之间工单状态发生变化。

更新影响行数不是 `1` 时，系统返回 `409 Conflict`。

## 环境要求

- JDK 21
- Maven
- MySQL 8.0.16 或更高版本
- IntelliJ IDEA（可选）

检查环境：

```bash
java -version
mvn -version
```

## 数据库初始化

确保 MySQL 服务已经启动。

在 MySQL 客户端中依次执行：

```sql
SOURCE sql/schema.sql;
SOURCE sql/seed.sql;
```

也可以在 IntelliJ IDEA 数据库控制台中依次运行：

1. `sql/schema.sql`
2. `sql/seed.sql`

数据库名称：

```text
campus_work_order
```

`seed.sql` 使用固定 ID 写入本地演示用户、工单和日志，适合在空的开发数据库中执行。

不应直接用于正式环境，也不建议在已经存在相同 ID 的数据库中反复执行。

## 数据库环境变量

数据库用户名和密码不写入 Git，通过环境变量传入。

### PowerShell

```powershell
$env:DB_USER="你的数据库用户名"
$env:DB_PASSWORD="你的数据库密码"
```

### Git Bash

```bash
export DB_USER="你的数据库用户名"
export DB_PASSWORD="你的数据库密码"
```

数据库连接地址：

```text
jdbc:mysql://localhost:3306/campus_work_order
```

## 启动项目

设置数据库环境变量后，在项目根目录执行：

```bash
mvn spring-boot:run
```

默认访问地址：

```text
http://localhost:8080
```

## 已实现接口

### 1. 创建工单

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
  "location": "滨江宿舍3栋412",
  "category": "PLUMBING",
  "priority": "MEDIUM"
}
```

客户端不能指定：

- `id`
- `creatorId`
- `assigneeId`
- `status`
- `createdAt`

这些字段分别由请求头、Service 或数据库决定。

创建成功：

```http
201 Created
```

响应示例：

```json
{
  "id": 101,
  "creatorId": 1,
  "assigneeId": null,
  "title": "宿舍水管漏水",
  "description": "洗手池下方持续漏水",
  "location": "滨江宿舍3栋412",
  "category": "PLUMBING",
  "priority": "MEDIUM",
  "status": "CREATED",
  "createdAt": "2026-10-09T10:30:00"
}
```

PowerShell 示例：

```powershell
curl.exe -i -X POST "http://localhost:8080/tickets" `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1" `
  -d '{"title":"宿舍水管漏水","description":"洗手池下方持续漏水","location":"滨江宿舍3栋412","category":"PLUMBING","priority":"MEDIUM"}'
```

### 2. 按 ID 查询工单

```http
GET /tickets/{id}
```

PowerShell 示例：

```powershell
curl.exe -i "http://localhost:8080/tickets/1"
```

工单存在：

```http
200 OK
```

工单不存在：

```http
404 Not Found
```

当前详情接口还没有进行用户身份和工单归属校验。

### 3. 查询自己创建的工单

```http
GET /tickets/mine
X-User-Id: 1
```

PowerShell 示例：

```powershell
curl.exe -i "http://localhost:8080/tickets/mine" `
  -H "X-User-Id: 1"
```

查询条件：

```sql
WHERE creator_id = #{creatorId}
ORDER BY created_at DESC, id DESC
```

`created_at DESC` 让较新的工单排在前面，`id DESC` 用于在创建时间相同时保持稳定顺序。

查询成功：

```http
200 OK
```

响应示例：

```json
[
  {
    "id": 13,
    "creatorId": 1,
    "assigneeId": 3,
    "title": "校园网无法连接",
    "description": "电脑能够连接校园网，但无法访问互联网",
    "location": "图书馆三楼",
    "category": "NETWORK",
    "priority": "URGENT",
    "status": "ASSIGNED",
    "createdAt": "2026-10-11T21:30:00"
  }
]
```

没有符合条件的工单时返回：

```http
200 OK
```

```json
[]
```

空列表表示查询成功但没有数据，因此不是 `404 Not Found`。

该接口已经使用不同用户 ID 进行了真实查询验证，只返回对应 `creator_id` 的工单。

### 4. 管理员分配维修人员

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
- `X-User-Id` 是执行操作的管理员 ID。
- `assigneeId` 是被分配的维修人员 ID。

PowerShell 示例：

```powershell
$body = @{
    assigneeId = 3
} | ConvertTo-Json -Compress

Invoke-WebRequest `
    -Uri "http://localhost:8080/tickets/13/assignment" `
    -Method Patch `
    -Headers @{ "X-User-Id" = "4" } `
    -ContentType "application/json" `
    -Body $body
```

分配成功：

```http
200 OK
```

响应中的关键字段：

```json
{
  "id": 13,
  "assigneeId": 3,
  "status": "ASSIGNED"
}
```

可能的响应：

| 状态码 | 场景 |
|---|---|
| `200 OK` | 分配成功 |
| `400 Bad Request` | 缺少 `assigneeId`，或者目标用户不是 `WORKER` |
| `403 Forbidden` | 操作人不是 `ADMIN` |
| `404 Not Found` | 工单不存在 |
| `409 Conflict` | 工单已经被操作，或者更新前状态发生变化 |

该接口已经通过真实 HTTP 请求验证：

- 管理员首次分配返回 `200 OK`。
- 重复分配同一工单返回 `409 Conflict`。
- 非管理员尝试分配返回 `403 Forbidden`。

## 参数校验

### 创建工单

| 字段 | 规则 |
|---|---|
| `title` | 不能为空，长度不超过 100 |
| `description` | 不能为空，长度不超过 1000 |
| `location` | 不能为空，长度不超过 100 |
| `category` | 不能为空，且必须是合法枚举值 |
| `priority` | 不能为空，且必须是合法枚举值 |

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

### 分配工单

| 字段 | 规则 |
|---|---|
| `assigneeId` | 不能为空，且对应用户必须是 `WORKER` |

DTO 校验在进入 Controller 方法体前执行。

校验失败后由全局异常处理器返回 `400 Bad Request`，Service 不会执行，也不会修改数据库。

字段校验失败示例：

```json
{
  "assigneeId": "assigneeId 不能为Null"
}
```

业务异常示例：

```json
{
  "message": "The ticket has been operated"
}
```

## MyBatis 映射

项目开启：

```yaml
map-underscore-to-camel-case: true
```

数据库下划线列名可以自动映射到 Java 驼峰属性：

```text
creator_id → creatorId
assignee_id → assigneeId
created_at → createdAt
updated_at → updatedAt
```

SQL 参数使用 MyBatis `#{}` 绑定：

```sql
WHERE creator_id = #{creatorId}
```

参数通过预编译方式传入，而不是直接拼接到 SQL 字符串中。

## 测试

在项目根目录执行：

```bash
mvn test
```

当前共有 14 条测试：

- 8 条 `TicketController` MockMvc 测试。
- 3 条 Service 真实数据库集成测试。
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
7. 查询个人工单列表返回 `200`。
8. 个人工单列表为空时返回 `200 + []`。

Controller 测试使用模拟的 Service，不连接真实数据库。

管理员分配接口的 MockMvc 切片测试尚待补充。

### Service 集成测试

Service 集成测试连接真实 MySQL，当前覆盖：

1. 创建工单时同时插入工单与创建日志。
2. 数据库生成的工单主键能够正确回填。
3. 管理员分配后状态变为 `ASSIGNED`。
4. 分配后 `assignee_id` 正确保存。
5. 分配成功后写入状态变化日志。
6. 重复分配被拒绝。
7. 重复操作不会生成第二条分配日志。

### 事务回滚测试

当前覆盖：

1. 创建日志写入失败时，已经插入的工单回滚。
2. 分配日志写入失败时，工单状态和维修人员字段回滚。

分配回滚后应保持：

```text
status = CREATED
assignee_id = null
```

如果测试时提示无法覆盖：

```text
target/classes/application.yml
```

通常是正在运行的应用占用了 `target` 目录。停止应用后重新运行测试。

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
- [x] 完成个人工单列表的真实多用户隔离验证
- [x] 实现 `PATCH /tickets/{id}/assignment`
- [x] 实现管理员与维修人员角色校验
- [x] 实现 `CREATED → ASSIGNED`
- [x] 实现重复分配冲突处理
- [x] 实现分配成功日志
- [x] 实现分配事务及失败回滚
- [x] 完成真实 HTTP 的 `200`、`403`、`409` 验证
- [x] 完成 8 条 Controller MockMvc 测试
- [x] 完成 5 条 Service 集成及回滚测试
- [ ] 补充分配接口的 Controller MockMvc 测试
- [ ] 实现管理员查看全部工单
- [ ] 实现维修人员查看被分配的工单
- [ ] 实现 `ASSIGNED → PROCESSING`
- [ ] 实现 `PROCESSING → RESOLVED`
- [ ] 实现 `RESOLVED → CLOSED`
- [ ] 实现其他非法状态变化处理
- [ ] 实现正式登录和权限认证
- [ ] 增加分页和筛选
- [ ] 完成部署

## 当前限制

当前版本存在以下明确限制：

- `X-User-Id` 可以被客户端伪造，不是真正的身份认证。
- 创建工单接口还没有校验请求人是否为 `REQUESTER`。
- 工单详情接口尚未进行归属和角色权限校验。
- 尚未实现管理员查看全部工单。
- 尚未实现维修人员业务接口。
- 状态流目前只实现了 `CREATED → ASSIGNED`。
- 列表接口没有分页。
- 分配接口使用条件更新进行基础并发保护，但尚未实现版本号或完整的并发控制方案。
- 尚未实现统一的业务错误码。
- 分配接口的 Controller MockMvc 测试尚未补充。
- 尚未进行生产环境部署。

这些内容属于后续计划，不能作为已经完成的项目能力写入简历。

## 第一版暂不实现

- 用户注册
- 图片或附件上传
- 短信、邮件和站内通知
- Redis 缓存
- 消息队列
- 微服务拆分
- 前端页面
- AI 自动分类
- 多部门、多校区和多租户