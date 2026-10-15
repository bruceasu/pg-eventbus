# JavaScript / TypeScript 客户端

同一份 JavaScript ESM 实现附带 TypeScript 声明，无需先构建。两包独立，不依赖 CLI、Gateway 源码或兄弟包。主要运行环境为 Node.js >=22.12，采用内建 fetch、JSON.parse 源 token 上下文和 JSON.rawJSON。

| 接入 | 包 | 说明 |
|---|---|---|
| HTTP | [@pg-eventbus/client-gateway](gateway/) | fetch、超时、AbortSignal、自定义请求头；9 个 POST 操作 |
| PostgreSQL | [@pg-eventbus/client-db](db/) | 注入 node-postgres 兼容连接或池；参数绑定、先获取租约再拉取 |

包名用于本地安装示例，本次没有发布至 npm。由消费项目安装所需路径：

```powershell
npm install D:/03_projects/pg-eventbus/client/javascript/gateway
# 或
npm install D:/03_projects/pg-eventbus/client/javascript/db
```

JavaScript 与 TypeScript 使用相同 import，TS 消费项目建议 ES2023 / NodeNext（或支持 package exports 的 bundler）。CommonJS 用 `await import('@pg-eventbus/client-gateway')`；没有独立 require/CJS 产物。浏览器仅适用 Gateway 包，需要现代 JSON 能力及 fetch/AbortController，服务端/反向代理须配置 CORS；不要将数据库凭证打包到浏览器。

两包输出的事件 ID/位点都是 `bigint`，包括小 ID。提交接受 bigint、最多19位十进制字符串（可负号）或安全整数 number，并检查 signed int64。`Number(id)` 可能丢失精度，避免转换。日志用 `id.toString()`；普通 `JSON.stringify(result)` 不能处理 bigint，可使用 `JSON.stringify(result, (_, value) => typeof value === 'bigint' ? value.toString() : value)` 导出为字符串字段。这只是应用日志格式，不是网关请求格式；客户端会将 commit 的 bigint 编为精确的 JSON 数字。

payload 可以含 bigint。大整数从 JSON 数字源 token 精确恢复；源 token 上下文或 JSON.rawJSON 不可用时明确失败，不静默舍入。整数超安全范围且使用指数/小数形式（如 `9.007199254740993e15`）会拒绝；小数通常仍用 JS number，不提供任意精度 decimal。输入不安全 number、Infinity、NaN 被拒绝。请将金融十进制值按业务约定编码为字符串。

recover 必须显式传 Date 或带时区的 `YYYY-MM-DDTHH:mm:ss[.1至9位小数]Z/±HH:mm` 字符串；字符串保留纳秒，Date只有毫秒。结果时间保留 ISO 字符串，不隐式转换 Date。不使用数值 recover 时间，避免旧 Jackson 负数编码歧义。

两包都不负责自动轮询、业务回调、重试、续租或释放。poll拿锁失败时返回空数组及lockAcquired=false；commit需检查committed，成功不会释放租约。获得锁但后续请求失败时也不自动释放，等待TTL，处理必须幂等。consumer锁时间null/位点0仍是服务占位，不代表实时registry状态。

离线检查方式：各包执行 `npm run compile`（只做 node --check）；使用已安装 TypeScript 执行 `tsc -p tsconfig.json --noEmit` 可检查 JS、声明及 [Gateway示例](gateway/examples/consume.ts)/[DB示例](db/examples/consume.ts)。不需要 npm install 才能运行 JavaScript。当前仅有静态/离线类型检查证据，没有运行 HTTP、DB 或跨平台场景验证。
