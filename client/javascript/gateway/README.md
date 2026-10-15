# JavaScript / TypeScript HTTP 客户端

独立 ESM 包 `@pg-eventbus/client-gateway`，自带 `.d.ts`，Node.js >=22.12。安装、精度和运行环境边界见[总说明](../README.md)。从宿主项目本地安装本目录后，JS/TS 均可：

无需先发布到 npm。在使用方项目运行 `npm install D:/03_projects/pg-eventbus/client/javascript/gateway` 即可；也可复制完整包目录后相对导入，或用 `npm pack` 生成 `.tgz` 分发。当前未发布公共 npm，具体命令及 ESM 入口设置见[在其它项目中使用](../README.md#在其它项目中使用)。

```javascript
import { PgEventBusClient, PgEventBusError } from '@pg-eventbus/client-gateway';

const client = new PgEventBusClient('http://localhost:8080', {
  timeoutMs: 30000,
  // headers: { Authorization: 'Bearer <host-supplied-token>' },
  // fetch: customFetch,
});
const published = await client.publish('source-a', 'consumer-a', 'price-update', { price: '123.45' });
console.log(published.eventId.toString());
const batch = await client.poll('consumer-a', 'instance-a', 100);
if (batch.lockAcquired) {
  // 幂等处理事件后提交成功处理的连续前缀；这里不自动执行回调。
  console.log(batch.events);
}
```

TypeScript 可指定 payload 类型，类型参数只影响静态提示，不验证业务字段：

```typescript
const batch = await client.poll<{ price: string }>('consumer-a', 'instance-a', 100);
const first = batch.events[0];
if (first) console.log(first.payload.price, first.id); // string / bigint
```

| 方法 | 参数 | 返回 |
|---|---|---|
| publish | source、target、eventType、payload、options? | `{eventId: bigint}` |
| poll | consumer、instance、limit?、options? | PollResultDto |
| commit | consumer、instance、newLastId、options? | CommitResultDto |
| recover | consumer、startTime、options? | RecoverResultDto |
| ensureCurrentPartition / ensureFuturePartitions / runMaintenance | options? | MaintenanceResultDto |
| dropOldPartitions | keepMonths、options? | MaintenanceResultDto |
| cleanupConsumed | options? | number |

options 为 `{signal?: AbortSignal}`。limit缺失/null/非正数使用网关批大小；limit和keepMonths为有符号int32。source/target/eventType/consumer/instance非空白，payload不能null。Gateway配置schema及TTL，客户端不传这些值。baseURL可以有反向代理路径前缀，不能含查询、fragment或用户名密码；消费者名称按单个路径段编码。HTTP版拒绝仅为`.`或`..`的名称，避免fetch的URL路径规范化改变路由；DB版不受该限制。对外网关没有内建认证，请求头仅用于宿主代理接入。

```javascript
const controller = new AbortController();
try {
  await client.recover('consumer-a', '2026-10-01T00:00:00.123456789Z', { signal: controller.signal });
  // 其它代码可调用 controller.abort()。
} catch (error) {
  if (error instanceof PgEventBusError) console.error(error.code, error.status);
  else throw error; // 参数错误为 TypeError/RangeError
}
```

timeoutMs为正整数且不超过2147483647，覆盖请求及响应体读取；单次AbortSignal与期限并用，结束后清理计时器/监听器。自定义fetch必须遵守标准AbortSignal和Response接口。错误码包括TIMEOUT、ABORTED、NETWORK_ERROR、INVALID_RESPONSE、HTTP_ERROR及网关业务code；status在无HTTP响应时为null，details只保留结构化字符串明细。不回显原始响应或网络异常，认证头/响应明细属于宿主可信数据，不应记录敏感信息。参数校验可能同步抛错，其余错误随Promise拒绝；不自动重试任何操作。网关默认请求体上限1MiB。

完整类型见[index.d.ts](index.d.ts)，消费示例见[consume.ts](examples/consume.ts)。打包时只携带本包文件；本次未发布npm，未做真实网关互通验证。
