# FD-001: 将维护 CLI 迁移到 Go

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual
**Effort:** High（多个独立小项；单项预计不超过半天）
**Impact:** CLI 作为独立可执行文件分发，无需 JVM、psql 或项目内其它模块。

## Problem

当前 `cli/` 已与 Gateway 和客户端解耦，但仍用 Spring Boot 启动命令行程序，通过 JDBC 调用 PostgreSQL 函数。运维人员需要 Java 25、数据源配置以及可执行 JAR；维护命令的工作主要是解析参数、执行 SQL 和输出结果，框架与业务体量不匹配。

当前主代码为 17 个 Java 文件、约 654 行，入口为 `PgEventBusCliApplication` / `PgEventBusCliRunner`。命令覆盖初始化、分区维护、事件发布、租约拉取、位点提交和按时间恢复。本次目标是将这些既有能力整体迁移到 Go，保持 PostgreSQL 函数作为业务边界，减少 CLI 的运行环境依赖。

依据用户在本会话中的范围决定：客户端按语言独立，维护 CLI 不依赖项目内其它模块、不使用 ORM；本次只创建 CLI 迁移设计，不启动实现或 Gateway 迁移。

## Options and decision

| 方案 | 收益 | 成本 / 限制 | 决定 |
|---|---|---|---|
| 保留 Spring Boot CLI | 无迁移成本，保留 Spring 配置机制 | 继续依赖 JVM，命令行生命周期仍依附应用容器 | 不采用 |
| 改成普通 Java/JDBC 命令行程序 | 可去掉 Spring Boot | 仍需 JVM，仍需重写配置及命令解析 | 不采用 |
| 独立 Go CLI，使用 pgx 原生连接 PostgreSQL | 单一可执行文件、直接参数化 SQL、可嵌入初始化脚本 | 需要明确命令、配置和输出兼容规则 | **采用** |
| Go 包装器转调 Java、psql 或现有 Go 客户端 | 可复用部分代码 | 转调外部程序保留部署依赖；现有 Go 直连客户端经 psql 执行，且拉取锁行为与 CLI 不同 | 不采用 |

选择 Go 的依据是当前程序边界很小、业务规则已在数据库中，迁移可集中在命令行和 PostgreSQL 适配层。收益以运行与分发简化衡量；不承诺未经实测的性能、内存或产物体积数字。

## Solution

### 程序与依赖边界

- 保留顶层 `cli/`，替换为独立 Go module：`github.com/bruceasu/pg-eventbus/cli`。使用本机已具备的 Go 1.25 工具链作为本次最低构建版本；`go.mod` / `go.sum` 锁定兼容的 pgx v5 版本，不使用浮动 latest，也不自动切换工具链。
- 使用标准库组织入口、参数解析、JSON、信号及输出；数据库使用 `github.com/jackc/pgx/v5`。配置文件兼容所需的 YAML 解析器同样锁定明确版本。无 ORM，不依赖其它项目 module，无 Java、psql、CGO 运行依赖。
- 单次命令使用一条 pgx 连接，按需连接、执行结束关闭；不为短生命周期 CLI 建连接池。帮助、语法检查及本地参数校验不连接数据库。
- 程序内通过 `context` 传递取消信号；支持可选 `--timeout`，默认 0 表示不新增命令执行时限，非零值须为正 duration。Ctrl+C 取消正在执行的请求并关闭连接，不启动后台任务或自动重试破坏性操作。
- 建议目录如下；包数量以职责为限，不抽取共享 core、contract 或仓储 module：

```text
cli/
  go.mod
  go.sum
  main.go
  internal/app/        # 命令解析、配置、输出和退出码
  internal/db/         # pgx 调用、SQL 标识符、初始化
  sql/
    init.sql          # go:embed 的完整初始化模板
  README.md
```

### 命令与行为兼容

可执行文件名为 `pg-eventbus-cli`（Windows 为 `.exe`）。`java -jar ...` 启动方式替换为该命令；保留以下位置参数和别名：

