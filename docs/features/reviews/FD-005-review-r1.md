# FD-005 独立评审 R1

<!-- aiw-data: FD-005-review-r1.json -->

结论：通过。未发现阻塞当前验收范围的问题；本轮仅作静态评审，不代表运行互通验证通过。

- Reviewer Session：`fd005-reviewer-20261016-5bca912e`，与 Worker 独立。
- 来源：`FD-005-000004-implementation-ready`；先检查 pending、revision=4、artifact 后由本 Session 领取。
- 审查范围：`78f426d..a151f2eeecafeb7a0941094e3a3a92eca859963c`；业务基线 `cd31a46`。评审写入前工作树干净。
- 输入：当前 FD、Worker 双报告、实际代码与差异、现有 Gateway api/store/model、相关 SQL。没有检查可选 fd-test 报告。

| 验收 | 独立检查结果 |
|---|---|
| AC-01 | 两个 package.json 独立导出 ESM/index.d.ts，files 包含 helper、声明、示例和 tsconfig；无依赖下载或兄弟包引用。现有 Worker 证据记录 JS 语法及 TS noEmit 成功。 |
| AC-02 | 两类的 9 个方法逐一对应 Gateway 路由或 SQL。DB poll 先 await acquire_consumer_lock，失败不查询事件，成功查询 poll_events；commit 保留 false，不自行释放/重试。维护函数直接 await SELECT，future=6、cleanup=10000。 |
| AC-03 | 两包 codec 检查 signed int64 和安全 number；ID 输出统一 bigint，HTTP commit 通过 rawJSON 发送数字 token。decode 用 primitive source 恢复大整数；指数/小数形式的超安全整数明确拒绝。DB 对 id/payload 转 text、时间按 UTC 保留微秒；recover 保留字符串小数秒；空事件及 nullable 目标/锁时间得到保留。 |
| AC-04 | HTTP 路径按消费者单段编码，拒绝 dot 段；构造 URL 拒绝凭证/query/fragment。统一 POST、成功信封解包、HTTP/业务/JSON/网络错误映射及脱敏。AbortController 在 fetch 和 response.text 期间有效，finally 清 timer/listener。DB schema 双引号转义，参数均绑定，query receiver 保留且不关闭宿主连接。 |
| AC-05 | 逐项核对 .d.ts 与实现的参数、可选项和 DTO；示例包括明确检查锁和 commit 结果。文档说明 Node、现代 JSON、CORS、bigint、同步参数错误、DB 驱动/连接/超时/取消责任及未发布状态。 |
| AC-06 | Worker 已记录实际离线检查及跳过项，未将运行场景当作通过。本轮仅执行只读检查和 Git whitespace 检查；无关实现未纳入此 FD。当前独立结果补足 Reviewer 条件。 |

实际命令：`aiw fd --help`、`aiw fd show FD-005`、`aiw fd claim --help`、上述 exact event 的 claim、`aiw fd emit --help`；`git status --short`、`git rev-parse HEAD`、`git log -8 --oneline`、`git diff --stat 78f426d..HEAD`、指定文档的 `git diff 78f426d..HEAD`、`git diff --check 78f426d..HEAD`（通过）；`Get-Content`/`rg`/`Get-ChildItem` 读取源码、报告、声明、清单、SQL和指令候选。工作树不存在 AGENTS.md/.ai/.github 的直接读取曾返回不存在；`aiw fd show` 正常解析验证的工作树及权威收据，随后成功领取事件。未自行生成或修改收据。

未运行：额外编译、测试、运行片段/示例、HTTP/数据库交互、npm 安装/打包/发布、formatter/linter、网络、浏览器/跨平台检查、最终构建、推送和部署。Worker 的 node --check/strict checkJs/tsc noEmit 是其报告证据，本 Reviewer 未重复执行。

剩余风险：真实 PostgreSQL/HTTP/并发、现代 JSON 宿主及自定义 fetch 的 AbortSignal 行为没有运行证据；泛型不验证业务 payload，普通小数仍受 JS number 语义限制。DB 取消、连接生命周期、日志脱敏和外部事务由宿主负责，既有 SQL 清理风险不变。以上均已在设计/文档明确且不构成本轮新增 Gate。

PM 通知父分支另有外部提交 `da18527`，移动 Java Maven 文件并修改 Gateway README；该提交不属于本次评审范围，与 JS 包路径无直接冲突。本 Reviewer 未改动父分支、实现、合并或归档。
