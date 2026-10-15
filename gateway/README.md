# HTTP Gateway

独立 HTTP 程序，直接通过 JDBC 调用 PostgreSQL 函数，无 ORM 或项目内模块依赖：

```powershell
mvn -f gateway/pom.xml clean package
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/pg_eventbus'
$env:SPRING_DATASOURCE_USERNAME = 'app_role'
$env:SPRING_DATASOURCE_PASSWORD = 'change-me'
$env:BUS_SCHEMA = 'analytics'
java -jar gateway/target/pg-eventbus-gateway-0.0.1-SNAPSHOT.jar
```

先使用 CLI 初始化数据库。JAR 可单独复制部署，默认端口 8080，可通过 `SERVER_PORT` 覆盖。默认批大小 100、锁 TTL 30 秒，通过 `BUS_BATCH_SIZE`、`BUS_LOCK_TTL_SECONDS` 覆盖。

HTTP 路由与 JSON 返回结构保持不变，详见[根 README](../README.md#http-网关)。Gateway 自有 DTO 包为 `me.asu.pg_eventbus.gateway.model`，客户端通过 HTTP 契约通信。
