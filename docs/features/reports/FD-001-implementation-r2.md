# FD-001 R1 评审修复报告

<!-- aiw-data: FD-001-implementation-r2.json -->

- Worker：`fd001-worker-20261015-2f9c053a`；精确认领来源事件 `FD-001-000005-changes-requested`。当前周期已有 1 次 Reviewer outcome，R1 未通过记录完整保留。
- 实现基线仍为 `c7098dd`；修复提交 `a239ce2`。本报告补充 R1 实现报告，不覆盖其历史证据。

## 修复

R1-01：`cli/internal/app/config.go` 在加载文件、建立连接或执行 SQL 之前调用 `rejectUnsupportedEnvironment`，枚举环境变量名称，拒绝 `SPRING_PROFILES_*`、`SPRING_CONFIG_*`、`SPRING_APPLICATION_JSON` 以及未支持的 `SPRING_DATASOURCE_*`，包括 Hikari 和显式空字符串。Windows 按环境变量名称不区分大小写的规则匹配。只允许原 URL/USERNAME/PASSWORD 和新增明确兼容的 DRIVER_CLASS_NAME；driver 仍只接受 org.postgresql.Driver。无关系统环境变量不受限制。拒绝诊断是固定文本，不含变量值、JSON、DSN、密码；Run 将该配置错误映射到退出码 2。帮助仍在所有配置检查之前返回且不连库。

上述内容有源码分支及调用顺序的静态证据，没有运行环境场景测试。同步更新 CLI README 与配置指南，不再笼统暗示任意 Spring 环境控制都可兼容。

R1-02：恢复 FD 中历史 SQL 源路径 `cli/src/main/resources/init.sql`，目标为 `cli/sql/init.sql`。新增子项 1.2.1 / 1.10.1 记录修复，未复用或擦除原项及 R1 评审结果。

## 实际核验

- 阅读 R1 Markdown/JSON、核对 pending Worker 事件和评审次数，再精确认领修复事件。
- `git diff --check` 通过；环境拒绝范围、空值分支、Windows 名称匹配、driver 校验及退出码按实际源码静态核对。
- 修复后在 `cli/` 用 `GOTOOLCHAIN=local CGO_ENABLED=0 GOPROXY=off go build -o ../.cache/compile/pg-eventbus-cli.exe .` 编译：通过。第一次修复后编译通过，新增 Windows 名称兼容后对最终代码重复一次，通过。临时产物未启动。
- 未执行测试、程序、数据库操作、Linux 构建、发布构建、formatter、linter 或 benchmark。没有新增网络操作。

原 AC-01—AC-10 对照见 R1 实现报告；本次只修复其中 AC-03/AC-08 的明确遗漏，行为验收与运行授权没有改变。真实 PostgreSQL 及跨平台运行仍无证据，残余风险不变。当前没有未解决 blocker，等待第 2 次独立 Reviewer outcome。
