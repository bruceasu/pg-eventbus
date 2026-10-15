# FD-002 Go CLI 原生 TOML 配置实现报告

<!-- aiw-data: FD-002-implementation-r1.json -->

- Worker session：`fd002-worker-20261015-toml`；精确认领事件 `FD-002-000003-design-ready`。
- 隔离分支 `feature/FD-002`，父分支 main，隔离基线 `2939b1d`。创建 worktree 前，父工作区出现的旧 schema 计划删除已单独提交，不混入本 FD 代码或设计提交。
- 实现提交：`45be7d1`（1.1）、`b74f4a1`（1.2）；当前验收取代 FD-001 的 CLI Spring 兼容决定，历史归档保持原样。

## 结果与验收

| 条件 | 实际源码证据 |
|---|---|
| AC-01 | config.go 只选择 --config / PGEVENTBUS_CONFIG / pg-eventbus.toml；显式扩展名检查、1 MiB 限制、文件错误分类、默认缺失与显式缺失的不同路径完整 |
| AC-02 | 新 environmentKeys 和 command.go optionKeys 对齐；配置按 rank 1/2/3 覆盖；TOML 使用指针保留缺失/空值区别，数值转 int64 后按 int32 范围校验；databaseOptions 凭证来源规则保持；timeout 适用三种来源 |
| AC-03 | 删除 YAML/properties、Spring 环境扫描及 driver/JDBC 转换；无旧参数映射；TOML 结构体、Undecoded 和精确 Keys 白名单共同拒绝未知/错误大小写键，固定诊断不转发解析器原始错误 |
| AC-04 | 增加 pg-eventbus.example.toml；更新 CLI README、根 README、配置指南中的接口说明与 Java/Go 区分；原 Gateway/客户端和归档不变 |
| AC-05 | internal/db、sql、main.go、run.go、output.go 无差异；command.go 只变选项和 help，不改业务命令或参数校验；完成离线本机编译及静态核验，无运行验证冒充 |

本次用户明确选择原生 Go/TOML，不再要求旧 SPRING/BUS 控制环境的兼容或拒绝；它们不参与加载，旧点分参数和显式旧格式文件被拒绝。这是配置接口决定变化，不是跳过测试后豁免功能。

## 实际检查

1. 沿用会话中已授予的依赖下载授权，使用固定 `github.com/BurntSushi/toml v1.4.0` 替换直接 YAML 依赖，执行 `go mod edit`、`go mod download github.com/BurntSushi/toml`、`go mod tidy`。首次未引用参数的 PowerShell 调用报 too many arguments，未完成依赖变更；随后按引号参数重试成功。未使用 floating latest。
2. 读取锁定 TOML 解析器 decode.go、meta.go、parse.go：确认指针分配、字符串/整数类型要求、重复键错误及 Undecoded 行为。发现其结构体字段允许 EqualFold，因此补充精确键白名单，保证公开键区分大小写。只是读取源码，没有运行 TOML/CLI 测试或程序。
3. `GOTOOLCHAIN=local CGO_ENABLED=0 GOPROXY=off go build -o ../.cache/compile/pg-eventbus-cli.exe .` 在 cli/ 执行，通过。临时产物未运行，不是发布包。
4. `git diff --check` 通过；`git diff --name-only 2939b1d -- gateway client docs/sql cli/internal/db cli/sql cli/internal/app/run.go cli/internal/app/output.go cli/main.go` 无输出。检索 CLI Go 源码和 go.mod，无 Spring/JDBC/YAML 加载分支和旧键映射。

go.sum 仍可包含 pgx 上游测试依赖的 YAML 校验和，CLI 没有 YAML 直接依赖或运行时加载器；未手工删除标准 go mod tidy 保留的上游校验和。

## 未运行与剩余风险

未新增或运行测试，未运行 CLI 或数据库操作，未做 Linux 构建/运行、formatter、linter 或最终发布构建。TOML 文件、参数和环境组合的真实运行以及 PostgreSQL 行为只有静态与编译证据；这些残余风险保留。暂无未解决 blocker，等待独立 Reviewer 检查当前 FD，而非重用旧 Spring 兼容验收。
