# 校园工单系统 MVP

## 1. 项目目标

为校园报修场景提供一套完整的工单流转流程，使报修人能够提交问题，管理员能够分配维修人员，维修人员能够处理工单，并保留每次状态变化记录。

## 2. 用户角色

| 角色 | 第一版允许的操作 |
|---|---|
| 报修人 | 创建工单、查看自己创建的工单、查看工单详情、确认维修完成并关闭工单 |
| 维修人员 | 查看分配给自己的工单、开始处理工单、将处理完成的工单标记为已解决 |
| 管理员 | 查看所有工单、将待分配工单分配给维修人员 |

第一版只定义角色的业务职责，暂不实现登录、Token 和真正的权限认证。

## 3. 工单状态

| 状态 | 含义 | 允许的下一状态 | 操作人 |
|---|---|---|---|
| CREATED | 报修人刚提交，尚未分配 | ASSIGNED | 管理员 |
| ASSIGNED | 已经分配维修人员，尚未开始处理 | PROCESSING | 被分配的维修人员 |
| PROCESSING | 维修人员正在处理 | RESOLVED | 被分配的维修人员 |
| RESOLVED | 维修人员已完成处理，等待确认 | CLOSED | 报修人 |
| CLOSED | 工单已经结束 | 无 | 无 |

状态不能跨级变化。例如 `CREATED` 不能直接变成 `RESOLVED`。

每次状态变化都必须在 `ticket_log` 中保存操作人、原状态、新状态、备注和操作时间。

## 4. 第一版功能

### 报修人

1. 创建工单。
2. 查看自己创建的工单列表。
3. 查看自己创建的工单详情。
4. 将自己的 `RESOLVED` 工单关闭。

### 管理员

1. 查看所有工单。
2. 查看工单详情。
3. 为 `CREATED` 工单分配维修人员。

### 维修人员

1. 查看分配给自己的工单。
2. 查看工单详情。
3. 将 `ASSIGNED` 工单更新为 `PROCESSING`。
4. 将 `PROCESSING` 工单更新为 `RESOLVED`。

## 5. 第一版暂不实现

- 用户注册和登录。
- Spring Security、JWT 和真正的权限认证。
- 图片或附件上传。
- 短信、邮件和站内通知。
- Redis 缓存。
- AI 自动分类或问答。
- 前端页面。
- 工单重新打开、撤销和复杂审批。
- 多部门、多校区和多租户。

## 6. MVP 完成标准

1. 工单能够从创建一直流转到关闭。
2. 非法状态变化会被拒绝。
3. 工单当前状态保存在 `ticket` 表。
4. 每次状态变化保存在 `ticket_log` 表。
5. 三种角色的查询范围和操作范围清楚。
6. 核心接口有测试。
7. 其他人能够根据 README 创建数据库并启动项目。


## 第一条端到端功能：创建工单

### 接口

- 方法：POST
- 路径：/tickets
- Content-Type：application/json
- 用途：报修人提交一张新工单

### 请求体

```json
{
  "title": "宿舍水管漏水",
  "description": "洗手池下方持续漏水，需要维修",
  "location": "滨江宿舍 3 栋 412",
  "category": "PLUMBING",
  "priority": "MEDIUM"
}
```
创建人 id 不由请求体传入。正式实现登录后，应从当前登录用户信息中取得，防止用户冒充其他创建人。

## 字段校验
- title：不能为空，长度不超过 100。
- description：不能为空，长度不超过 1000。
- location：不能为空，长度不超过 100。
- category：只能是预先规定的分类,明确为 PLUMBING/ ELECTRICAL / NETWORK / EQUIPMENT / OTHER
- priority：只能是 LOW、MEDIUM、HIGH、URGENT。
- 客户端不能指定 status、assigneeId、createdAt。

## 成功响应

状态码：`201 Created`
```json
{
  "id": 101,
  "creatorId": 12,
  "assigneeId": null,
  "title": "宿舍水管漏水",
  "description": "洗手池下方持续漏水，需要维修",
  "location": "滨江宿舍 3 栋 412",
  "category": "PLUMBING",
  "priority": "MEDIUM",
  "status": "CREATED",
  "createdAt": "2026-10-07T14:30:00"
}
```
## 校验失败
状态码：`400 Bad Request`
```json
{
  "title": "title must not be blank",
  "location": "location must not be blank"
}
```
校验失败时，不得插入 `ticket `或 `ticket_log `数据。

涉及的数据表
1. `app_user`
    - 读取当前报修人的用户 id。
    - 不修改用户数据。
2. `ticket`
    - 插入新工单。
    - 初始状态固定为 CREATED。
    - assignee_id 初始为空。
3. `ticket_log`
    - 插入创建记录。
    - from_status 为空。
    - to_status 为 CREATED。
    - 操作人为当前报修人。

创建工单和创建日志必须属于同一个事务。其中任意一步失败，两张表都不能留下不完整数据。

### 数据流
POST /tickets  
→ TicketController  
→ TicketCreateRequest  
→ TicketService  
→ TicketMapper  
→ ticket + ticket_log  
→ TicketResponse  
→ 201 JSON

### 验收标准
- 合法请求返回 `201`。
- 返回结果包含数据库生成的工单 id。
- 工单状态自动设为 CREATED。
- 处理人初始为空。
- `ticket` 中新增一行。
- `ticket_log` 中新增一条创建记录。
- 非法请求返回 `400，Service` 不应执行。
- `ticket` 或 `ticket_log` 任一步失败时，整个事务回滚。