| 命令 | 语义 |
|---|---|
| `db init [owner]` | owner 缺省读取 `PGEVENTBUS_DB_OWNER`；安装表、函数和授权，不创建数据库或角色，不预建分区 |
| `partition ensure-current` | 调用 `ensure_current_partition()` |
| `partition ensure-future` | 调用 `ensure_future_partitions(6)` |
| `partition drop-older-than <keepMonths>` | 调用 `drop_partition_older_than(keepMonths)`；拒绝负保留月数 |
| `event send\|publish <source> <target> <type> <payloadJson>` | 校验完整单个 JSON 值，以参数绑定写入 JSONB；不经浮点数往返解析 payload |
| `event get\|poll <consumerName> <instanceId> [limit]` | 先获取租约；失败时返回空事件数组，成功后才调用 poll；不自动提交或释放租约 |
| `event set-cursor\|commit <consumerName> <instanceId> <newLastId>` | 以 int64 处理事件 ID，由数据库校验持有者与租约 |
| `event recover <consumerName> <isoInstant>` | 接收 RFC3339/RFC3339Nano 时间，调用按时间初始化函数 |
| `maintenance run` | 调用 `maintenance()`，不在 Go 中重复实现分区策略 |

`limit` 省略或非正数时使用配置的批大小。批大小和 TTL 默认分别为 100、30 秒；配置值非正数属于配置错误。保留当前 `ConsumerDto` 中锁时间为 null、位点占位值等既有返回含义，不趁迁移增加额外 registry 表查询或扩大权限要求。

### 配置兼容与明确变更

- 保留已公开的 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`BUS_SCHEMA`、`BUS_BATCH_SIZE`、`BUS_LOCK_TTL_SECONDS`、`PGEVENTBUS_DB_OWNER`。这些名称作为兼容接口使用，不要求引入 Spring。
- 保留命令行 `--spring.datasource.url`、`--spring.datasource.username`、`--spring.datasource.password`、`--bus.schema`、`--bus.batch-size`、`--bus.lock-ttl-seconds` 的 `--key=value` 形式；允许全局选项位于命令前或后。不得把 JSON 参数内部的 `--` 片段当成选项。
- 支持运行目录的单个 `application.yml`、`application.yaml` 或 `application.properties`，仅绑定上述数据源和 bus 键。发现多个候选文件时要求用 `--config <path>` 显式选择，避免隐式选错。优先级为命令行 > 环境变量 > 选定配置文件 > 内置默认值；空密码允许作为显式值，不与缺失值混淆。
- 本次不复刻 Spring 的 profiles、配置导入、占位符展开、classpath 查找和任意 DataSource/Hikari 属性；相关保留 Spring 控制键若出现，返回明确的不支持提示，不静默宣称兼容。文档标明此边界。
- 接受原文档中的 `jdbc:postgresql://...` URL，也接受原生 `postgresql://...` / `postgres://...` URL。只转换已知 JDBC 前缀及已支持参数，保留 URL 转义、TLS、主机和数据库含义；遇到未支持的 JDBC 参数报明确配置错误，不静默忽略。显式用户名、密码覆盖 URL 内相应值，覆盖次序按上述配置来源规则确定，不输出包含秘密的 DSN。
- schema 缺失或空白继续回退 `bus`。调用函数时将 schema 和固定函数名单独引用，数据参数使用 `$1...` 绑定；不通过修改连接的全局 search_path 来选 schema。
- 初始化 schema / owner 继续采用 `[A-Za-z_][A-Za-z0-9_]*` 校验，并按 PostgreSQL 标识符引用，避免大小写折叠造成安装与调用不一致。此处标识符不能作为普通 SQL 数据参数绑定。

### 输出与退出码

- 普通事件及维护成功操作只向 stdout 输出一行 JSON；字段名保持现有 camelCase，ID 保持 int64，payload 保留 JSON 类型及大整数精度，空 events 输出 `[]`，可空字段保留显式 null。
- 保持现有 CLI 的时间字段语义：当前 `new ObjectMapper().findAndRegisterModules()` 未禁用时间戳序列化，Instant 使用数值型 epoch seconds（含小数秒）。Go 应提供精确保留纳秒的 JSON 编码，静态核对现有依赖默认值；不能直接用 Go `time.Time` 的字符串编码替换现有输出，也不能经 float64 截断 ID 或纳秒。输入 recover 时间继续使用 ISO/RFC3339。
- `db init` 成功保留 `db init completed` 文本；帮助输出允许面向人的多行文本。诊断写 stderr；错误不伪装成成功 JSON，不输出 Spring banner 或启动日志。
- 退出码约定：0 为命令正常完成或帮助；1 为连接、SQL、文件读取等运行失败；2 为命令、参数或配置错误；130 为用户中断。获取锁失败或 commit 返回 false 仍为数据库正常结果，JSON 表达 false、退出码 0，保持现有语义。
- 修正旧 CLI 未知命令或缺参仅打印 usage 且可能返回 0 的行为。退出码、启动方式及不完整的 Spring 配置兼容是文档必须说明的迁移影响。

