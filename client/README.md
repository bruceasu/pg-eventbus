# 客户端

每种语言均提供两种独立接入方式：`db` 访问 PostgreSQL，`gateway` 通过 HTTP 访问 Gateway。

| 语言 | 直连 DB | Gateway |
|---|---|---|
| Java | [db](java/db) | [gateway](java/gateway) |
| Go | [db](go/db) | [gateway](go/gateway) |
| Python | [db](python/db) | [gateway](python/gateway) |
| JavaScript / TypeScript | [db](javascript/db) | [gateway](javascript/gateway) |

Java 使用各自目录下的 `pom.xml` 构建；Go 使用各自的 `go.mod`；Python 使用各自的 `pyproject.toml`。客户端均不依赖 CLI 或 Gateway 的源码与构建产物。

JS/TS 共用 JavaScript ESM 实现和 `.d.ts` 声明，各包有独立 package.json，Node.js >=22.12；无需预构建。支持本地路径安装，本次未发布 npm。DB版注入宿主query连接/池，HTTP版使用fetch；ID/位点返回bigint，精度及宿主责任见[JS/TS说明](javascript/README.md)。

Java 直连包保留原 `client`、`core`、`jdbc` 和 `contract` 包名，已合并为一个 JAR。Java Gateway 客户端 DTO 使用 `me.asu.pg_eventbus.client.http.model`，与直连包互不冲突。Python 两种分发包仍使用 `pg_eventbus` 导入名，应分别安装在独立虚拟环境。

完整使用说明见[客户端指南](../docs/pg-eventbus-client-guide.md)。
