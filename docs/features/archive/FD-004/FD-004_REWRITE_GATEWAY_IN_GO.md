# FD-004: Rewrite gateway in Go

**Status:** Complete
**Revision:** 7
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户要求使用 Go 重写 gateway 并自动完成 FD 生命周期。当前 gateway 是独立 Java/Spring Boot/JDBC 程序，9 个 HTTP POST 路由依赖 PostgreSQL 既有函数。目标是移除网关运行时的 JVM 依赖，同时保持各语言 HTTP 客户端的业务契约。

## Options and decision

选项 A：保留 Java 并增加 Go 代理，仍需要 JVM，不能满足重写目的。选项 B：复用 CLI internal 包或直连客户端，会破坏模块独立性且引入 CLI 的数值时间编码或外部 psql。选择 C：独立 Go module、net/http 与 pgxpool，网关自有 DTO/SQL，不修改数据库函数及客户端。依赖版本沿用本机已有 pgx v5.7.6，不联网更新。

## Solution

gateway/go.mod 使用 Go 1.25；main 负责启动、信号取消与有界优雅退出；internal/config 负责环境配置，internal/store 负责并发安全连接池与 SQL，internal/api 负责路由、请求验证和 JSON 信封。

保留全部 POST 路由、camelCase DTO、64 位 ID、空事件数组、nullable 字段、原成功 message、code=OK 和 requestId=null。HTTP 时间使用 RFC3339Nano 字符串，兼容现有 HTTP 客户端，不能复制 CLI 的 epoch 数值编码。poll 先拿锁，失败时不查询；成功不释放锁；commit 保留数据库布尔结果且不释放；recover 保留初始化位点；maintenance/future/drop/cleanup 分别调用原函数，future=6、cleanup=10000。schema 使用 pgx.Identifier 引用，所有数据参数绑定。

原生配置使用 PGEVENTBUS_DATABASE_URL/USER/PASSWORD、PGEVENTBUS_SCHEMA/BATCH_SIZE/LOCK_TTL_SECONDS、PGEVENTBUS_LISTEN_ADDR（默认 :8080）、PGEVENTBUS_REQUEST_TIMEOUT（默认 30s）。保留 Spring 环境变量为低优先级别名：SPRING_DATASOURCE_URL/USERNAME/PASSWORD、BUS_SCHEMA/BATCH_SIZE/LOCK_TTL_SECONDS、SERVER_PORT；旧 URL 别名允许剥离 jdbc:，原生 URL 必须为带主机 PostgreSQL URL。逐字段显式原生环境变量优先，包括空值；凭证覆盖 URL。只提供 -h/--help，不接受其它参数，不读取 Spring 文件或 TOML，迁移步骤明确记录。缺少 URL、非法端口、非正 int32 批大小/TTL、无效超时均启动失败，错误不含连接字符串或凭证。