### 初始化脚本与事务

- 将 `cli/src/main/resources/init.sql` 迁至 `cli/sql/init.sql`，由 `go:embed` 编译进程序；运行时不依赖仓库或外部 SQL 文件。`docs/sql/` 仍是现有拆分脚本来源，函数集合、顺序和定义不在本 FD 中改写。
- 安装 schema 的两条语句、grant 角色改成有明确位置的渲染标记；仅渲染这些安装参数，不对整个脚本做 `bus` / owner 的无差别单词替换。标识符与 DO 块内 SQL 字符串按其不同语境编码，不能把 quote-identifier 的结果无处理地嵌入 SQL 字符串。
- 保留未带 schema 前缀的对象引用、事务内 `SET LOCAL search_path` 和函数的 `SET search_path FROM CURRENT`；选择非默认 schema 后，授权与函数后续分区操作必须落在该 schema。
- 完整多语句脚本通过同一物理连接、支持该执行方式的 pgx simple protocol 执行；不能按分号切分 PL/pgSQL 的 `$$` 函数或 DO 块。脚本自身已有 `BEGIN/COMMIT`，不再添加一层隐式嵌套事务。
- 执行失败或取消后，在仍可用的同一连接上尝试 ROLLBACK，随后关闭连接；不得返回初始化成功。pgx simple protocol 不用于拼接任意外部数据值。
- 不继续提供原仅在 classpath 脚本缺失时才触发的 `PGEVENTBUS_SQL_DIR` / 向上查找 docs 回退。Go 构建缺少嵌入脚本会直接失败；迁移说明明确该诊断接口已移除。

### 构建切换

Go 实现在 `cli/` 内替换 Java，最终不保留双实现及双入口。删除 CLI 自身 Java 源码、测试源码、资源旧路径与 `cli/pom.xml`，从根 POM 聚合列表移除 `cli`；Gateway 与 Java 客户端 Maven 构建保持原样。

CLI 支持从仅包含 `cli/` 源码与 module 文件的目录独立构建。目标为 Windows amd64 和 Linux amd64，使用纯 Go 路径（`CGO_ENABLED=0`）；本 FD 不引入发布流水线、安装器或预制二进制提交。构建及校验命令必须在正确 module 目录执行，避免误用 client/go 下的 module。

## Scope

范围内：既有 CLI 全部命令、直接 PostgreSQL 调用、内嵌初始化 SQL、上述配置与输出规则、退出码、取消处理、CLI 构建切换及文档更新。

范围外：Gateway 迁移、任何客户端改造、共享 Go module、ORM、数据库函数业务修复、表结构升级、迁移版本管理、新增业务命令、认证体系、生产部署、性能承诺和发布流水线。本次创建设计也不授权提交、worktree、实现、测试、最终构建、推送或发布。

### 预计文件变更

| 路径 | 操作 | 用途 |
|---|---|---|
| `cli/go.mod`、`cli/go.sum`、`cli/main.go` | 新建 | 独立 Go 入口与固定依赖 |
| `cli/internal/app/*.go` | 新建 | 命令、配置、JSON 及退出策略 |
| `cli/internal/db/*.go`、`cli/sql/init.sql` | 新建 / 迁移 | 原生连接、参数化 SQL、初始化 |
| `cli/src/`、`cli/pom.xml` | 删除 | 切换完成后移除 Java CLI；原有测试不冒充 Go 验证证据 |
| `pom.xml` | 修改 | 只移除 CLI 聚合项 |
| `cli/README.md`、`README.md`、`docs/pg-eventbus-schema-and-cli-addendum.md`、`docs/PG-EventBus.md` | 修改 | 构建、运行、兼容边界、架构说明 |

## Work items

每项规模均为小、难度低或中；预计工作量是拆分参考，不是交付时间保证。基础逻辑由指定项拥有，后续命令项复用，不各自实现连接、JSON 或配置逻辑。首次设计无旧项映射；开始实现后固定这些 ID。

