# JavaScript / TypeScript 客户端

同一份 JavaScript ESM 实现附带 TypeScript 声明，无需先构建。两包独立，不依赖 CLI、Gateway 源码或兄弟包。主要运行环境为 Node.js >=22.12，采用内建 fetch、JSON.parse 源 token 上下文和 JSON.rawJSON。

| 接入 | 包 | 说明 |
|---|---|---|
| HTTP | [@pg-eventbus/client-gateway](gateway/) | fetch、超时、AbortSignal、自定义请求头；9 个 POST 操作 |
| PostgreSQL | [@pg-eventbus/client-db](db/) | 注入 node-postgres 兼容连接或池；参数绑定、先获取租约再拉取 |

## 在其它项目中使用

不需要先发布到 npm。当前两个包尚未发布到公共 npm，不能直接从公共仓库按包名安装。可以本地安装、复制完整包目录，或分发 `.tgz`；需要统一版本管理时再发布到公共 npm 或公司私有仓库。客户端是使用方程序中的库，无需单独启动或部署；HTTP 版需要可访问的 Gateway，DB 版需要 PostgreSQL 及宿主提供的驱动。

### 本地路径安装

在使用方项目（含 package.json）的目录执行，路径替换为实际仓库位置：

```powershell
npm install D:/03_projects/pg-eventbus/client/javascript/gateway
# 或
npm install D:/03_projects/pg-eventbus/client/javascript/db
```

安装后，JS 和 TS 使用相同包名导入：

```javascript
import { PgEventBusClient } from '@pg-eventbus/client-gateway';

const client = new PgEventBusClient('http://localhost:8080');
const result = await client.publish('source-a', 'consumer-a', 'price-update', { price: '123.45' });
console.log(result.eventId.toString());
```

DB 版改为从 `@pg-eventbus/client-db` 导入，并传入连接或连接池，完整示例见 [DB README](db/README.md)。默认本地目录安装可能使用符号链接，因此移动或删除客户端源码目录可能使依赖失效。跨机器或 CI 使用时，不能只提交指向个人电脑绝对路径的依赖；可选择下面的包文件方式。

### 复制完整目录

将 `client/javascript/gateway` 整个目录复制到使用方项目，例如 `vendor/pg-eventbus-gateway`，再相对导入：

```javascript
import { PgEventBusClient } from './vendor/pg-eventbus-gateway/index.js';
```

示例假设入口文件位于使用方项目根目录。保留包内的 `package.json`、全部 `.js` 和 `.d.ts` 文件：package.json 声明 ESM，codec.js 是运行依赖，类型声明为 TS 和编辑器提供提示。DB 版同样复制完整 `db` 目录，不能只复制 index.js。直接复制不会自动获取更新，后续升级需要同步替换目录。

### 打包为 .tgz 分发

在要分发的包目录执行；以下为 Gateway 版，DB 版将目录改为 `db`：

```powershell
cd D:/03_projects/pg-eventbus/client/javascript/gateway
npm pack
# 当前版本输出 pg-eventbus-client-gateway-0.1.0.tgz
```

`npm pack` 将现有 JS、声明及 package.json 中 files 列出的文件打包，不需要先编译，也不会发布到仓库。把生成的文件复制到使用方项目（如 vendor 目录），在使用方项目根目录安装：

```powershell
npm install ./vendor/pg-eventbus-client-gateway-0.1.0.tgz
# DB 包对应 pg-eventbus-client-db-0.1.0.tgz
```

之后仍用包名 import，无需改业务代码。用于团队或 CI 时，应让构建环境能取得相同的 `.tgz` 文件，并保存相应依赖记录；文件名中的版本以实际打包输出为准。

### 发布后按版本安装

未来发布到公共 npm 或配置好的私有 registry 后，使用方可按版本安装：

```powershell
# 仅在对应包及版本已经发布、registry/访问权限配置完成后可用
npm install @pg-eventbus/client-gateway@0.1.0
# 或
npm install @pg-eventbus/client-db@0.1.0
```

发布属于包维护者的独立操作，本仓库此次没有执行发布；上面的命令不表示公共 npm 已存在这些包。

## JavaScript 与 TypeScript 接入

JavaScript 与 TypeScript 使用相同 import，TS 消费项目建议 ES2023 / NodeNext（或支持 package exports 的 bundler）。CommonJS 用 `await import('@pg-eventbus/client-gateway')`；没有独立 require/CJS 产物。浏览器仅适用 Gateway 包，需要现代 JSON 能力及 fetch/AbortController，服务端/反向代理须配置 CORS；不要将数据库凭证打包到浏览器。

包内运行代码是 `.js`，`.d.ts` 只用于类型检查，不需要运行。JS 示例可保存为 `app.mjs` 后执行 `node app.mjs`；使用 `.js` 作为宿主入口时，应在宿主 package.json 声明 `"type": "module"`。TS 使用方按自身项目流程编译/打包应用即可，无需先编译本客户端。

两包输出的事件 ID/位点都是 `bigint`，包括小 ID。提交接受 bigint、最多19位十进制字符串（可负号）或安全整数 number，并检查 signed int64。`Number(id)` 可能丢失精度，避免转换。日志用 `id.toString()`；普通 `JSON.stringify(result)` 不能处理 bigint，可使用 `JSON.stringify(result, (_, value) => typeof value === 'bigint' ? value.toString() : value)` 导出为字符串字段。这只是应用日志格式，不是网关请求格式；客户端会将 commit 的 bigint 编为精确的 JSON 数字。

payload 可以含 bigint。大整数从 JSON 数字源 token 精确恢复；源 token 上下文或 JSON.rawJSON 不可用时明确失败，不静默舍入。整数超安全范围且使用指数/小数形式（如 `9.007199254740993e15`）会拒绝；小数通常仍用 JS number，不提供任意精度 decimal。输入不安全 number、Infinity、NaN 被拒绝。请将金融十进制值按业务约定编码为字符串。

recover 必须显式传 Date 或带时区的 `YYYY-MM-DDTHH:mm:ss[.1至9位小数]Z/±HH:mm` 字符串；字符串保留纳秒，Date只有毫秒。结果时间保留 ISO 字符串，不隐式转换 Date。不使用数值 recover 时间，避免旧 Jackson 负数编码歧义。

两包都不负责自动轮询、业务回调、重试、续租或释放。poll拿锁失败时返回空数组及lockAcquired=false；commit需检查committed，成功不会释放租约。获得锁但后续请求失败时也不自动释放，等待TTL，处理必须幂等。consumer锁时间null/位点0仍是服务占位，不代表实时registry状态。

离线检查方式：各包执行 `npm run compile`（只做 node --check）；使用已安装 TypeScript 执行 `tsc -p tsconfig.json --noEmit` 可检查 JS、声明及 [Gateway示例](gateway/examples/consume.ts)/[DB示例](db/examples/consume.ts)。不需要 npm install 才能运行 JavaScript。当前仅有静态/离线类型检查证据，没有运行 HTTP、DB 或跨平台场景验证。
