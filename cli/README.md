# 维护 CLI

独立 Go 1.25 module，使用 pgx 直接连接 PostgreSQL，无 ORM、JVM、psql、CGO 或项目内模块依赖。构建及运行说明如下；这些命令不是 FD 默认流程自动执行的检查。

```powershell
cd cli
$env:GOTOOLCHAIN = 'local'
$env:CGO_ENABLED = '0'
go build -o bin/pg-eventbus-cli.exe .
$env:PGEVENTBUS_DATABASE_URL = 'postgresql://localhost:5432/pg_eventbus'
$env:PGEVENTBUS_DATABASE_USER = 'postgres'
$env:PGEVENTBUS_DATABASE_PASSWORD = 'change-me'
$env:PGEVENTBUS_SCHEMA = 'analytics'
.\bin\pg-eventbus-cli.exe db init app_role
.\bin\pg-eventbus-cli.exe partition ensure-current
.\bin\pg-eventbus-cli.exe partition ensure-future
.\bin\pg-eventbus-cli.exe maintenance run
```

Linux amd64：在本目录执行 `CGO_ENABLED=0 GOTOOLCHAIN=local GOOS=linux GOARCH=amd64 go build -o bin/pg-eventbus-cli .`；Windows 目标使用 `GOOS=windows GOARCH=amd64`。无需复制仓库，二进制自带 `sql/init.sql`。`app_role` 须提前创建。该 SQL 是带渲染标记的安装模板，不应直接手工执行；手动安装使用 `docs/sql/` 的拆分脚本。

保留 `db init`、`partition`、`event send|publish`、`event get|poll`、`event set-cursor|commit`、`event recover`、`maintenance run`；不预建分区，不自动提交位点或释放租约。使用 `--help` 查看语法，help 不连接数据库。

配置使用 TOML。将 [pg-eventbus.example.toml](pg-eventbus.example.toml) 复制为运行目录的 `pg-eventbus.toml`，也可以通过 `--config <path>` 或 `PGEVENTBUS_CONFIG` 指定文件（参数优先）。默认文件缺失时可仅使用环境变量/参数；显式文件缺失则报错。文件最大 1 MiB，未知键、重复键、错误类型或无效 TOML 均报配置错误，诊断不包含源文件内容。

```toml
timeout = "0s"

[database]
url = "postgresql://localhost:5432/pg_eventbus"
user = "app_role"
password = "change-me"
schema = "analytics"

[consumer]
batch_size = 100
lock_ttl_seconds = 30
```

| 参数 | 环境变量 | TOML 键 |
|---|---|---|
| `--database-url` | `PGEVENTBUS_DATABASE_URL` | `database.url` |
| `--database-user` | `PGEVENTBUS_DATABASE_USER` | `database.user` |
| `--database-password` | `PGEVENTBUS_DATABASE_PASSWORD` | `database.password` |
| `--schema` | `PGEVENTBUS_SCHEMA` | `database.schema` |
| `--batch-size` | `PGEVENTBUS_BATCH_SIZE` | `consumer.batch_size` |
| `--lock-ttl-seconds` | `PGEVENTBUS_LOCK_TTL_SECONDS` | `consumer.lock_ttl_seconds` |
| `--timeout` | `PGEVENTBUS_TIMEOUT` | `timeout` |

配置值优先级为参数 > 环境变量 > TOML > 默认值。默认 schema bus、批大小 100、TTL 30 秒、timeout 0；数值必须为正 int32，timeout 使用 Go duration，如 `30s`、`2m`。显式空密码与缺失值不同；单独用户名/密码覆盖同级或低优先级 URL 中的凭证，高优先级 URL 内凭证不被低优先级文件覆盖。初始化授权角色继续由 `db init <owner>` 或 `PGEVENTBUS_DB_OWNER` 提供。

参数支持 `--key=value` 和 `--key value`，可位于命令前后；`--` 后全部按位置参数处理。JSON 必须作为完整单个参数传入，内部字符串不解析为选项。密码优先通过环境变量传入。

URL 只接受原生 `postgresql://` 或 `postgres://`，连接参数使用 PostgreSQL/pgx 名称，例如 connect_timeout。TOML 键区分大小写，`${...}` 是普通字符串，不做变量展开。旧 YAML/properties、application.*、Spring/BUS 环境映射、driver 配置、点分参数和 JDBC URL 转换均已移除；旧环境变量作为无关环境被忽略，旧文件不自动读取，显式旧文件格式或点分参数会报错。Gateway/Java 客户端仍使用各自配置，不能拿其配置直接给 CLI。

成功的事件/维护操作向 stdout 输出单行 JSON，时间保留旧 CLI 的数值型 epoch seconds，包含纳秒小数；ID 为 int64，payload 大整数不经浮点数解析。错误与诊断只写 stderr；初始化成功保留 `db init completed` 文本。退出码：0 成功/帮助，1 运行错误，2 参数/配置错误，130 Ctrl+C。租约获取失败或 commit=false 是正常业务结果，退出码仍为 0。

`--timeout 30s` 可限制一次连接和命令的总执行时间，默认 0 不新增执行时限；连接建立默认最多 10 秒。Ctrl+C 取消请求。初始化失败会尝试在原连接回滚，随后关闭连接，不自动重试。

回退时使用迁移前提交 `c7098dd`（或其父提交 `1be8739`）中的 Java CLI，在该版本独立检出后按原 Maven 说明构建/使用已有 JAR；不要把旧 Java 源码复制回当前 module。数据库函数不因程序语言切换而改变。

更多命令见[CLI 指南](../docs/pg-eventbus-schema-and-cli-addendum.md)。
