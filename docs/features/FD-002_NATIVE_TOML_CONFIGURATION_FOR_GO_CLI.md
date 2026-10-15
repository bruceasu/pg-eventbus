# FD-002: Go CLI 原生 TOML 配置

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

FD-001 将 CLI 迁到 Go，却保留了 Spring 配置文件、点分参数、环境变量、driver 与 JDBC URL 兼容分支。用户明确要求 Go 程序不再沿用 Spring Boot 格式和约定，并改用 TOML。本 FD 取代 FD-001 中 CLI 的配置兼容决定，保留原归档作为历史，不改变 Gateway 或客户端。

## Options and decision

1. YAML 和 TOML 并存并继续兼容 Spring：增加维护分支，违背本次明确取舍，不采用。
2. 只将 YAML 换成 TOML，保留 spring.datasource 和 bus 名称：仍然沿用 Spring 约定，不采用。
3. **采用原生 TOML、独立参数和 PGEVENTBUS 环境变量**：只有一个配置模型，移除旧兼容分支。

## Solution

默认只从运行目录读取 `pg-eventbus.toml`；`--config <path>` 优先于 `PGEVENTBUS_CONFIG`，再回退默认文件。显式指定的缺失文件报运行错误 1，默认文件不存在可仅使用参数/环境变量。显式路径仅接受 .toml。文件有 1 MiB 限制。

```toml
timeout = "0s"

[database]
url = "postgresql://localhost:5432/pg_eventbus"
user = "app_role"
password = "change-me"
schema = "bus"

[consumer]
batch_size = 100
lock_ttl_seconds = 30
```

| 参数 | 环境变量 | TOML 键 |
|---|---|---|
| --database-url | PGEVENTBUS_DATABASE_URL | database.url |
| --database-user | PGEVENTBUS_DATABASE_USER | database.user |
| --database-password | PGEVENTBUS_DATABASE_PASSWORD | database.password |
| --schema | PGEVENTBUS_SCHEMA | database.schema |
| --batch-size | PGEVENTBUS_BATCH_SIZE | consumer.batch_size |
| --lock-ttl-seconds | PGEVENTBUS_LOCK_TTL_SECONDS | consumer.lock_ttl_seconds |
| --timeout | PGEVENTBUS_TIMEOUT | timeout |

配置值优先级：参数 > 环境变量 > TOML > 内置默认值。保持缺省 schema bus、批大小 100、锁 TTL 30 秒、timeout 0；显式空密码与缺失值不同。保留现有 URL 内凭证与单独凭证字段的来源优先级。仅接受原生 postgresql:// 或 postgres:// URL，移除 JDBC 前缀和参数转换。保留 db init [owner] 与原生 PGEVENTBUS_DB_OWNER 后备，不新增授权行为。

使用固定版本 `github.com/BurntSushi/toml v1.4.0`。以带指针的明确 TOML 结构体区分缺失、零值、空字符串；拒绝未知键、错误类型、重复键和错误 TOML。解析器错误只输出固定诊断，避免源配置片段泄露密码。配置值不做 Spring 占位符展开，`${...}` 是普通字符串。

移除 YAML/properties 依赖与加载器、application.* 发现、Spring 环境扫描、driver 配置和 Spring/BUS 参数映射；旧环境变量作为无关环境被忽略，旧文件不自动读取，旧点分参数和显式旧格式路径报错。不存在隐藏的兼容优先级。帮助和示例统一使用新约定，提供 `cli/pg-eventbus.example.toml`。Java Gateway/客户端继续采用自身配置，文档明确区别。

这是用户明确授权的 CLI 配置接口变更；不再要求 FD-001 中 Spring 兼容与 Spring 环境拒绝行为。命令、SQL、JSON 编码、退出码、权限及事务行为保持不变，不豁免这些要求。

## Scope

仅修改 CLI 的配置加载、参数帮助、依赖锁定、示例及相关使用文档。范围外：Gateway、客户端、SQL、结果格式、命令语义、业务逻辑、数据库操作、测试或发布。本会话继续 FD 的隔离实施、独立评审与本地交付约定；已有依赖下载授权用于准备本次替换依赖，不运行测试、应用或最终构建。

