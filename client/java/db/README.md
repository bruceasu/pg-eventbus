# Java 直连客户端

本目录是独立 Maven 项目，包含 JDBC 实现、客户端服务和 DTO。只需本目录即可构建，不依赖仓库其它 Java 模块：

```powershell
mvn -f client/java/db/pom.xml clean install
```

依赖坐标保持 `me.asu:pg-eventbus-client-java:0.0.1-SNAPSHOT`，入口保持 `me.asu.pg_eventbus.client.PgEventBusClient`。JDBC 直接调用 PostgreSQL 函数，不使用 ORM。原 `core`、`jdbc`、`contract` 包已收进本 JAR，不再单独发布。

装配与方法说明见[客户端指南](../../../docs/pg-eventbus-client-guide.md)。