| ID | 独立结果 | 规模 / 难度 / 预计 | 依赖 | 完成标准 |
|---|---|---|---|---|
| 1.1 | Go module、入口与命令语法 | 小 / 中 / ≤半天 | 无 | 锁定工具链兼容依赖；命令及别名、位置参数、选项和 help 有唯一解析入口；参数错误不访问数据库 |
| 1.2 | 配置加载与 URL 兼容 | 小 / 中 / ≤半天 | 1.1 | 环境变量、显式选项、有限配置文件、覆盖顺序、空密码、JDBC URL 转换与不支持项均有确定处理 |
| 1.3 | 连接、SQL 绑定与取消边界 | 小 / 中 / ≤半天 | 1.2 | 直接 pgx 连接；统一标识符引用、参数绑定、取消/超时、清理和脱敏错误；不使用 psql 或其它项目模块 |
| 1.4 | 结果模型、时间编码与输出 | 小 / 中 / ≤半天 | 1.1 | 统一 stdout/stderr/退出码；核对旧 DTO；int64、数值时间戳、JSONB、null 和空数组编码有静态证据 |
| 1.5 | 原子初始化 | 小 / 中 / ≤半天 | 1.3、1.4 | 内嵌完整脚本、限定安装参数渲染、同连接多语句执行与失败回滚处理；无 SQL 业务定义漂移 |
| 1.6 | 分区及 maintenance 命令 | 小 / 低 / ≤2小时 | 1.3、1.4 | 四条命令均调用正确函数及固定参数；成功结果只在 SQL 完成后构造；错误向上传递 |
| 1.7 | publish、commit、recover 命令 | 小 / 中 / ≤半天 | 1.3、1.4 | 保留别名、参数及结果；JSONB 无精度丢失，ID 不转 float64，recover 正确绑定时间 |
| 1.8 | 租约拉取命令 | 小 / 中 / ≤半天 | 1.3、1.4 | limit 回退正确；先获锁，失败不读事件；成功才 poll；保留结果字段，不自动提交/释放锁 |
| 1.9 | 构建入口切换与迁移文档 | 小 / 中 / ≤半天 | 1.5—1.8 | 删除 Java CLI 及根聚合项，CLI 可脱离仓库构建；文档完整列出配置与输出变化、Windows/Linux 构建和回退方式 |
| 1.10 | 静态核对与实现证据 | 小 / 低 / ≤2小时 | 1.9 | 逐项关联下列验收与实际改动，执行一次授权范围内 compile-only 检查；记录未运行项、局限及 Dual 报告，交独立 Reviewer |

### TODO

- [ ] 1.1 Go module、入口与语法。
- [ ] 1.2 配置加载与 URL 兼容。
- [ ] 1.3 连接、绑定与取消。
- [ ] 1.4 结果模型与输出。
- [ ] 1.5 原子初始化。
- [ ] 1.6 维护命令。
- [ ] 1.7 发布、提交及恢复。
- [ ] 1.8 租约拉取。
- [ ] 1.9 构建切换与文档。
- [ ] 1.10 静态核对及证据。

## Acceptance

| 编号 | 必须具备的可观察行为 | 负责项 |
|---|---|---|
| AC-01 | CLI 是单一 Go 可执行程序，运行不依赖 JVM、psql、Gateway 或其它项目 module；help 不连库 | 1.1、1.3、1.9 |
| AC-02 | 现有命令与别名齐全；错误参数退出 2，错误 SQL/连接退出 1，正常业务 false 退出 0，取消退出 130 | 1.1、1.4、1.6—1.8 |
| AC-03 | 文档既有环境配置、指定的文件/选项和优先级生效；旧示例 JDBC URL 可用；不支持的配置有诊断 | 1.2、1.9 |
| AC-04 | 自定义 schema 的安装、授权、发布和后续分区操作一致；SQL 值及标识符均按语境处理 | 1.3、1.5—1.8 |
| AC-05 | 初始化在同一连接完整执行；任何中途失败不报告成功，不留下本次部分安装；既有数据库/角色不由 CLI 创建 | 1.5 |
| AC-06 | publish 保留 JSONB 值；poll 遵守租约顺序；commit 不绕过锁；recover 和 maintenance 调用原函数，业务规则不搬到 Go | 1.6—1.8 |
| AC-07 | JSON 字段、int64、大整数 payload、数值型纳秒时间戳、null、空数组符合上述兼容规则；stdout 无诊断混入 | 1.4、1.6—1.8 |
| AC-08 | 未知命令、无效配置/JSON/时间/数字在必要时连库前失败；错误信息不泄露密码；退出释放连接 | 1.1—1.4、1.7 |
| AC-09 | 根 Maven 构建仅移除 CLI，其余 Java 项目及客户端接口不变；文档反映新入口与明确迁移影响 | 1.9 |
| AC-10 | 实现报告如实记录 compile-only、静态核验、跳过项和剩余风险，所有项完成后交独立 Reviewer | 1.10 |