HTTP 拒绝缺失/空白必填字段、非法 JSON/类型/越界整数、缺失/null payload、非法 recover 时间；limit 缺失/null/非正数用默认；keepMonths 保留有符号 int32 行为。忽略未知 JSON 属性以匹配原接口。JSON 请求上限 1 MiB，要求 application/json（允许参数及 application/*+json）。新错误响应统一为失败信封：400/413/415/500/504，不承诺复制 Spring 的错误页面；未知路径/方法按 404/405。HTTP/DB 超时和退出均有界，不返回原始 SQL/驱动错误，不新增认证、健康检查或消费调度。

## Scope

范围内：完整 Go gateway、删除原 gateway Java 源码/测试/POM、根 POM 移除 gateway、当前使用文档迁移、编译与独立评审、本地交付和归档。保留 Java HTTP/直连客户端及 Maven 聚合；不修改 SQL、CLI 或客户端实现、不新增/运行测试、不启动网关/数据库、不执行最终发布构建、不联网/推送/部署。主分支既有文档已由 aa92083 单独提交；本 FD 以该提交为基线。

## Work items

| ID | 结果 | 规模/难度/预计 | 依赖 | 完成标准 |
|---|---|---|---|---|
| 1.1 | Go module 与配置 | 小/中/≤半天 | 无 | 独立依赖、原生/旧环境变量优先级、连接参数与超时验证、帮助路径 |
| 1.2 | DTO 与 PostgreSQL 操作 | 小/中/≤半天 | 1.1 | HTTP 时间/nullable/ID、连接池、全部 SQL 操作、锁及维护语义 |
| 1.3 | HTTP 请求与路由 | 小/中/≤半天 | 1.2 | 9 路由、字段/JSON 验证、成功/失败信封、请求取消与时限 |
| 1.3.1 | Java recover 数值时间兼容 | 小/中/≤2小时 | 1.3 | 按既有 Java ObjectMapper 的数值 epoch seconds 输入兼容，不经 float64，限制解析资源；与 ISO 输入并存 |
| 1.3.2 | 修复 R1 负小数时间兼容 | 小/中/≤2小时 | 1.3.1 | 按 Jackson 对负整数秒的正纳秒组合规则解析，保留 -0.x，重新编译与独立评审 |
| 1.4.1 | 异常退出先取消连接请求 | 小/低/≤1小时 | 1.4 | 非信号退出也在关闭连接池前取消 root context，避免借出连接等待完整请求期限 |
| 1.4 | Go 启动与 Java 移除 | 小/中/≤半天 | 1.3 | 信号/退出/池关闭、移除 gateway Java 构建与源码、Java 客户端保留 |
| 1.5 | 迁移文档 | 小/中/≤半天 | 1.4 | 根/模块 README、配置/客户端/模型/索引文档无失效当前源码链接，配置/错误兼容边界清晰 |
| 1.6 | 编译、静态证据与交接 | 小/低/≤2小时 | 1.5 | 一次离线 compile-only、diff 检查、Dual 实施报告、独立 Reviewer |

### TODO

- [x] 1.1 Go module 与配置。
- [x] 1.2 DTO 与 PostgreSQL 操作。
- [x] 1.3 HTTP 请求与路由。
- [x] 1.3.1 Java recover 数值时间兼容。
- [x] 1.3.2 修复 R1 负小数时间兼容。
- [x] 1.4 Go 启动与 Java 移除。
- [x] 1.4.1 异常退出先取消连接请求。
- [x] 1.5 迁移文档。
- [x] 1.6 编译、静态证据与交接。

## Acceptance

- AC-01：gateway 为独立 Go module，不依赖 JVM、psql、CLI 或客户端；根 Maven 仅聚合 Java 客户端。
- AC-02：9 个 POST 路由、成功信封/消息、DTO 类型/字段/空值、ISO 时间与现有 HTTP 客户端一致。
- AC-03：发布、先租约再拉取、commit=false、recover 和 5 个维护入口完整，参数与数据库函数不变，SQL 标识符安全。
- AC-04：配置默认值与优先级清晰，旧环境迁移可用；请求验证与错误状态明确，日志/错误不泄露 SQL、URL、凭证或负载；并发访问使用连接池，请求取消/超时与优雅退出有界。
- AC-05：当前文档符合 Go 实现，明确 Spring 配置文件退出、请求上限、失败信封变化、无认证及原有 SQL 限制；历史归档不改。
- AC-06：离线编译和静态检查真实记录，无测试/数据库/应用运行验证；独立 Reviewer 事件通过后本地 squash 合并并归档。

### 实施中证据修正

逐行核对 client/java/gateway 的 PgEventBusHttpClient 发现其默认 ObjectMapper.findAndRegisterModules() 未禁用 WRITE_DATES_AS_TIMESTAMPS，recover 会发数值 Instant。为履行 AC-02/03 的既有客户端兼容，新增 1.3.1，不更改原 ID 或客户端。recover 在带时区 RFC3339 字符串之外接受整数/小数 epoch seconds（最多 32 字符，不支持科学计数法，数值范围对应 ISO 年 0000—9999，截取到纳秒精度）；缺失/null/布尔/无时区字符串/越界均拒绝。上文仅字符串的设计描述由本段补充，不将缩小客户端兼容性作为默认决定。此项是静态发现后的实际修复，首次编译发生在修复前，因此修复后必须重新编译。

## Verification

只做静态核验和一次离线编译：gateway 目录执行 GOTOOLCHAIN=local CGO_ENABLED=0 GOPROXY=off GOSUMDB=off go build -mod=readonly -o ../.cache/compile/pg-eventbus-gateway.exe .。不执行程序、测试、formatter、linter/vet、网络请求、真实 SQL 或最终构建。依赖校验和来自仓库 cli/go.sum 中既有同版本依赖。

静态逐路由比较旧控制器、GatewayService、SQL 签名及各语言 HTTP DTO；检查空值、64 位范围、时间编码、租约失败短路、SQL 参数绑定、JSON 限制/错误脱敏、优先级及退出路径。运行 git diff --check，搜索当前文档中的旧入口。独立 Reviewer 检查实际 diff 和 Dual Worker 报告，最多三次结果，不将未运行集成验证描述为通过。

### 独立 Reviewer R1

- 来源事件 `FD-004-000004-implementation-ready`；Session `fd004-reviewer-20261016-b72c9e1a`；审阅 HEAD `204dbf2`，第 1 次结果为 changes-requested。
- 独立 Dual 评审：`docs/features/reviews/FD-004-review-r1.md` / JSON。R1/P2：负小数 recover 未遵循 Jackson 负整数秒加正纳秒的既有输入规则，影响 AC-02/03 与 1.3.1；修复、授权编译和独立复审后再判断通过。
- Reviewer 仅静态核对和本地 javap；未运行测试、额外编译或应用/数据库；尚未合并/归档。

### Worker repair R2

- 来源 `FD-004-000005-changes-requested`，同一 Worker Session claim；本周期已产生 1 次 Reviewer 结果，修复明确可行动。
- 完成 1.3.2，负数时间按 Jackson 历史规则；静态 `-2.500000000` → -1.5 秒、`-0.5` → -0.5 秒，解决 R1/P2。完成 1.4.1，异常退出先取消 root context 再关闭池。
- 修复提交 `3a987e7` 后第三次离线编译与 diff 检查通过；Dual 报告 `docs/features/reports/FD-004-implementation-r2.md` / JSON。未执行运行验证；R1 保留为历史，需独立 R2 评审，不以 Worker 自判代替。

### 独立 Reviewer R2

- 精确来源 `FD-004-000006-implementation-ready`；Reviewer Session `fd004-reviewer-20261016-b72c9e1a`；审阅 HEAD `9231e307b2f8f255cea0ff956a567ac239551bfb`，第 2 次 Reviewer 结果 verification-passed。
- Dual 评审 `docs/features/reviews/FD-004-review-r2.md` / JSON；R1/P2 经实际代码及既有独立 Jackson 证据核对已解决，1.4.1 取消顺序符合要求；其余验收沿用 R1 完整静态检查并确认增量未破坏。
- 全量 diff whitespace 检查通过；Worker 修复后第 3 次离线编译记录已阅。Reviewer 未执行额外编译、测试或运行验证；本通过不声明 PM 合并/归档已完成。

## Sources

### Worker evidence

- Session `fd004-host-20261016-5de84e0f`；来源事件 `FD-004-000003-design-ready`；独立 worktree 坐标 main / feature/FD-004 / .wt/FD-004 已核对。
- 1.1—1.6、1.3.1 全部完成；两个离线编译均通过，第二次覆盖静态发现后的数值 recover 修正；diff whitespace 静态检查通过。
- Dual 报告 `docs/features/reports/FD-004-implementation-r1.md` / JSON，记录实际命令与边界；无测试、应用/数据库运行、网络、发布构建或推送。
- 无持续 blocker；静态/编译不能证明运行兼容、锁/并发或退出实效，保留在报告中，独立 Reviewer 待交接。

- Issue: none
- 用户：$fd-workflow 创建 使用 GO 重写 gateway，然后自动完成（auto）。
- 基线 aa92083；gateway/src/main/java、client/*/gateway、docs/sql/050、080、090、100、110、170、200。
- Planner source event FD-004-000002-design-requested；Session 见 .ai/fd/FD-004/host-session.txt。

**Completed:** 2026-10-15
