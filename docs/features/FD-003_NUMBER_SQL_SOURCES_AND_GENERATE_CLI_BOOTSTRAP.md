# FD-003: SQL 顺序编号、唯一来源与初始化脚本生成

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

docs/sql 的 22 个功能拆分脚本缺少可见执行顺序，cli/sql/init.sql 另有一份人工同步副本。用户同意采用三位顺序编号、单一 SQL 维护来源、自动生成，并要求留下当前使用与未来 Flyway 接入说明。

## Options and decision

仅重命名不能避免 SQL 漂移；直接套 Flyway 命名却没有版本历史会混淆安装顺序与升级版本。采用三位编号源文件 + 标准库 Go 生成器 + 已提交生成物/一致性检查。当前不引入 Flyway 或迁移执行引擎，未来接入规则单独记录。

## Solution

源文件仅维护在 docs/sql，按现有 cli/sql/init.sql 的顺序改名：001_bootstrap、010_register_consumer、020_create_month_partition，之后每步递增 10 至 200_maintenance，990_grant_access 最后。数字唯一且三位，合法 SQL 文件按文件名排序，必须以 001_bootstrap.sql 开头、990_grant_access.sql 结尾。

保留源文件可手工安装：bootstrap 仍默认创建/选择 bus，grant 将 app_role 集中为 DO 中单个 v_role 字符串，所有授权通过 format(%I) 引用变量；20 个函数定义不改。

新增 cli/internal/cmd/gensql（标准库、无额外依赖）。go:generate 放在 cli/sql/script.go，生成 cli/sql/init.sql，头部明确 generated/DO NOT EDIT，包含 BEGIN、按顺序来源注释和 COMMIT。生成器仅对 bootstrap 的两条 schema/search_path 语句及 grant 的角色变量初始化做精确、单次锚点转换，产生原有 schema_ident / owner_literal 标记。锚点缺失或重复立即失败，不按分号解析 SQL，不把数据库函数定义写进生成器。校验文件名、重复编号、首尾边界和非法模板标记；所有检查完成后通过同目录临时文件重命名写入，失败不覆盖已有文件。

提供 -check 模式，仅比较将生成的内容和已提交产物，过期/缺失时非零退出。go generate ./sql 是明确生成动作，Go build 本身不会自动调用它；文档要求改 SQL 后生成、检查并提交两者。生成物仍提交并内嵌，使只复制 cli 源码也可构建/运行；生成器需要完整仓库源码，不增加 CLI 运行时依赖。

docs/sql/README.md 列出全部顺序、同连接与事务要求、schema/角色替换、生成/检查/构建流程、编号只表示顺序。docs/flyway-adoption.md 记录未来版本化迁移与 repeatable 的区别、冻结初始快照、已有数据库比对后显式 baseline、schema/search_path/角色及渲染适配、Flyway 接管后避免双重 schema 管理；不执行任何 Flyway 或数据库命令。现有文档与生成来源注释一并更新，历史 FD 不改。

## Scope

范围内：22 个文件重命名、授权参数集中、生成器/检查入口、生成产物和说明。范围外：函数业务变更、CLI 命令/配置接口变更、Flyway 真正接入、数据库升级、Gateway/客户端、测试、运行验证或发布。继续现有 FD 隔离实施、独立评审与本地交付；用户已授权执行生成动作，生成/比较产物不等于运行 CLI 或数据库测试。

## Work items

| ID | 结果 | 规模/难度/预计 | 依赖 | 完成标准 |
|---|---|---|---|---|
| 1.1 | 编号 SQL 来源 | 小/低/≤2小时 | 无 | 22 个脚本顺序与旧合并脚本一致，角色变量集中，20 个函数定义不变 |
| 1.2 | 生成器与产物检查 | 小/中/≤半天 | 1.1 | 有确定顺序/锚点验证、原子输出、check 模式、go:generate，实际生成并检查一致性 |
| 1.3 | 当前安装与未来 Flyway 文档 | 小/中/≤半天 | 1.2 | 全部顺序、同连接、生成流程、未来基线/版本/checksum/R__、权限/搜索路径/接管边界清晰；旧链接更新 |
| 1.4 | 编译与证据交接 | 小/低/≤2小时 | 1.3 | 静态核验与一次本机 CLI/生成器编译，Dual 报告保留真实检查和风险，独立 Reviewer |

### TODO

- [ ] 1.1 编号 SQL 来源。
- [ ] 1.2 生成器和一致性检查。
- [ ] 1.3 当前与未来说明。
- [ ] 1.4 编译和静态证据。

## Acceptance

- AC-01：源 SQL 22 个、三位且唯一编号，现有安装顺序和 20 个函数定义保留。
- AC-02：唯一维护来源在 docs/sql；生成器不复制业务定义，仅限定替换安装参数；生成 SQL 的角色引用、search_path、事务及 CLI 渲染契约兼容。
- AC-03：生成确定、check 可检测差异；输入无效时不覆盖输出，源编号或锚点错误有明确失败路径；CLI 仍内嵌产物且独立运行。
- AC-04：完整执行顺序和生成/构建命令可查；未来 Flyway 区分初装与演进，包含已有部署接管及权限/schema 注意事项，当前没有假装启用 Flyway。
- AC-05：CLI/生成器编译、静态源与产物检查有真实记录；无数据库或测试执行，Gateway、客户端和历史归档不改。

## Verification

生成仅使用标准库，设置 GOTOOLCHAIN=local、GOPROXY=off 后执行 go generate ./sql，以及 go run ./internal/cmd/gensql -source ../docs/sql -output sql/init.sql -check（cli 目录）。这些动作仅处理仓库 SQL 文本，不执行 SQL。

默认静态核验 + 编译：比较基线与新来源/生成物的 20 个函数定义，核对来源序列及仅有的安装转换、事务/角色语境和文件写入失败分支；执行 git diff --check。一次 GOTOOLCHAIN=local CGO_ENABLED=0 GOPROXY=off go build -o ../.cache/compile/ ./... 同时编译 CLI 和生成工具，不启动临时程序或数据库。未授权测试/formatter/linter/最终构建；不把生成器 -check 当作数据库或集成测试。

未来 Flyway 文档基于本次只读查阅的 Redgate 官方 versioned/repeatable/baseline/baselineOnMigrate 说明，链接留在文档。Flyway 实际执行、已有数据库基线真实性、跨平台原子替换和 PostgreSQL 运行效果尚未验证，保留风险。

## Sources

- Issue: none
- 用户本轮明确同意三位编号、单一来源、自动生成，并要求当前及未来 Flyway 文档。
- 基线 main 699e293；cli/sql/init.sql、docs/sql、cli/internal/db/initialize.go。
- Planner source event FD-003-000002-design-requested；session fd003-planner-20261015-sql。