## Verification

默认执行方式遵循 `fd-workflow`：Worker 静态核验加一次仓库允许的 compile-only 检查；不生成或运行测试，不把可执行程序启动、数据库操作、最终构建或基准测量混称编译检查。本次规划未运行任何测试或构建。

1. 逐一将原 Java runner、DTO、数据库调用和嵌入 SQL 与新实现对照，按 AC-01—AC-09 留下文件位置及处理分支证据；重点核对配置来源、租约顺序、JSON 精度、脚本不按分号拆分、回滚及资源关闭。
2. 检查最终 `go.mod` 仅外部依赖、无本地 replace 或其它项目 module；无 os/exec 调用 Java/psql，无 CGO；CLI 源码与根 POM 删除范围受控，Gateway/客户端和数据库函数未改。
3. 仓库当前没有 compile 脚本。实现时优先使用届时仓库的 `compile*` 脚本；若仍无脚本，采用 `go build -o <隔离工作区内临时目录>/pg-eventbus-cli[.exe] .` 作为唯一编译连通性检查，在 `cli/` 执行并标明该产物不是发布包；设置 `GOTOOLCHAIN=local`、`CGO_ENABLED=0`。依赖必须可用，不把下载依赖或切换工具链当作已授权动作。
4. Worker 报告使用中文 Markdown + 同名 JSON，保留一处同目录 `aiw-data` 引用，记录实际命令、结果、未运行项目及风险，再通过 `implementation-ready` 交独立 Reviewer；本设计阶段不创建实现/评审报告。

以下是另行授权时可用的运行验证场景，**不是默认 FD 阶段，也不作为 Reviewer 必须评估的独立测试报告**：通过 `$fd-test` 在临时 PostgreSQL 数据库内核对默认与自定义 schema、初始化中途失败回滚、缺失授权角色、租约争用、int64 边界与大整数 JSONB、时区及纳秒、失效连接和取消。禁止对真实业务库执行破坏性维护；不具备可丢弃数据库时记录未运行。跨平台最终产物、脱离仓库的程序启动和体积/启动耗时测量同样需另行授权。

数据库语义仍是必须实现的验收要求；静态或编译通过不代表运行行为已验证，未运行集成场景不构成行为豁免。迁移后的真实 PostgreSQL 运行一致性与双平台运行表现，在取得实际运行证据前保留为残余风险。

### 实施前置与回退

- 本 FD 设计基于 `main` 的 `1be8739`（Refactor）。开始实现前重新核对源代码与 FD、按工作流提交独立设计计划并保持父工作区干净，在 `feature/FD-001` / `.wt/FD-001` 内实施；本次没有创建 worktree 或提交。
- 切换前保留旧 CLI Git 历史和既有可执行 JAR 的获取说明；回退使用切换前版本的 Java CLI，数据库函数不因语言切换升级或回滚。不在仓库新增长期并行 Java 实现。
- 当前无 `AGENTS.md`、已有 FD 或稳定 OpenSpec 规范；不创建 Issue、AIW Task 或 OpenSpec change 作为前提。

### Planner handoff

- Planner source event: `FD-001-000002-design-requested`。
- Planner session: `fd001-planner-20261015-61fcae7b`。
- 设计结果：已明确范围、技术选择、兼容影响、独立工作项及 Verification；无待人决策的 material input Gate。完成设计后发出 `design-ready`，仅留下待处理 Worker handoff，不自动开始实现。

## Sources

- Issue: none
- 用户请求：本会话“创建一个迁移 CLI 到 Go 的 FD”，前序关于 CLI 独立、无 ORM 与 Go 迁移的讨论。
- [当前 CLI 说明](../../cli/README.md)、[CLI runner](../../cli/src/main/java/me/asu/pg_eventbus/cli/PgEventBusCliRunner.java)、[数据库调用](../../cli/src/main/java/me/asu/pg_eventbus/cli/CliDatabase.java)、[初始化脚本](../../cli/src/main/resources/init.sql)。这些是迁移前路径，实施完成后更新为实际实现路径并保留历史基线引用。
- [配置与初始化指南](../pg-eventbus-schema-and-cli-addendum.md)、[数据库语义](../PG-EventBus.md)、[根 POM](../../pom.xml)。
- 技术接口参考：[pgx](https://pkg.go.dev/github.com/jackc/pgx/v5)、[Go embed](https://pkg.go.dev/embed)。本阶段未访问网络或锁定浮动依赖版本。
