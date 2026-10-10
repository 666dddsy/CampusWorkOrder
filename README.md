# CampusWorkOrder 校园工单系统

## 项目简介

CampusWorkOrder 是一个基于 Spring Boot 与 MyBatis 实现的校园报修工单后端项目。

项目目标是实现从报修人创建工单，到管理员分配维修人员、维修人员处理工单、报修人确认关闭的完整业务流程，并通过 `ticket_log` 保存工单创建和状态变化记录。

当前版本已经完成：

- 报修人创建工单。
- 按工单 ID 查询详情。
- 报修人查看自己创建的工单列表。
- 请求参数校验和字段级错误响应。
- 创建工单与创建日志的事务一致性。
- Controller 切片测试和真实数据库事务集成测试。

管理员分配、维修人员处理、状态流转、正式登录认证和部署尚未实现。

详细需求见 [docs/mvp.md](docs/mvp.md)。

## 当前业务流程

计划中的工单状态流如下：

```text
CREATED → ASSIGNED → PROCESSING → RESOLVED → CLOSED
```

| 状态 | 含义 |
|---|---|
| `CREATED` | 报修人已经创建工单，等待管理员分配 |
| `ASSIGNED` | 管理员已经分配维修人员 |
| `PROCESSING` | 维修人员正在处理 |
| `RESOLVED` | 维修已经完成，等待报修人确认 |
| `CLOSED` | 报修人确认问题解决，工单关闭 |

状态不允许跨级变化，例如不能从 `CREATED` 直接变为 `RESOLVED`。

目前代码已经实现创建时自动写入 `CREATED` 状态，后续状态变更接口尚未实现。

## 用户角色

| 角色 | 计划支持的功能 | 当前实现情况 |
|---|---|---|
| `REQUESTER` | 创建工单、查看自己的工单、确认关闭 | 已实现创建和查询 |
| `WORKER` | 查看分配给自己的工单、开始处理、标记已解决 | 尚未实现 |
| `ADMIN` | 查看全部工单、分配维修人员 | 尚未实现 |

当前没有实现登录、Spring Security 或 JWT。

接口暂时通过请求头传递用户 ID：

```http
X-User-Id: 1
```

`X-User-Id` 只是开发阶段用于打通业务链路的身份占位方案。客户端可以伪造该请求头，因此它不属于真正的身份认证或权限控制。

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
│  │  │  ├─ service             # 业务逻辑和事务
│  │  │  ├─ mapper              # MyBatis Mapper 接口
│  │  │  ├─ entity              # 数据库实体
│  │  │  ├─ dto                 # 请求和响应对象
│  │  │  ├─ domain/enums        # 状态、分类、优先级和角色
│  │  │  └─ exception           # 全局异常处理
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

### `ticket`

保存工单当前内容和当前状态。

主要字段：

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

### `ticket_log`

保存工单创建以及后续状态变化记录。

主要字段：

- `ticket_id`
- `operator_id`
- `from_status`
- `to_status`
- `note`
- `created_at`

## 创建工单事务

创建一张工单需要同时完成两次数据库写入：

1. 向 `ticket` 插入工单。
2. 向 `ticket_log` 插入创建记录。

`TicketService.createTicket` 使用 `@Transactional` 保证两次写入属于同一个事务。

如果日志插入失败，已经插入的工单也必须回滚，不能在数据库中留下没有创建日志的不完整工单。

当前创建链路为：

```text
POST /tickets
→ TicketCreateRequest 参数校验
→ TicketController
→ TicketService.createTicket
→ TicketMapper.insert
→ TicketLogMapper.insert
→ 返回数据库生成的工单 ID
→ TicketService.selectById
→ TicketResponse
→ 201 Created
```

创建方法返回数据库生成的 ID，Controller 再通过 Service 查询完整工单，以获得数据库生成的创建时间等字段。

需要注意：`createTicket` 返回时，其事务已经结束。Controller 随后的 `selectById` 是另一次 Service 调用，不属于创建事务。

## 环境要求

- JDK 21
- Maven
- MySQL 8.0.16 或更高版本
- IntelliJ IDEA（可选）

确认命令可以正常执行：

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

`schema.sql` 会创建：

```text
campus_work_order
```

`seed.sql` 使用固定 ID 写入本地演示用户、工单和日志，适合在空的开发数据库中执行，不应直接用于正式环境，也不建议在已有相同 ID 的数据库中反复执行。

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

应用读取的数据库配置为：

```text
jdbc:mysql://localhost:3306/campus_work_order
```

## 运行测试

在项目根目录执行：

```bash
mvn test
```

当前测试代码包括：

- 8 条 `TicketController` MockMvc 测试。
- 1 条创建工单成功的真实数据库集成测试。
- 1 条创建日志失败时整体回滚的真实数据库集成测试。
- 1 条 Spring 上下文启动测试。

