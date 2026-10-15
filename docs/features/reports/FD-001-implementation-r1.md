# FD-001 Go CLI 实现报告

<!-- aiw-data: FD-001-implementation-r1.json -->

## 交接与范围

- Worker：`fd001-worker-20261015-2f9c053a`；来源事件：`FD-001-000003-design-ready`。
- 父分支 `main`，设计基线 `c7098dd`，实现分支 `feature/FD-001`，工作区 `.wt/FD-001`。原 Java 实现保留在基线历史中。
- 完成 1.1—1.10：CLI 独立 Go module、命令/别名、有限 Spring 配置兼容、直接 pgx 调用、内嵌原子初始化、结果编码、取消及退出码、文档与构建切换。Gateway、客户端、`docs/sql/` 均无改动。
- Auto 初始仅允许编译和静态核验；用户随后明确允许下载锁定依赖及传递依赖、获取校验和。未扩展为测试、最终构建、程序启动或数据库操作授权。

## 实现与验收证据

| 验收 | 实际代码 / 静态依据 |
|---|---|
| AC-01 | `cli/go.mod` 仅外部依赖，无本地 replace；`main.go` 入口；`internal/app/run.go` 在配置和连接之前处理帮助；无 os/exec 或 CGO |
| AC-02 | `internal/app/command.go` 明确命令、别名、参数数量及本地校验；`run.go` 保留 false 业务结果，分别返回 0/1/2/130 |
| AC-03 | `internal/app/config.go` 按来源优先级载入选项、环境变量、唯一或指定文件；JDBC 参数白名单；支持空密码及凭证来源等级；未知键/命名空间、Spring 控制项报错 |
| AC-04 | `internal/db/connection.go` 使用 `pgx.Identifier{schema, function}`；操作使用 `$1...`；`initialize.go` 只替换 schema/角色限定标记，角色经 SQL 字符串传入 format(%I) |
| AC-05 | `sql/script.go` 编译时嵌入完整 SQL；`initialize.go` 同一连接 simple protocol 执行，错误后使用独立清理 context 尝试 ROLLBACK；不拆分分号或另套事务 |
| AC-06 | `events.go`、`maintenance.go` 调用原函数；`poll.go` 先获锁、失败返回空数组，成功才读事件；无自动提交、释放锁或 registry 读权限扩张 |
| AC-07 | `result.go` 明确 camelCase、int64、nullable 字段、非 nil events；payload 使用 RawMessage/原始字节；自定义 Timestamp 无 float64；`output.go` 完整编码后一次写 stdout |
| AC-08 | 命令 JSON/数字/时间与配置校验均在 Open 前；`connection.go` 只输出固定错误和 SQLSTATE，不输出原始驱动错误或 DSN；独立清理 context 关闭连接 |
| AC-09 | 删除 CLI Java/POM，从根 POM 仅移除 CLI 聚合项；更新根说明、CLI README、配置与 SQL 指南；旧启动和部分 Spring 配置不再支持的影响已明确 |
| AC-10 | 本报告和同名 JSON 记录实际命令、结果及未执行项；实现交独立 Reviewer，未在 Worker 会话自评通过 |

静态 SQL 对照从 `git show c7098dd:cli/src/main/resources/init.sql` 读取基线，逐段比较 `CREATE OR REPLACE FUNCTION ... $$;`，20 个函数定义均完全相同。模板变更仅在安装 schema、授权角色渲染和说明注释处。

读取本地 Jackson 2.19.2 字节码（`javap -c -private`）：SerializationFeature 的两个时间戳特性默认启用，InstantSerializerBase 调用 DecimalUtils.toBigDecimal。进一步静态核对 DecimalUtils 后，Go 编码匹配其 signed epoch-second 与正 nanosecond 拼接，包括 epoch=0 的 0.0 和 1970 年前的既有编码；没有把时间输出换成 Go 默认字符串，也没有顺带修正旧负时间编码。此处是兼容静态证据，不是运行对照测试。

读取已下载的 pgx v5.7.6 源码确认：JSONCodec 对 RawMessage/字节有专用编码路径；Conn.Exec 支持 QueryExecModeSimpleProtocol。普通数据操作使用 pgx 参数绑定，simple protocol 只显式用于完整安装脚本与 ROLLBACK。

## 实际执行的检查

1. 读取 FD、角色规则、工作管理合同、CLI 代码、DTO、SQL 与文档；检查 FD 事件和 worktree.json，Worker 精确认领来源事件。
2. 已授权依赖准备：`go mod download`、`go mod download all`；离线 `GOPROXY=off go mod tidy` 因缺少历史传递依赖元数据失败，随后在用户下载授权范围内 `go mod tidy` 成功，固定所有解析出的版本和 go.sum。没有执行测试。
3. 在 `cli/` 设置 `GOTOOLCHAIN=local`、`CGO_ENABLED=0`、`GOPROXY=off`，执行 `go build -o ../.cache/compile/pg-eventbus-cli.exe .`：首次通过；之后静态发现旧 Jackson 负时间编码差异并调整源码，再对最终源码重复同一编译检查，通过。该临时可执行文件未启动，不是发布包。
4. `git diff --check` 通过；`git diff --name-only c7098dd -- gateway client docs/sql` 无输出；检索源码确认无外部程序转调或 float64 数值转换。
5. 按工作项分别本地提交，未 rebase、push、部署或发布。Reviewer handoff 前再次提交 FD 进度和证据。

## 未执行与剩余风险

未生成或运行测试，未启动 Go CLI，未连接 PostgreSQL，未做 Linux 交叉编译/运行、最终发布构建、性能或内存测量，未重新构建未改动的 Java 项目。没有将这些检查记作通过。

真实 PostgreSQL 下的原子回滚、租约争用、取消、TLS/认证及 JSONB/时间往返仅有静态证据，尚无运行证据；Linux 可执行文件及双平台实际行为仍未验证。可在后续明确授权 `$fd-test` 或平台验证后补充事实；行为要求仍有效，没有因未运行而豁免。

当前无需未解决的人工决策。依赖下载授权已解决且已执行；本次没有导致 Auto 停止的未解决 blocker。等待独立 Reviewer 的实际报告与事件，不以本报告替代评审。
