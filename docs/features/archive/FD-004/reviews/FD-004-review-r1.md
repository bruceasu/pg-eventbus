# FD-004 独立评审 R1

<!-- aiw-data: FD-004-review-r1.json -->

结论：changes-requested。第 1 次 Reviewer 结果，发现 1 项 P2 兼容性问题。

- Reviewer Session：`fd004-reviewer-20261016-b72c9e1a`，独立于 Worker。
- 精确来源事件：`FD-004-000004-implementation-ready`，已检查 pending/reviewer 后由本 Session claim。
- 审阅 FD revision 4；diff `fff50cc..204dbf2a3e02be4ac76df2927b685a3a6351d54f`，原 Java 业务基线 `aa92083`。

## 发现

**R1 / P2：负小数 recover 时间与既有 Java 客户端不一致。** `gateway/internal/api/instant.go:33-37` 将整个负小数按数学意义缩放并保留负纳秒余数。既有 Java 默认 ObjectMapper 使用 Jackson InstantSerializerBase → DecimalUtils.toBigDecimal(epochSecond, nano)，其负秒与正纳秒拼接为负小数；InstantDeserializer._fromDecimal 则传 true 给 extractSecondsAndNanos，在整数秒小于 0 时对余数纳秒取绝对值。Reviewer 独立以本地 Jackson 2.19.2 的 javap 反汇编确认以上路径，并核对了 Java 客户端 recover 方法、旧 ConsumerController 与 GatewayService。

例如 Java `1969-12-31T23:59:58.500Z` 的 epochSecond=-2、nano=500000000，默认请求数值为 `-2.500000000`；旧 gateway 转回 -2 秒加 500000000 纳秒，当前 Go 转成 -2 秒减 500000000 纳秒，即 `1969-12-31T23:59:57.500Z`，相差 1 秒。`init_consumer_at_time` 使用 created_at <= startTime 更新位点，因而可能选择不同 offset。以上是代码与字节码推导，未执行运行探测。

该问题违反 AC-02/AC-03 及 1.3.1 的既有 Java ObjectMapper 输入兼容要求；数值范围内的 1970 年前时间没有被 FD 排除。请使数值拆分遵循既有 Jackson 的整数秒/纳秒规则（包括负秒的小数部分），保留 ISO 字符串路径、解析资源与范围限制，并同步 FD、实现报告及必要文档；实际代码变化后执行获授权的 compile-only，再申请独立复审。无需新增或执行测试。

## 验收核对

| 验收 | 静态结论及证据 |
|---|---|
| AC-01 | 独立 gateway Go module/pgxpool，无 JVM/psql/CLI/客户端依赖；根 Maven 仅两个 Java 客户端。支持。 |
| AC-02 | 9 个 POST 路由、成功 message/OK、camelCase/int64、nullable、空 events=[]、RFC3339 响应与各语言 DTO 对照一致；recover 负小数例外见 R1。 |
| AC-03 | 对照旧 Java 与 SQL：参数绑定、标识符引用、poll 租约失败短路且不释放、commit 保留 false、future=6、cleanup=10000、5 个维护入口一致；recover 时间参数含 R1。 |
| AC-04 | 原生/旧环境逐字段优先、凭证覆盖、正 int32/端口/期限检查、1 MiB/媒体/类型/必填验证、错误脱敏、连接池/context/信号退出路径有静态依据。支持既定静态验收。 |
| AC-05 | 当前 README/配置/客户端/模型/索引文档更新；Spring 文件迁移、失败信封、无认证及 SQL 已有限制披露。历史、SQL、CLI、客户端路径 diff 为空。支持。 |
| AC-06 | Worker Dual 报告如实记录两次离线编译，第二次覆盖新增 recover 逻辑；Reviewer 未重复编译。独立评审已完成但未通过，禁止由本结果推断可合并/归档。 |

## 实际命令与边界

实际执行：`aiw fd show FD-004`、`aiw fd claim --help`、`aiw fd emit --help`、上述精确 claim；`git diff --stat fff50cc..204dbf2`、按文档路径的 diff、`git diff --name-only fff50cc..204dbf2 -- cli client docs/sql docs/features/archive`、`git diff --check fff50cc..204dbf2`（退出 0）、`git show aa92083:<旧 Java 路径>`、`git rev-parse HEAD`、`git status --short`；Get-Content/rg 静态读取 FD、Dual Worker 报告、Go 源码、各语言客户端、SQL、文档/模板/技能；本地 `javap -c -p -classpath <jackson-datatype-jsr310-2.19.2.jar>` 静态查看 DecimalUtils、InstantDeserializer、InstantSerializerBase。部分最初猜测的文件路径和 Windows glob 读取失败，随后用 rg --files 定位并读取正确路径；无检查成功的虚报。

未运行测试、额外编译、HTTP/数据库/业务应用、formatter、linter/vet、网络请求、最终构建、推送或部署。未评估可选 fd-test 报告。

剩余风险：静态检查与 Worker 编译不证明实际 PostgreSQL/HTTP 互通、并发租约、跨平台或退出效果。异常 Serve 故障时 defer pool.Close 先于 defer stop，可等待已存在请求的配置期限（最多 24h）；正常信号路径先取消且 HTTP Shutdown 为 10s，当前有界要求未将该异常延迟列为阻塞。既有 SQL 清理边界与无认证维持 FD 明确范围。
