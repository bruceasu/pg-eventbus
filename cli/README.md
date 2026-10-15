# 维护 CLI

独立 Go 1.25 module，使用 pgx 直接连接 PostgreSQL，无 ORM、JVM、psql、CGO 或项目内模块依赖。构建及运行说明如下；这些命令不是 FD 默认流程自动执行的检查。

```powershell
cd cli
$env:GOTOOLCHAIN = 'local'
$env:CGO_ENABLED = '0'
go build -o bin/pg-eventbus-cli.exe .
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/pg_eventbus'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:SPRING_DATASOURCE_PASSWORD = 'change-me'
$env:BUS_SCHEMA = 'analytics'
.\bin\pg-eventbus-cli.exe db init app_role
.\bin\pg-eventbus-cli.exe partition ensure-current
.\bin\pg-eventbus-cli.exe partition ensure-future
.\bin\pg-eventbus-cli.exe maintenance run
```

Linux amd64：在本目录执行 `CGO_ENABLED=0 GOTOOLCHAIN=local GOOS=linux GOARCH=amd64 go build -o bin/pg-eventbus-cli .`；Windows 目标使用 `GOOS=windows GOARCH=amd64`。无需复制仓库，二进制自带 `sql/init.sql`。`app_role` 须提前创建。该 SQL 是带渲染标记的安装模板，不应直接手工执行；手动安装使用 `docs/sql/` 的拆分脚本。

保留 `db init`、`partition`、`event send|publish`、`event get|poll`、`event set-cursor|commit`、`event recover`、`maintenance run`；不预建分区，不自动提交位点或释放租约。使用 `--help` 查看语法，help 不连接数据库。

配置优先级为命令行 > 环境变量 > 配置文件 > 默认值。保留 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`BUS_SCHEMA`、`BUS_BATCH_SIZE`、`BUS_LOCK_TTL_SECONDS` 和 `PGEVENTBUS_DB_OWNER`。URL 支持 `jdbc:postgresql://`、`postgresql://`、`postgres://`。默认 schema `bus`、批大小 100、TTL 30 秒。显式用户名、密码覆盖同级或低优先级 URL 中的凭证；高优先级 URL 内凭证不会被低优先级文件覆盖；空密码可显式指定。

支持 `--spring.datasource.url=...`、`--spring.datasource.username=...`、`--spring.datasource.password=...`、`--bus.schema=...`、`--bus.batch-size=...`、`--bus.lock-ttl-seconds=...`，全局选项可置于命令前后。`--` 后全部按位置参数处理；JSON 必须作为完整的单个参数传入，内部字符串不解析为选项。避免在命令行传密码，优先使用环境变量。

配置文件可用 `--config <path>` 选择；否则只在当前目录寻找 `application.yml`、`application.yaml`、`application.properties`，同时存在多个时要求显式选择。支持上述 datasource 和 bus 键，兼容值为 `org.postgresql.Driver` 的 driver-class-name。YAML 支持单文档、标量和嵌套映射，不支持别名、序列、重复键。properties 支持普通 key=value，不支持转义、续行或 Unicode 转义，需要特殊字符时使用 YAML。

```yaml
spring:
  datasource:
    url: postgresql://localhost:5432/pg_eventbus
    username: app_role
    password: change-me
bus:
  schema: analytics
  batch-size: 100
  lock-ttl-seconds: 30
```

不复刻 Spring profiles、配置导入、占位符展开、任意 Hikari 属性；未知配置键会明确报错。在连库前拒绝环境中显式设置的 `SPRING_PROFILES_*`、`SPRING_CONFIG_*`、`SPRING_APPLICATION_JSON`，以及未支持的 `SPRING_DATASOURCE_*`（包括 Hikari），即使值为空也报配置错误，不回退到基础配置执行命令。仅保留数据源 URL、USERNAME、PASSWORD、DRIVER_CLASS_NAME；后者只能是 `org.postgresql.Driver`。无关操作系统环境变量不受影响。Java 客户端使用的 `bus.enabled`、`bus.max-retries` 不属于 CLI 配置。JDBC 查询参数仅支持 sslmode、sslrootcert、sslcert、sslkey、application_name、connectTimeout、user、password、options；connectTimeout 转为 connect_timeout，其它 JDBC 参数报错。原生 URL 参数由 pgx 校验。删除旧 `PGEVENTBUS_SQL_DIR` 和向上查找脚本的回退路径。

成功的事件/维护操作向 stdout 输出单行 JSON，时间保留旧 CLI 的数值型 epoch seconds，包含纳秒小数；ID 为 int64，payload 大整数不经浮点数解析。错误与诊断只写 stderr；初始化成功保留 `db init completed` 文本。退出码：0 成功/帮助，1 运行错误，2 参数/配置错误，130 Ctrl+C。租约获取失败或 commit=false 是正常业务结果，退出码仍为 0。

`--timeout 30s` 可限制一次连接和命令的总执行时间，默认 0 不新增执行时限；连接建立默认最多 10 秒。Ctrl+C 取消请求。初始化失败会尝试在原连接回滚，随后关闭连接，不自动重试。

回退时使用迁移前提交 `c7098dd`（或其父提交 `1be8739`）中的 Java CLI，在该版本独立检出后按原 Maven 说明构建/使用已有 JAR；不要把旧 Java 源码复制回当前 module。数据库函数不因程序语言切换而改变。

更多命令见[CLI 指南](../docs/pg-eventbus-schema-and-cli-addendum.md)。
