# FD-004 Go Gateway 实施报告 R1

<!-- aiw-data: FD-004-implementation-r1.json -->

- Worker Session：`fd004-host-20261016-5de84e0f`；来源事件：`FD-004-000003-design-ready`。
- 比较基线：`fff50cc`（就绪 FD），业务基线 `aa92083`；代码提交至 `51da510`，本报告及 FD 勾选另行提交。
- 隔离坐标已核对：parent `main`、branch `feature/FD-004`、worktree `D:/03_projects/pg-eventbus/.wt/FD-004`。收据由 AIW 管理，未手工修改。

全部 1.1—1.6 及新增 1.3.1 完成：独立 Go module、环境配置、连接池/SQL、自有 HTTP DTO、9 个入口、请求验证/脱敏错误、进程退出、Java gateway 移除、根 Maven 调整及当前文档迁移。既有 Java/Go/Python 客户端、CLI、SQL 和历史归档均未修改。

| 验收 | 实现及静态依据 |
|---|---|
| AC-01 | gateway/go.mod 无 replace 或项目依赖；net/http + pgxpool，无 os/exec/JVM；根 POM 只保留两个 Java 客户端 |
| AC-02 | 对照旧控制器与结果模型：9 个 POST、成功 message/OK、camelCase、int64、requestId/error null、空事件 []、nullable 广播/锁字段；time.Time 输出 RFC3339Nano |
| AC-03 | store 中参数绑定/pgx.Identifier；poll 锁失败短路、默认 limit、无释放；commit 保留布尔结果与请求 ID；recover 初始化位点；维护 allowlist、future=6、cleanup=10000 |
| AC-04 | 配置原生变量逐项优先、旧别名/JDBC 前缀、显式空密码覆盖、正 int32/端口/期限检查；1 MiB JSON、类型/必填校验、不暴露原始驱动错误；连接池、请求 context、启动 10s/退出 10s |
| AC-05 | 根/模块 README 与配置、模型、客户端指南及文档索引更新，说明只读环境变量、Spring 文件迁移、失败信封变化、无认证及 SQL 限制；旧当前 gateway 源码链接已替换 |
| AC-06 | 两次离线编译成功及静态 diff 核对；第二次针对 1.3.1 修改后代码。独立评审及交付尚待后续事件，未预先声称通过 |

静态核对 Java PgEventBusHttpClient 后发现其默认 ObjectMapper 序列化数值 Instant，新增 1.3.1 保留 recover 输入兼容：ISO 字符串与数值 epoch seconds 共存，不经 float64；长度/指数/范围限制在解析前阻止超大数运算。只读 javap 检查本地 Jackson 2.19.2 InstantDeserializer 的 decimal seconds/nanos 路径；未运行 Java/Go 业务程序。数值输入限 32 字符、无科学计数法、ISO 0000—9999 年，截取至纳秒，文档已披露。

实际检查：

- `go version`：本机 go1.25.1 windows/amd64；本地 pgx 及传递依赖可用，go.sum 沿用 cli 已有同版本校验和（保留其额外条目，不影响独立构建）。
- gateway 目录：`GOTOOLCHAIN=local CGO_ENABLED=0 GOPROXY=off GOSUMDB=off go build -mod=readonly -o ../.cache/compile/pg-eventbus-gateway.exe .`，两次退出码均 0；第一次在数值时间修正之前，第二次在修正之后，未运行输出程序。
- `git diff --check` 与 `git diff --check fff50cc`：通过；检查实际 diff、SQL 定义、客户端 DTO/序列化和当前文档链接，未触及范围外实现。
- 每个 Work Item 单独提交；一次 git add 在 gateway 子目录使用根相对路径而失败，已回到 FD 根目录按路径提交，不丢失变更。预检时临时 Session 文件曾因 PowerShell 位置参数误写在根目录，核对内容后删除并以显式 LiteralPath/Value 写入 ignored .ai，worktree 随后正常建立，无持续阻断。

未运行：测试、HTTP/数据库/应用运行、最终构建、formatter、linter/vet、跨平台运行、Maven 客户端重编译、网络下载、推送或部署。当前没有持续阻断记录，不需要技能/模板改动；PowerShell 使用显式参数为可复用操作经验，不扩大本 FD。

剩余风险：编译和静态检查不能证明 PostgreSQL 实际调用、并发/锁行为、HTTP 客户端互通、信号/跨平台退出或生产配置有效。旧 JDBC 特有参数没有转换；使用 Spring 文件、旧错误页字段、超 1 MiB 请求或数值时间扩展范围的调用方需要迁移。没有新增认证；原有清理 SQL 边界和保留策略风险不在本 FD 修复范围。
