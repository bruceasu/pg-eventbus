# HTTP Gateway

独立 Go 1.25 HTTP 程序，使用标准库 net/http 和 pgx 连接池调用 PostgreSQL 函数，不依赖 JVM、psql、CLI 或客户端模块。先使用 CLI 初始化数据库，再从本目录构建：

```powershell
$env:GOTOOLCHAIN = 'local'
$env:CGO_ENABLED = '0'
go build -o bin/pg-eventbus-gateway.exe .
$env:PGEVENTBUS_DATABASE_URL = 'postgresql://localhost:5432/pg_eventbus'
$env:PGEVENTBUS_DATABASE_USER = 'app_role'
$env:PGEVENTBUS_DATABASE_PASSWORD = 'change-me'
$env:PGEVENTBUS_SCHEMA = 'analytics'
.\bin\pg-eventbus-gateway.exe
```

生成的程序可单独复制部署。Linux/macOS 用 `go build -o bin/pg-eventbus-gateway .` 构建。`--help` / `-h` 不读取配置或连接数据库；其它参数返回退出码 2。配置错误返回 2，连接/监听/服务失败返回 1，正常退出返回 0。Ctrl+C / SIGTERM 取消数据库请求，再等待最多 10 秒 HTTP 退出，超时强制关闭连接。

配置只读环境变量；不加载 Spring application.yml/properties、TOML 或 CLI 配置文件：

| 原生变量 | 默认值 |
|---|---|---|
| `PGEVENTBUS_DATABASE_URL` | 必填 |
| `PGEVENTBUS_DATABASE_USER` | URL/pgx 默认 |
| `PGEVENTBUS_DATABASE_PASSWORD` | URL/pgx 默认 |
| `PGEVENTBUS_SCHEMA` | bus |
| `PGEVENTBUS_BATCH_SIZE` | 100 |
| `PGEVENTBUS_LOCK_TTL_SECONDS` | 30 |
| `PGEVENTBUS_LISTEN_ADDR` | :8080 |
| `PGEVENTBUS_REQUEST_TIMEOUT` | 30s |

每项显式原生变量优先于别名，包括空值；用户名/密码再覆盖 URL 内凭证。原生 URL 必须是带主机的 `postgresql://` 或 `postgres://`。schema 去首尾空白，空值回退 bus，通过标识符引用保留大小写。批大小和 TTL 必须为正 int32；监听地址为 `host:port`（如 `127.0.0.1:8080` 或 `:8080`），端口 1..65535；请求超时为正 Go duration，最多 24h。启动连接检查最多 10 秒；每次请求的期限覆盖等待连接及 SQL，客户端断开会取消请求。


9 个 POST 路由、成功信封及 DTO 见[根 README](../README.md#http-网关)和[客户端指南](../docs/pg-eventbus-client-guide.md)。网关自有模型位于 [internal/store/model.go](internal/store/model.go)，时间为 RFC3339Nano 字符串，ID 为 int64；空事件是 `[]`，可空锁信息/广播目标为 null。poll 先获取租约，失败不查询，成功与 commit 均不释放锁；正常信封 success 不代表拿锁或提交成功。

有请求体的四个入口要求 application/json（可带参数，也接受 application/*+json），JSON 最大 1 MiB，未知属性忽略；缺失/空白必填字段、缺失/null payload、类型/整数越界和无时区 recover 时间返回 400。recover 同时接受带时区 RFC3339 字符串和 Java 客户端默认的整数/小数 epoch seconds（最多 32 字符，不支持科学计数法，年份范围 0000—9999，精度截取到纳秒）。limit 缺失/null/非正时用配置批大小；keepMonths 按原接口接受有符号 int32，请谨慎调用删除接口。维护入口不要求请求体。

数值 recover 保留 Jackson 的历史规则：整数秒为负时，小数部分按正纳秒组合，例如 `-2.500000000` 恢复到 epoch `-1.5` 秒；`-0.5` 则为 `-0.5` 秒。为避免负数编码歧义，建议显式发送带时区 ISO 字符串。

业务入口错误使用相同失败信封（success=false、data=null、error.errorCode/errorMessage/details/fieldErrors）：参数 400，超限 413，媒体类型 415，数据库错误 500，请求数据库期限超时 504；不泄露 SQL、URL、凭证或负载。未知路由/方法为标准 404/405。错误格式不复刻 Spring 默认错误页面，使用旧错误页字段的调用方需要适配。HTTP 读头期限 5 秒、整次读请求期限 15 秒、空闲连接 60 秒、写期限为配置请求期限加 5 秒。

没有内建认证/授权、TLS 或消费调度，部署方负责接入控制；SQL 的既有清理/分区边界详见[模型文档](../docs/PG-EventBus.md)。本次迁移只做离线编译和静态核对，未执行 HTTP、数据库或跨平台运行验证。
