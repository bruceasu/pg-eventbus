# FD-005: Add TypeScript and JavaScript clients

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户要求增加TypeScript/JavaScript客户端。既有语言均有db/gateway两种模式，新增两个独立包，避免调用方拼SQL/HTTP和JS number丢失64位位点。继续既有自动实施方式。

## Options and decision

单独TS源码要求构建产物；重复TS/JS实现易漂移。选JavaScript ESM + .d.ts，放在client/javascript/{db,gateway}，独立npm包、无项目模块依赖。DB注入node-postgres兼容query(text,values)，不下载驱动或调用psql；HTTP可注入fetch。CommonJS使用动态import。

## Solution

两包均提供PgEventBusClient及9种操作：publish、poll、commit、recover、ensureCurrentPartition、ensureFuturePartitions、dropOldPartitions、runMaintenance、cleanupConsumed。Promise结果为camelCase，时间ISO字符串，主要环境Node>=22.12。HTTP浏览器需要fetch/AbortController/精确JSON支持及宿主CORS。

ID/位点输出bigint，输入bigint/十进制字符串/安全整数number且检查signed int64；HTTP commit发送JSON数值token。JSON.parse源token上下文还原不安全整数，JSON.rawJSON编码bigint，能力缺失明确失败，禁止舍入降级。payload允许bigint，不安全number输入拒绝；小数按JS语义，超安全范围指数整数拒绝。recover接受Date或带时区ISO字符串，保留字符串纳秒，不发送数值时间。

HTTP默认timeoutMs=30000，外部signal与期限覆盖fetch/body读取，finally清理timer/listener；consumer用encodeURIComponent，校验必填字段、int32参数、URL。成功信封解包；非2xx/业务失败/坏JSON抛PgEventBusError(code/status/details)，不回显原响应，无自动重试。

DB注入宿主query，schema=bus、batchSize=100、lockTtlSeconds=30。标识符引用转义，参数绑定；ID结果::text、payload::text，时间UTC to_char保留微秒，不改驱动全局parser。poll先租约失败短路、成功不释放；consumer占位同Gateway。future=6、cleanup=10000，维护用普通SELECT等待执行。DB连接生命周期/query_timeout/cancellation由宿主负责。

## Scope

两个独立包/声明、9操作、JS/TS示例、当前文档、离线检查及独立评审。排除npm安装/发布、驱动实现、CORS改造、既有客户端/Gateway/CLI/SQL变更、消费/重试/续租调度、测试、网络和部署。可选范围提问未回复时按既有两种模式推进。

## Work items

| ID | 结果 | 规模/难度/预计 | 依赖 | 完成标准 |
|---|---|---|---|---|
| 1.1 | 独立包/DTO/精度边界 | 小/中/≤半天 | 无 | exports/types、bigint/JSON/ISO校验，两包自带helper和声明 |
| 1.2 | Gateway实现 | 小/中/≤半天 | 1.1 | 9操作、编码、信封、错误、期限/取消/清理、精确commit |
| 1.3 | DB实现 | 小/中/≤半天 | 1.1 | query、9操作、绑定/引用、锁/维护/映射 |
| 1.4 | 接入文档 | 小/低/≤半天 | 1.2/1.3 | JS/TS安装示例、DB宿主、精度/错误/时间/取消边界与索引 |
| 1.5 | 离线检查与交接 | 小/中/≤半天 | 1.4 | node --check、本地tsc --noEmit、Dual报告、独立Reviewer |

### TODO

- [ ] 1.1 独立包/DTO/精度边界。
- [ ] 1.2 Gateway实现。
- [ ] 1.3 DB实现。
- [ ] 1.4 接入文档。
- [ ] 1.5 离线检查与交接。

## Acceptance

- AC-01：JS直接import，TS声明可解析，包独立，无新增下载。
- AC-02：9操作与Gateway/SQL对应，DB先拿锁失败短路，不自动提交/释放/重试；维护等待query、参数不变。
- AC-03：signed int64不经不安全number，输出bigint/commit数值token，payload大整数精确；ISO/nullable/空数组保留。
- AC-04：HTTP取消/期限覆盖读取且清理，错误不暴露原响应；DB引用/绑定，不关闭宿主连接。
- AC-05：声明与JS签名/DTO一致；JS/TS、DB超时/驱动责任、现代JSON/CORS/bigint边界清晰。
- AC-06：离线compile-only/静态检查、独立评审真实记录；不运行测试/网络/DB/HTTP，无关实现不变。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

本机Node24.14.0；node --check每个JS，本地C:/green/aiw/http-openai-proxy/node_modules/typescript/lib/tsc.js --noEmit仅做声明/checkJs类型检查。不安装依赖、不跑场景/测试/formatter/linter/npm pack/最终构建。代码修复后可重做受影响检查。静态核对Gateway模型/路由与SQL，精度、拒绝路径、租约/清理；git diff --check。Worker与Reviewer独立。

## Sources

- Issue: none
- 用户本轮要求增加TypeScript/JavaScript客户端；可选范围提问默认匹配既有两种模式。
- client/README、Gateway api/store、docs/sql；main基线cd31a46。
- Planner来源FD-005-000002-design-requested；Session fd005-host-20261016-93d8ca8e。
