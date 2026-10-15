# FD-005: Add TypeScript and JavaScript clients

**Status:** Complete
**Revision:** 5
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

两个独立包/声明、9操作、JS/TS示例、当前文档、离线检查及独立评审。排除npm安装/发布、驱动实现、CORS改造、既有客户端/Gateway/CLI/SQL变更、消费/重试/续租调度、测试、网络和部署。用户已明确回复“两种都提供”，范围已确认。

## Work items

| ID | 结果 | 规模/难度/预计 | 依赖 | 完成标准 |
|---|---|---|---|---|
| 1.1 | 独立包/DTO/精度边界 | 小/中/≤半天 | 无 | exports/types、bigint/JSON/ISO校验，两包自带helper和声明 |
| 1.1.1 | 输入资源/日期验证与类型入口 | 小/中/≤2小时 | 1.1 | ID字符串长度、日历有效性、脱敏URL、独立tsconfig及TS消费示例 |
| 1.2 | Gateway实现 | 小/中/≤半天 | 1.1 | 9操作、编码、信封、错误、期限/取消/清理、精确commit |
| 1.2.1 | fetch路径规范化防护 | 小/低/≤1小时 | 1.2 | 拒绝HTTP点路径消费者，文档明确限制；包清单携带示例与类型检查入口 |
| 1.3 | DB实现 | 小/中/≤半天 | 1.1 | query、9操作、绑定/引用、锁/维护/映射 |
| 1.4 | 接入文档 | 小/低/≤半天 | 1.2/1.3 | JS/TS安装示例、DB宿主、精度/错误/时间/取消边界与索引 |
| 1.5 | 离线检查与交接 | 小/中/≤半天 | 1.4 | node --check、本地tsc --noEmit、Dual报告、独立Reviewer |

### TODO

- [x] 1.1 独立包/DTO/精度边界。
- [x] 1.1.1 输入资源/日期验证与类型入口。
- [x] 1.2 Gateway实现。
- [x] 1.2.1 fetch路径规范化防护。
- [x] 1.3 DB实现。
- [x] 1.4 接入文档。
- [x] 1.5 离线检查与交接。

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

### Independent review

- R1 静态独立评审通过，无阻塞发现。Reviewer Session `fd005-reviewer-20261016-5bca912e`；来源 `FD-005-000004-implementation-ready`；范围 `78f426d..a151f2eeecafeb7a0941094e3a3a92eca859963c`。
- 评审报告：`docs/features/reviews/FD-005-review-r1.md` 及同名 JSON。Reviewer 未运行额外编译、测试、HTTP/DB、网络或示例；互通和并发风险仍未运行验证。

## Sources

### Worker evidence

- Session fd005-host-20261016-93d8ca8e；source FD-005-000003-design-ready；坐标main / feature/FD-005 / .wt/FD-005已核对。
- 1.1—1.5及1.1.1/1.2.1完成，用户已确认两种模式。两包JS语法、strict checkJs/声明/TS消费示例通过；Gateway实际增加dot段防护后重查通过，DB未重复检查。
- Dual报告docs/features/reports/FD-005-implementation-r1.md/JSON；无测试、HTTP/DB运行、npm安装/打包/发布、网络或部署。无持续blocker，未修改无关实现；独立Reviewer待交接。

- Issue: none
- 用户本轮要求增加TypeScript/JavaScript客户端；随后明确确认“两种都提供”。
- client/README、Gateway api/store、docs/sql；main基线cd31a46。
- Planner来源FD-005-000002-design-requested；Session fd005-host-20261016-93d8ca8e。
