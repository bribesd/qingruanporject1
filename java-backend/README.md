# 智能企业知识库管理与问答平台（Java 后端）

这是原 Node.js（Express + SQLite）后端的 **Java / Spring Boot 版**，与原版保持
**1:1 的 API 兼容**：所有接口路径、请求/响应字段、状态码、中文错误文案完全一致，
前端（React，`../frontend`）无需任何改动即可对接。

## 技术栈

- Java 17 + Spring Boot 3.3
- Spring Web（REST）、Spring JDBC（`JdbcTemplate`）
- MySQL（InnoDB + utf8mb4）
- JWT（jjwt 0.12，HS256）+ BCrypt（spring-security-crypto）

## 前置条件

- JDK 17 或更高
- Maven 3.6+
- 可连接的 MySQL 8（本机默认 `localhost:3306`）

## 配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/enterprise_kb?...
    username: root     # 改为你的 MySQL 账号
    password: root     # 改为你的 MySQL 密码
app:
  jwt:
    secret: ${JWT_SECRET:...}   # 建议用环境变量覆盖为强随机密钥（≥32 字符）
```

- 数据库 `enterprise_kb` 会在首次启动时自动创建（连接串含 `createDatabaseIfNotExist=true`，
  需要账号具备建库权限；否则请先手动 `CREATE DATABASE enterprise_kb DEFAULT CHARSET utf8mb4;`）。
- 表结构（`resources/sql/schema.sql`）与种子数据会在启动时自动初始化，幂等可重复执行。

## 启动

```bash
cd java-backend
mvn spring-boot:run
```

服务监听 `3001` 端口（与原 Node 版一致）。

## 默认账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 超级管理员 |
| lisi / zhaoliu | test123 | 管理员 |
| testuser / zhangsan / wangwu | test123 | 普通用户 |

## API 说明

接口与原版完全一致，鉴权方式：`Authorization: Bearer <token>`。

- `POST /api/auth/login` — 登录（返回 `{ token, user }`）
- `GET  /api/health` — 健康检查
- `GET  /api/dashboard/summary` — 统计看板
- `GET  /api/roles` — 角色列表
- `GET/POST/PUT/DELETE /api/users[/:id]` — 用户管理（增删改需管理员）
- `GET/POST/PUT/DELETE /api/categories[/:id]` — 分类管理
- `GET/POST/PUT/DELETE /api/knowledge[/:id]` — 知识库管理
- `GET/POST /api/questions`、`GET /:id`、`POST /:id/answers`、`PATCH /:id/status` — 问答管理
- `GET /api/audit-logs` — 审计日志

列表接口通过 `X-Total-Count` 响应头返回总数，支持 `?limit=&offset=` 分页。

## 目录结构

```
src/main/java/com/enterprise/kb/
├── KnowledgeBaseApplication.java   # 启动类
├── config/        # CORS、数据初始化
├── security/      # JWT 认证过滤器、管理员拦截器
├── exception/     # 业务异常与全局异常处理
├── util/          # 分页、审计日志、JDBC/字符串辅助
├── controller/    # REST 控制器
└── service/       # 业务逻辑
```

## 与原 Node 版的对照

| Node 文件（src/） | Java 对应 |
|---|---|
| server.js | KnowledgeBaseApplication + GlobalExceptionHandler |
| config/db.js、db/schema.js | resources/sql/schema.sql + application.yml |
| initDb.js、seedUsers.js | config/DataInitializer.java |
| middleware/authMiddleware.js | security/JwtAuthFilter.java |
| middleware/requireAdmin.js | security/RequireAdminInterceptor.java |
| utils/auth.js | security/JwtUtil.java |
| utils/logAction.js | util/AuditLogger.java |
| utils/pagination.js | util/Pagination.java |
| routes/*.js | controller/* + service/* |
