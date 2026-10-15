# 文档索引与维护规则

以下使用文档描述当前代码。最后一次源码核对日期：2026-10-16；Gateway 已迁移至 Go，本轮只做离线编译与静态核对，没有运行网关、数据库安装、业务命令或 Flyway。

| 文档 | 内容 | 对应实现 |
|---|---|---|
| [模型与实现](PG-EventBus.md) | 表、路由语义、锁、重试、清理及当前限制 | [SQL 来源](sql/)、[Java 核心服务](../client/java/db/src/main/java/me/asu/pg_eventbus/core/)、[Gateway 服务](../gateway/internal/store/store.go) |
| [配置与 CLI](pg-eventbus-schema-and-cli-addendum.md) | 各程序配置边界、初始化、命令、输出和退出码 | [CLI 参数](../cli/internal/app/command.go)、[配置解析](../cli/internal/app/config.go)、[数据库调用](../cli/internal/db/)、[CLI README](../cli/README.md) |
| [客户端指南](pg-eventbus-client-guide.md) | 语言与接入方式、装配、接口、HTTP 路由及示例 | [客户端目录](../client/)、[Gateway 路由及请求](../gateway/internal/api/api.go) |
| [Gateway 配置与迁移](../gateway/README.md) | Go 构建、环境变量、启动/退出、请求验证和错误边界 | [配置](../gateway/internal/config/config.go)、[入口](../gateway/main.go) |
| [JS/TS 客户端](../client/javascript/README.md) | ESM及类型声明、DB/HTTP接入、bigint精度/时间/超时责任 | [DB](../client/javascript/db/index.js)、[HTTP](../client/javascript/gateway/index.js) |
| [SQL 来源与安装顺序](sql/README.md) | 22 个编号来源、生成流程、手工安装 | [生成器](../cli/internal/cmd/gensql/main.go)、[内嵌入口](../cli/sql/script.go)、[生成模板](../cli/sql/init.sql) |
| [未来 Flyway 接入](flyway-adoption.md) | 未来迁移来源、V/R 文件、已有库 baseline 和 CLI 职责切换 | 当前尚未实现 Flyway 集成；三位安装编号不是迁移版本 |

当前边界：CLI 是 Go/pgx 独立程序，使用 TOML；Gateway 是 Go/pgx 独立程序，只读取环境变量，并保留旧 Spring 环境别名。客户端按语言保存在 `client/<语言>/db` 与 `client/<语言>/gateway`。Go/Python 直连客户端仍使用外部 psql，HTTP 客户端使用 Gateway，不能从 CLI/Gateway 使用 pgx 推断直连客户端也使用 pgx。

## 随代码更新文档

- 修改目录、构建方式或依赖边界时，同时更新根 README、相关模块 README 和本索引。
- 修改配置项、默认值、参数、环境变量、退出码时，同时更新 CLI README 和配置指南；Gateway 与 Java 直连配置分别核对，不共享默认值表。
- 修改客户端方法、DTO、HTTP 路由或锁行为时，同时更新客户端指南及模型文档中的相关行为说明。
- 修改 SQL 时只编辑 `docs/sql/` 编号源文件，再按 [SQL 说明](sql/README.md#生成-cli-内嵌-sql)生成并检查 `cli/sql/init.sql`；新增或重命名脚本时同步更新文档中的顺序表和链接。生成检查只验证文本一致性，不验证 SQL 执行行为。
- 提交前检查相对链接、示例中的文件路径，以及 `git diff --check`；文档必须区分已实现行为、已知限制和未来方案。静态核对不能替代真实数据库验证。

[FD 索引](features/FEATURE_INDEX.md)记录设计生命周期；`features/archive/` 中的设计、实施报告与评审是当时的历史证据，保留原样，现行行为以使用文档及代码为准。[FD 模板](templates/TEMPLATE.md)是新设计模板，不是运行说明。原始 `PG-EventBus.mht` 同样属于历史资料。
