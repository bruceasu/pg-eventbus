# FD-004 独立评审 R2

<!-- aiw-data: FD-004-review-r2.json -->

结论：verification-passed。第 2 次 Reviewer 结果；本轮无阻塞发现，R1/P2 已修复。R1 原始报告及失败事件保持历史，不改写为通过。

- Reviewer Session：`fd004-reviewer-20261016-b72c9e1a`，独立于 Worker。
- 来源事件：`FD-004-000006-implementation-ready`；先确认 pending/reviewer，再精确 claim。
- 审阅 HEAD：`9231e307b2f8f255cea0ff956a567ac239551bfb`；FD revision 6；完整计划基线 `fff50cc`，增量基线 R1 `f5b187d`，修复代码提交 `3a987e7`。
- 依据：本 Session 的 R1 独立全量源码/旧 Java/SQL/客户端/本地 Jackson 字节码审阅，加上本轮实际增量 diff、当前 FD、R2 Dual Worker 报告；未依赖 Worker 的“已修复”自判。

## 修复核对

`gateway/internal/api/instant.go` 的 QuoRem 仍按零截断整数秒；仅 whole.Sign()<0 时 nanos.Abs，因而 `-2.500000000` 得到 seconds=-2/nanos=+500000000，与 R1 独立检查的 Jackson 规则一致；`-0.5` 的 whole=0，负余数保留。正数、整数、ISO 字符串与长度/指数/范围控制未改。以上均为静态推导，未运行示例。R1/P2 对 AC-02/03 的具体违反已解决，1.3.2 的编译证据由 Worker R2 如实记录。

`gateway/main.go` 的资源退出 defer 显式先 stop() 再 database.Close()，覆盖异常 Serve/listen 返回；取消传播到 BaseContext 派生请求。正常信号的取消与 10s HTTP Shutdown 路径保留。支持 1.4.1，消除 R1 指出的先关池再取消所导致的延迟机制；不声称真实系统退出已验证。Gateway README 明确披露负数历史编码规则。

## 当前验收

| 条件 | 结论及依据 |
|---|---|
| AC-01 | 支持：独立 Go/pgx 模块，无 JVM/psql/CLI/客户端依赖；根 Maven 只聚合 Java 客户端。R1 检查路径本轮未变化。 |
| AC-02 | 支持静态验收：9 个 POST、成功信封/消息、DTO/int64/null/空数组/RFC3339 与既有客户端对照保留；数值 recover 修复见上文。 |
| AC-03 | 支持静态验收：SQL 参数/引用、租约失败短路、不主动释放、commit=false、recover 及 5 个维护入口保持；R1/P2 已修复。 |
| AC-04 | 支持静态验收：配置/验证/脱敏/连接池/context/时限保持，退出取消顺序改善。 |
| AC-05 | 支持：R1 当前文档迁移证据保持，新负数规则与实现一致；SQL、CLI、客户端与历史归档 diff 仍为空。 |
| AC-06 | 当前审核前置条件具备：Worker R2 记录修复后第 3 次离线编译成功，Reviewer 全量差异 whitespace 检查退出 0。独立评审通过允许后续 PM 交付；合并/清理/归档须由 PM 实际执行，未在本报告声明完成。 |

## 实际命令、未运行项与风险

本轮执行 `aiw fd resume FD-004`、`aiw fd claim FD-004 FD-004-000006-implementation-ready --session fd004-reviewer-20261016-b72c9e1a`；`git status --short`、`git diff f5b187d..9231e307b2f8f255cea0ff956a567ac239551bfb`、`git diff --check fff50cc..9231e307b2f8f255cea0ff956a567ac239551bfb`（退出 0）、增量 name-only、全量保护路径 name-only、`git rev-parse HEAD`；Get-Content 静态读取当前 FD、R2 Markdown/JSON、instant.go 与 main.go。R1 的实际全量审阅命令与 javap 证据见 R1 报告，不描述为本轮重跑。

未运行测试、额外编译、HTTP/数据库/业务应用、Maven、formatter、linter/vet、网络、最终构建、跨平台运行、推送或部署；未评估可选 fd-test 报告。

剩余风险：静态验收与离线编译不证明真实 PostgreSQL/HTTP 互通、并发/租约、信号及跨平台行为。Spring 文件/旧错误页/JDBC 专用参数仍需按文档迁移；输入格式与 1 MiB 限制、无认证及原 SQL 清理边界保持已记录范围。无持续 blocker；本轮不需要技能或模板变更。
