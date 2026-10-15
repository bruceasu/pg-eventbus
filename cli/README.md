# 维护 CLI

独立构建，无需安装项目内其它模块：

```powershell
mvn -f cli/pom.xml clean package
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/pg_eventbus'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:SPRING_DATASOURCE_PASSWORD = 'change-me'
$env:BUS_SCHEMA = 'analytics'
$cliJar = 'cli/target/pg-eventbus-cli-0.0.1-SNAPSHOT.jar'
java -jar $cliJar db init app_role
java -jar $cliJar partition ensure-current
java -jar $cliJar partition ensure-future
java -jar $cliJar maintenance run
```

JAR 自带 `init.sql`，复制到其它目录后可直接运行。`app_role` 须提前创建。手动执行 SQL 时，在 `src/main/resources/init.sql` 开头设置 `CREATE SCHEMA` 与 `SET LOCAL search_path`。

CLI 直接通过 JDBC 执行 SQL，不依赖客户端、Gateway 或其它项目模块，不使用 ORM。默认拉取批大小 100、锁 TTL 30 秒，通过 `BUS_BATCH_SIZE`、`BUS_LOCK_TTL_SECONDS` 覆盖。

全部命令见[CLI 指南](../docs/pg-eventbus-schema-and-cli-addendum.md)。
