# FD-002 独立评审 R1

<!-- aiw-data: FD-002-review-r1.json -->

结论：**verification-passed**。未发现阻断问题。按用户最新决定评估原生 TOML 配置；FD-001 的 Spring 兼容与环境拒绝规则已被本 FD 明确替代，无需保留。

Reviewer session：`fd002-reviewer-20261015-98eac671`；已核对 pending 状态并认领来源事件 `FD-002-000004-implementation-ready`。Worker session `fd002-worker-20261015-toml` 与本会话独立。当前周期第 1 次评审；被审 HEAD `7245d65`，基线 `2939b1d`，FD revision 4。

## 验收依据

| 条件 | 独立静态检查结果 |
|---|---|
| AC-01 | `configurationPath` 按 --config、PGEVENTBUS_CONFIG、运行目录 pg-eventbus.toml 选择，显式空路径/非 TOML 拒绝；默认不存在允许继续，显式缺失经 fileError 返回 1。readTOML 最多读取 1 MiB+1 后拒绝超限，文件/解析错误使用固定诊断，没有原始片段或凭证 |
| AC-02 | optionKeys、environmentKeys、TOML 字段与文档映射一致，按 rank 1/2/3 覆盖。指针区分缺失与显式空密码；整数先由 TOML 解为 int64，再按正 int32 校验；timeout 为字符串 duration、拒绝负值。databaseOptions 保留单独凭证与 URL 内凭证的来源排序及同级覆盖；默认 schema/批量/TTL/timeout 与 owner 后备未变 |
| AC-03 | 旧 YAML/properties 加载、Spring 环境扫描、driver 和 JDBC 转换已删除。URL 限定原生 scheme；旧点分参数和显式旧格式路径拒绝，旧环境/自动文件不参与加载。TOML typed struct、Undecoded、精确 Keys 白名单共同拒绝类型错误、未知/大小写错误字段；解析错误不转发。`${...}` 保留普通字符串 |
| AC-04 | TOML 示例、help、新参数/环境映射及根说明/配置指南一致。Java 配置被明确区分；Gateway、客户端和 FD-001 归档未改 |
| AC-05 | command.go 只改选项清单/help；DB、SQL、Run、输出和 main 无差异，命令语义、JSON、事务、退出码和无 ORM 边界保持。Worker Dual 报告记录离线本机编译成功及实际跳过项，没有把编译等同运行验证 |

独立读取本地锁定 `github.com/BurntSushi/toml v1.4.0` 的 decode.go、meta.go、parse.go：`indirect` 分配指针；unifyString 只接受字符串，unifyInt 只接受 int64；结构体只解表；重复键/重复表路径报错。结构体匹配允许 EqualFold，而 CLI 补充 exact key 白名单避免静默兼容错误大小写；Undecoded 拒绝未绑定内容。没有执行解析器或 CLI。

配置优先级以最终选中值为准；高优先级 URL 内凭证不会被低优先级 TOML 的独立凭证覆盖，同级/更高独立凭证可覆盖且显式空值得以保留。环境变量名只来自新映射。保留 pgx 上游测试依赖的 YAML go.sum 记录并不引入 CLI YAML 加载器，go.mod 已移除直接 YAML 依赖。

## 实际命令与边界

执行了 `Get-Content -Encoding utf8` 读取 FD、Worker Markdown/JSON、完整配置/命令源码、说明与示例、锁定 TOML 解析器；`git diff --stat 2939b1d`、`git diff 2939b1d -- <paths>`、`git status --short`、`git rev-parse HEAD`、`rg` 搜索旧映射和解析器重复键路径；`aiw fd resume FD-002` 显示 exact pending Reviewer 事件后执行 `aiw fd claim FD-002 FD-002-000004-implementation-ready --session fd002-reviewer-20261015-98eac671`。初次尝试读取 worktree 下 .ai/fd/FD-002/latest.json 发现该位置不存在，随后使用正常 CLI 查询/认领，无手写 receipt。

`git diff --check` 无错误。`git diff --name-only 2939b1d -- gateway client docs/sql cli/internal/db cli/sql cli/internal/app/run.go cli/internal/app/output.go cli/main.go docs/features/archive` 无输出，确认受保护实现与历史记录不变。随后写入并提交评审证据及 FD Verification，以原认领事件为 source 发出 verification-passed。

未运行测试、程序、数据库操作、额外编译、lint、formatter、发布构建、跨平台或性能检查，未读取可选 fd-test 报告；未改实现。TOML/环境/参数组合、真实 PostgreSQL 和双平台实际行为尚未运行验证，保留残余风险。本次通过支持本地交付与归档，不代表部署/发布验证完成。
