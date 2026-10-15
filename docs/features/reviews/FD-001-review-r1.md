# FD-001 独立评审 R1

<!-- aiw-data: FD-001-review-r1.json -->

结论：**changes-requested**。存在一项阻断问题，修复后可重新提交独立评审。

Reviewer session：`fd001-reviewer-20261015-83ea1c9f`；来源事件：`FD-001-000004-implementation-ready`，本会话已精确认领。实现基线 `c7098dd`，被审实现提交 `0649490`；同时读取事件产生的 FD revision 4 和索引状态变更。Worker session 为 `fd001-worker-20261015-2f9c053a`，与本会话不同。这是当前周期第 1 次 Reviewer outcome。

## 发现

### R1-01 / P2：保留 Spring 环境控制项被静默忽略

位置：`cli/internal/app/config.go:60`，`environmentKeys` 定义在第 31 行。对应 AC-03、AC-08，以及配置兼容章节“相关保留 Spring 控制键若出现，返回明确的不支持提示”。

环境加载仅对已知六个键调用 `os.LookupEnv`，不检查 Spring 控制环境变量。迁移前常用的 `SPRING_PROFILES_ACTIVE=prod` 或 `SPRING_CONFIG_LOCATION=...` 出现时，程序继续读取当前目录基础配置并执行维护 SQL；不会报告 profile/配置定位不受支持。`SPRING_DATASOURCE_HIKARI_*` 等也被忽略。命令行及文件未知键已有拒绝逻辑，环境来源缺少同等边界。此路径直接由源码可确认，不依赖尚未授权的运行测试。

修复建议：连接数据库前对相关保留环境键作明确检测，返回脱敏的不支持配置诊断及退出码 2。最小明确范围为 `SPRING_PROFILES_*`、`SPRING_CONFIG_*`、`SPRING_APPLICATION_JSON` 和 `SPRING_DATASOURCE_*` 中超出声明兼容数据源键的设置（包括 `SPRING_DATASOURCE_HIKARI_*`）。`SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 必须继续支持；driver-class-name 若选择支持则仅接受 PostgreSQL 驱动并写明，否则显式拒绝。仅检测保留控制命名空间，无需拒绝任意不相关操作系统环境变量；检测到显式设置时，包括空字符串，避免静默误判。不得把配置值、JSON 内容或秘密写入诊断。同步修正实现报告中“不支持项均有确定处理”的结论，记录实际覆盖范围。此修复不需要新增测试或扩大运行授权。

### R1-02 / 低优先级文档建议

FD 初始化脚本章节将历史迁移描述写成 `cli/sql/init.sql` 迁至同一路径。原路径实际为 `cli/src/main/resources/init.sql`。建议恢复历史来源，使审计说明与实际 rename 一致；此项本身不阻断功能验收。

## 验收核对

| 验收 | 静态结论及依据 |
|---|---|
| AC-01 | 独立 module，只有外部依赖；help 在配置/连接之前返回；无外部程序转调或 ORM |
| AC-02 | 命令/别名及本地参数校验齐全；false 正常返回；错误分类、Ctrl+C context 和清理路径符合设计；不将未运行的平台行为记为通过 |
| AC-03 | 文件选择、优先级、空密码、JDBC 白名单及凭证来源排名有实现；保留 Spring 环境控制项遗漏，见 R1-01 |
| AC-04 | 函数以 pgx.Identifier 分别引用 schema/函数名，数据参数绑定；安装标记单独渲染，owner 在 DO 内作为 format(%I) 参数，混合大小写得到引用 |
| AC-05 | 完整嵌入事务脚本；同一连接 simple protocol 执行，无分号拆分/套事务；出错后独立清理 context 尝试 ROLLBACK，再关闭连接 |
| AC-06 | 与基线 Java 数据库调用逐一对照，函数与参数一致；先获锁再 poll，无自动提交/释放；20 个 SQL 函数定义独立文本比对完全一致 |
| AC-07 | DTO 字段与基线一致；int64/RawMessage、nullable target、显式 null 和非 nil 空 events 有明确路径；数字时间编码无 float64。旧 Jackson 编码依据为 Worker 的本地字节码核对，本评审未运行 Java/Go 时间对照 |
| AC-08 | JSON、时间、数字、配置先于连接校验，连接/SQL 错误脱敏；环境配置遗漏见 R1-01；资源关闭使用独立 context |
| AC-09 | 根 POM 仅移除 CLI；Gateway/client/docs/sql 无差异；迁移入口、配置限制、Windows/Linux 构建及回退文档齐全 |
| AC-10 | Dual Worker 报告真实区分 compile-only 和未执行检查；没有用 Worker 自评替代独立 Reviewer。R1-01 修复后更新相应证据 |

## 实际操作与边界

实际只执行文档/源码读取、事件查询与认领，以及 Git 静态检查和本评审证据写入：`Get-Content -Encoding utf8`、`Get-ChildItem`、`rg`/`rg --files`、`git show c7098dd:<path>`、`git diff --stat c7098dd`、`git diff c7098dd -- <paths>`、`git status --short`、`git log --oneline -13`、`git diff --check`、`git diff --name-only c7098dd -- gateway client docs/sql`、`aiw fd show FD-001`、`aiw fd claim ... --session fd001-reviewer-20261015-83ea1c9f`、`aiw fd emit --help`。以 PowerShell 正则抽取基线和当前脚本的完整函数定义：20 对均完全相同。读取本地 pgx v5.7.6 JSON 编码实现确认 RawMessage 专门路径。`git diff --check` 无错误，范围外路径无差异。

未运行测试、应用、数据库操作、编译、发布构建、lint、格式化、跨平台或性能检查；未读取任何可选 fd-test 报告。未改实现代码。提交本评审报告、JSON、FD Verification 和事件产生的索引状态后，以本认领事件为 source 发出 changes-requested。

剩余风险：PostgreSQL 初始化回滚、租约争用、取消、TLS/认证和 JSONB/时间往返尚无运行证据；Linux 与 Windows 实际运行尚未验证。这些是记录中的运行风险，不是本次因缺少可选测试而新增的阻断条件。
