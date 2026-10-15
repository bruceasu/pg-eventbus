# FD-003 SQL 来源与生成流程实现报告

<!-- aiw-data: FD-003-implementation-r1.json -->

- Worker session `fd003-worker-20261015-sql`；精确认领事件 `FD-003-000003-design-ready`。
- 父分支 main，隔离基线 `c188b2f`；分支 feature/FD-003，工作区 .wt/FD-003。四个工作项已实现，等待独立 Reviewer。

## 实现与验收

| 条件 | 证据 |
|---|---|
| AC-01 | 22 个 SQL 来源按原合并顺序改为 001、010…200、990；990 中角色集中到 v_role。静态抽取当前来源、生成物与基线函数定义，20 个逐段完全相同 |
| AC-02 | 唯一可编辑 SQL 来源为 docs/sql；gensql 按排序读文件，只对 001 两条语句和 990 角色变量的单次精确锚点转换；不含函数业务定义，不按分号解析。来源仍可用于人工安装，生成物保留 CLI 的两个 schema 标记、一个角色字符串标记和单一外围事务 |
| AC-03 | 生成器检查文件命名/类型、编号唯一、首尾边界、来源已有标记和锚点出现次数；全部内容组装后再写同目录临时文件并替换。check 分支只比对字节；go:generate 接入，生成物仍提交/内嵌。Windows 本机实际生成与一致性检查通过 |
| AC-04 | docs/sql/README.md 提供 22 项顺序、同连接/事务、人工副本参数、生成/检查/构建及编号含义；docs/flyway-adoption.md 提供新库/已有库、V/R、baseline 与保护、schema/角色/事务适配和职责切换；现有说明及链接已更新 |
| AC-05 | 本机离线编译 CLI 与 gensql 通过；Gateway、客户端、CLI 业务/DB/配置/JSON/SQL执行及归档无差异。未执行数据库命令或测试，不声称 Flyway 已启用 |

角色授权仍是同一组 REVOKE/GRANT/ALTER DEFAULT PRIVILEGES，默认角色 app_role，只将写死标识符改为 format(%I) 引用单个 v_role 变量。CLI 已有 Initialize 按 SQL 字符串语境渲染 owner_literal，无需改动运行时代码。

## 实际执行

1. 读取当前目录、合并来源、初始化实现、工作流和父工作区；创建/认领 FD 及隔离 worktree，按项提交。读取 Redgate 官方 Versioned/Repeatable/Baseline/baselineOnMigrate 文档以核对未来说明，文档保留直接链接；未下载依赖。
2. 在 cli/ 设置 GOTOOLCHAIN=local、GOPROXY=off，执行 `go generate ./sql`；随后 `go run ./internal/cmd/gensql -source ../docs/sql -output sql/init.sql -check`，通过。源注释收尾后重复生成和检查，通过。这些命令只处理文件文本，未执行安装 SQL、Go CLI 或数据库。
3. 静态文本对照：从 `git show c188b2f:cli/sql/init.sql` 抽取 20 个完整函数，与编号来源及生成物逐段比较，完全相同；生成物列出的 22 个来源与文件排序一致；两个 schema_ident、一个 owner_literal，单个外围 BEGIN/COMMIT 符合约定。
4. 在 cli/ 设置 GOTOOLCHAIN=local、CGO_ENABLED=0、GOPROXY=off，执行 `go build -o ../.cache/compile/ ./...`，CLI 和生成工具编译通过，临时产物未启动，不是发布包。
5. `git diff --check` 通过；受保护路径的 git diff --name-only 无输出；当前 Markdown 本地链接静态核对无缺失目标。旧路径/人工同步说明检索无匹配（rg 的 1 表示未匹配，不是验证失败）。

## 边界与风险

未生成/运行测试，未做无效输入运行探测、数据库安装、psql、Flyway、Linux 运行、formatter、linter 或发布构建。文件错误与锚点保护分支只有静态证据；跨平台文件替换、真实 PostgreSQL 权限/搜索路径及将来 Flyway 接管仍无运行证据。

go generate 不随 Go build 隐式执行，已明确要求改 SQL 后生成、check 并一起提交；单独 cli 源码仍可直接构建已提交产物。未来数据库版本管理是单独接入工作，不会由三位编号自动获得。本次没有活动 blocker，未修改验收或把未执行检查当作通过。