## Work items

| ID | 结果 | 规模/难度/预计 | 依赖 | 完成标准 |
|---|---|---|---|---|
| 1.1 | 原生配置与 TOML 解析 | 小/中/≤半天 | 无 | 新参数/环境/文件绑定、优先级和类型边界完整，删除旧加载分支和依赖 |
| 1.2 | 示例与迁移说明 | 小/低/≤2小时 | 1.1 | 提供可直接复制的 TOML，CLI 与 Java 配置清晰区分，列出旧接口移除和新映射 |
| 1.3 | 编译、静态证据与独立评审交接 | 小/低/≤2小时 | 1.2 | 完成静态核验及本机离线编译检查，Dual 报告记录未运行项，交独立 Reviewer |

### TODO

- [x] 1.1 原生配置与 TOML 解析。
- [x] 1.2 示例与迁移说明。
- [x] 1.3 编译与静态证据。

## Acceptance

- AC-01：只加载 TOML；缺省文件、显式路径和大小限制符合设计，文件错误不泄露内容。
- AC-02：新参数/环境/TOML 映射及优先级生效；保持显式空密码、数值范围、timeout 和凭证来源规则。
- AC-03：无 Spring/YAML/properties/JDBC 兼容加载；旧约定不参与配置；未知 TOML 字段、错误类型和语法有明确失败路径。
- AC-04：示例、help 与文档一致；Gateway/客户端配置与历史 FD 记录不变。
- AC-05：SQL、命令、JSON、事务、退出码和无 ORM 边界无回归性改动；有编译和静态证据及独立评审，不冒充运行验证。

## Verification

默认只做静态核验与一次本机编译检查。读取锁定 TOML 解析器源码核对类型、未知键和重复键处理；逐项对照配置来源与文档，检查源码不存在旧映射/解析器，确保 DB/SQL/命令执行代码未改。

依赖准备沿用会话已有明确授权；锁定版本与 go.sum。编译在 cli 目录设置 GOTOOLCHAIN=local、CGO_ENABLED=0、GOPROXY=off，执行 `go build -o ../.cache/compile/pg-eventbus-cli.exe .`，临时产物不运行、不当作发布构建。不执行测试、formatter、linter、程序或数据库操作。独立 Reviewer 仅评估上述当前验收与实际证据；可选测试仍需用户明确调用 fd-test。

未运行 TOML/环境实际组合场景、真实 PostgreSQL 或跨平台程序，保留相应残余风险。编译和静态阅读不能替代运行验证。

### Worker evidence

- Worker session `fd002-worker-20261015-toml`, source event `FD-002-000003-design-ready`.
- Worktree coordinates verified: main / feature/FD-002 / .wt/FD-002; actual isolation base 2939b1d.
- All three work items implemented. Local-toolchain, CGO-disabled, offline compile-only passed. No tests or application/database execution.
- Dual report: docs/features/reports/FD-002-implementation-r1.md and JSON.
- Static protection check shows Gateway, clients, SQL and CLI database/execution/output paths unchanged. No active blocker; independent Reviewer pending.

### Reviewer R1

- Reviewer session `fd002-reviewer-20261015-98eac671`, exact source event `FD-002-000004-implementation-ready`; reviewed HEAD `7245d65` against `2939b1d`.
- Outcome **verification-passed**, first outcome in this cycle; no blocking finding. Native TOML acceptance supersedes prior Spring conventions; no behavior requirement waived.
- Dual evidence: `docs/features/reviews/FD-002-review-r1.md` plus JSON. Independently checked actual diff, configuration/source ranks, strict TOML parser branches and protected paths. No tests, program/database execution or extra builds; recorded runtime risks remain.

## Sources

- Issue: none
- 用户最新要求：Go 不再使用 Spring Boot 格式与约定，配置使用 TOML。
- 前序归档：`docs/features/archive/FD-001/FD-001_MIGRATE_CLI_TO_GO.md`。
- 基线：main `631ea90`；`cli/internal/app/config.go`、`command.go`、`cli/README.md`。
- Planner source event `FD-002-000002-design-requested`；session `fd002-planner-20261015-toml`。
