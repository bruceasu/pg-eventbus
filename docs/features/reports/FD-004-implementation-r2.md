# FD-004 Go Gateway 修复报告 R2

<!-- aiw-data: FD-004-implementation-r2.json -->

- Worker Session：`fd004-host-20261016-5de84e0f`；精确来源事件：`FD-004-000005-changes-requested`，claim 前已确认 pending/worker。
- 第 1 次评审结果为 changes-requested，具体问题可修复，未达到 3 次限制；保留 R1 报告，不覆盖历史结论。
- 就绪计划基线 `fff50cc`；R1 评审提交 `f5b187d`；修复代码提交 `3a987e7`。

新增完成 1.3.2：按本地 Jackson DecimalUtils.extractSecondsAndNanos(..., true) 规则处理负数：整数秒为负时对剩余纳秒取绝对值；整数秒为 0 时保持负小数符号。静态推导 `-2.500000000` → seconds=-2/nanos=+500000000，与旧 Java 网关恢复同一时间；`-0.5` → seconds=0/nanos=-500000000；正数、整数、ISO 字符串及长度/指数/范围检查路径保留。解决 R1/P2 对 AC-02/03 的具体违反，没有修改客户端、SQL 或范围外模块，没有降低兼容要求。

新增完成 1.4.1：退出 defer 先 stop() 取消 root context，再关闭连接池。除正常信号外，异常 Serve/listen 失败也先取消借出连接的查询，避免 Close 等待完整配置请求期限。此项消除 R1 记录的非阻塞退出延迟风险；真实平台行为未运行验证。

同步 Gateway README 记录负数历史规则，FD 增加稳定子项及真实完成状态。R1 实施报告的其余 AC-01—AC-06 静态证据仍有效，未声称独立评审或交付已完成。

实际命令：FD 根目录 `git diff --check` 通过；gateway 目录第三次离线 `GOTOOLCHAIN=local CGO_ENABLED=0 GOPROXY=off GOSUMDB=off go build -mod=readonly -o ../.cache/compile/pg-eventbus-gateway.exe .` 成功，覆盖负数时间和退出修复。前两次编译保留为历史证据，第三次因实际代码修复而执行。只读源码/本地字节码推导，不执行场景测试。

未运行测试、HTTP/数据库、应用、Maven、最终构建、formatter、linter/vet、跨平台运行、网络、推送或部署。无持续 blocker/反馈记录；该兼容问题已在正常评审修复循环内处理，没有 Gate override。可复用经验是迁移时核对客户端默认序列化与负时间边界；无需在此 FD 修改技能或模板。

剩余风险：实际数据库/HTTP互通、并发/租约及信号平台行为未验证；旧 Spring 文件和错误页需迁移，JDBC 专有参数不转换，数值扩展格式/超大请求受文档限制；无认证及原 SQL 清理边界保持。独立 Reviewer R2 待处理。
