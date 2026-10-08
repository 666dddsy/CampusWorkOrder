# CampusWorkOrder 校园工单系统

## 项目简介

CampusWorkOrder 是一个面向校园报修场景的后端项目。

报修人可以创建和查看工单，管理员负责分配维修人员，维修人员负责处理工单。系统会记录每次状态变化，使工单处理过程可以追踪。

本项目用于练习 Java 后端开发、数据库设计、事务处理、接口测试和项目部署。

## 核心业务流程

工单按照以下顺序流转：

```text
CREATED → ASSIGNED → PROCESSING → RESOLVED → CLOSED
```

- `CREATED`：报修人创建工单，等待分配。
- `ASSIGNED`：管理员已分配维修人员。
- `PROCESSING`：维修人员正在处理。
- `RESOLVED`：维修完成，等待报修人确认。
- `CLOSED`：报修人确认问题解决，工单关闭。

不允许跨级修改状态，例如不能从 `CREATED` 直接变为 `RESOLVED`。

详细需求见 [docs/mvp.md](docs/mvp.md)。

## 用户角色

| 角色 | 主要功能 |
|---|---|
| `REQUESTER` | 创建工单、查看自己的工单、确认关闭工单 |
| `WORKER` | 查看分配给自己的工单、开始处理、标记已解决 |
| `ADMIN` | 查看全部工单、分配维修人员 |

第一版暂不实现登录、JWT 和真正的权限认证。

## 计划技术栈

- Java 21
- Spring Boot
- Spring Web MVC
- Jakarta Validation
- MyBatis
- MySQL 8
- Maven
- JUnit 5
- Mockito
- MockMvc

## 项目结构

```text
CampusWorkOrder
├─ docs
│  └─ mvp.md          项目范围和业务规则
├─ sql
│  ├─ schema.sql      数据库及数据表结构
│  └─ seed.sql        演示数据和查询示例
├─ src
│  ├─ main
│  │  ├─ java         Java 源代码
│  │  └─ resources    配置文件和 Mapper XML
│  └─ test
│     └─ java         自动化测试
├─ pom.xml
└─ README.md
```

## 数据库设计

项目包含三张核心表：

- `app_user`：保存报修人、维修人员和管理员。
- `ticket`：保存工单当前信息和当前状态。
- `ticket_log`：保存工单创建、分配和状态变化记录。

创建工单时，工单数据和创建日志必须处于同一个事务中。任意一步失败时，两张表都不能留下不完整数据。

## 数据库初始化

环境要求：

- MySQL 8.0.16 或更高版本。
- 确保本地 MySQL 服务已经启动。
- 执行前检查 `schema.sql`，避免误删已有数据库。

依次执行：

```sql
SOURCE sql/schema.sql;
SOURCE sql/seed.sql;
```

也可以在 IntelliJ IDEA 的数据库控制台中依次运行：

1. `sql/schema.sql`
2. `sql/seed.sql`

`seed.sql` 只用于生成本地演示数据，不应用于正式环境。

## 环境变量

数据库账号和密码不写入 Git，通过环境变量提供。

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

## 启动与测试

当前项目正在搭建 Spring Boot 骨架，暂时不能完整启动。

Spring Boot 骨架完成后，计划使用以下命令运行测试：

```bash
mvn test
```

启动应用：

```bash
mvn spring-boot:run
```

应用默认访问地址计划为：

```text
http://localhost:8080
```

以上命令需要在实际运行通过后再次确认和更新。

## 当前进度

- [x] 确定 MVP 功能范围
- [x] 设计用户角色和工单状态流
- [x] 设计数据库表结构
- [x] 编写演示数据和查询 SQL
- [ ] 修正并重新验证数据库脚本
- [ ] 搭建 Spring Boot 工程
- [ ] 实现创建工单
- [ ] 实现工单详情查询
- [ ] 实现工单分配和状态流转
- [ ] 完成参数校验和异常处理
- [ ] 完成自动化测试
- [ ] 完成项目部署

## 第一版暂不实现

- 用户注册和登录
- Spring Security 和 JWT
- 图片或附件上传
- 短信、邮件和站内通知
- Redis 缓存
- 消息队列
- 微服务
- 前端页面
- AI 自动分类
- 多部门、多校区和多租户