Controller 测试当前覆盖：

1. 合法创建请求返回 201。
2. 空标题返回 400。
3. 缺少 `X-User-Id` 返回 400。
4. 非法工单分类返回 400。
5. 工单详情存在时返回 200。
6. 工单详情不存在时返回 404。
7. 查询个人工单列表时返回 200。
8. 个人工单列表为空时返回 `200 + []`。

Controller 测试使用模拟的 Service，不连接真实数据库。

Service 集成测试连接真实 MySQL，用于验证实际写入、主键回填以及事务回滚。

如果测试时提示无法覆盖：

```text
target/classes/application.yml
```

通常是已经运行的应用仍然占用 `target` 目录。先停止应用，再重新执行测试。

## 启动项目

设置数据库环境变量后，在项目根目录运行：

```bash
mvn spring-boot:run
```

默认地址：

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

客户端不能指定以下字段：

- `id`
- `creatorId`
- `assigneeId`
- `status`
- `createdAt`

这些字段由请求头、Service 或数据库决定。

创建成功返回：

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

工单存在时返回：

```http
200 OK
```

工单不存在时返回：

```http
404 Not Found
```

当前详情接口还没有进行用户身份和工单归属校验。正式权限功能实现后，应根据当前登录用户的角色和工单归属判断是否允许访问。

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

接口根据请求头中的用户 ID 查询：

```sql
WHERE creator_id = #{creatorId}
ORDER BY created_at DESC, id DESC
```

查询成功返回：

```http
200 OK
```

响应示例：

```json
[
  {
    "id": 3,
    "creatorId": 1,
    "assigneeId": 3,
    "title": "校园网无法连接",
    "description": "电脑能够连接校园网，但无法访问互联网",
    "location": "图书馆三楼",
    "category": "NETWORK",
    "priority": "URGENT",
    "status": "PROCESSING",
    "createdAt": "2026-10-06T11:00:00"
  },
  {
    "id": 1,
    "creatorId": 1,
    "assigneeId": null,
    "title": "宿舍水龙头漏水",
    "description": "水龙头关闭后仍然持续滴水",
    "location": "东苑3栋201",
    "category": "PLUMBING",
    "priority": "MEDIUM",
    "status": "CREATED",
    "createdAt": "2026-10-06T10:00:00"
  }
]
```

用户没有创建任何工单时，返回：

```http
200 OK
```

```json
[]
```

空列表表示查询成功但没有符合条件的数据，因此不是 404。

## 参数校验

创建工单时执行以下校验：

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

字段校验失败时返回：

```http
400 Bad Request
```

示例：

```json
{
  "title": "title must not be blank"
}
```

DTO 校验在进入 Controller 方法体前执行。校验失败后由全局异常处理器返回 400，Service 不会执行，也不会向数据库写入工单。

缺少 `X-User-Id` 或枚举值无法解析时，目前使用 Spring MVC 默认的 400 响应。

## MyBatis 映射

项目开启：

```yaml
map-underscore-to-camel-case: true
```

因此数据库列可以自动映射到 Java 驼峰属性，例如：

```text
creator_id → creatorId
assignee_id → assigneeId
created_at → createdAt
updated_at → updatedAt
```

SQL 参数使用 MyBatis `#{}` 绑定，例如：

```sql
WHERE creator_id = #{creatorId}
```

参数通过预编译方式传入，而不是直接拼接到 SQL 字符串中。

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
- [x] 实现 `POST /tickets`
- [x] 实现 `GET /tickets/{id}`
- [x] 实现 `GET /tickets/mine`
- [x] 完成 8 条 Controller MockMvc 测试
- [x] 完成创建成功与事务回滚集成测试
- [x] 使用真实 HTTP 请求验证创建工单
- [ ] 完成个人工单列表的真实多用户隔离验证
- [ ] 实现管理员查看所有工单
- [ ] 实现管理员分配维修人员
- [ ] 实现维修人员查看被分配的工单
- [ ] 实现 `ASSIGNED → PROCESSING`
- [ ] 实现 `PROCESSING → RESOLVED`
- [ ] 实现 `RESOLVED → CLOSED`
- [ ] 实现非法状态变化处理
- [ ] 实现正式登录和权限认证
- [ ] 增加分页和筛选
- [ ] 完成部署

## 当前限制

当前版本存在以下明确限制：

- `X-User-Id` 可以被客户端伪造，不是真正认证。
- 工单详情接口尚未进行归属和角色权限校验。
- 尚未实现管理员和维修人员接口。
- 尚未实现状态流转。
- 列表接口没有分页，数据量增大后需要增加分页参数。
- 尚未实现并发状态更新保护。
- 尚未实现统一业务错误码。
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