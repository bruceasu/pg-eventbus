# JavaScript / TypeScript PostgreSQL 客户端

独立 ESM 包 `@pg-eventbus/client-db`，自带 `.d.ts`，Node.js >=22.12。先用CLI安装数据库函数与授权角色。安装、精度和时间规则见[总说明](../README.md)。宿主另行安装并配置node-postgres（`pg`），本包不下载或持有驱动：

```javascript
import pg from 'pg';
import { PgEventBusClient } from '@pg-eventbus/client-db';

const pool = new pg.Pool({
  connectionString: process.env.PGEVENTBUS_DATABASE_URL,
  connectionTimeoutMillis: 10000,
  query_timeout: 30000,
});
const client = new PgEventBusClient(pool, { schema: 'bus', batchSize: 100, lockTtlSeconds: 30 });
try {
  const sent = await client.publish('source-a', 'consumer-a', 'price-update', { price: '123.45' });
  console.log(sent.eventId.toString());
  const batch = await client.poll('consumer-a', 'instance-a');
  if (batch.lockAcquired) console.log(batch.events); // 业务处理后再commit连续前缀
} finally {
  await pool.end(); // 连接生命周期属于宿主
}
```

可传 `pg.Pool`、已连接 `pg.Client` 或 `{query(text, values): Promise<{rows: Record<string, any>[]}>}`。schema默认bus，批大小100、TTL30秒且均须正int32。宿主传入的query方法会保留receiver；客户端不调用connect/end、不修改类型解析器、不创建事务。使用已连接Client时应在无关事务之外调用；在外层事务中使用将改变可见性/锁提交时点，需由宿主自行管理。

方法和结果与[HTTP客户端](../gateway/README.md)一致，但没有HTTP options/signal参数。DB publish另允许target=null广播（HTTP入口不允许），payload=null表示JSON null。limit缺失/null/非正数使用配置批大小；keepMonths为有符号int32，与既有Gateway相同，负数可能使保留策略更激进，应谨慎调用。

SQL用引用转义schema和参数绑定。ID/位点在SQL中转text后转换bigint；payload在SQL中转text再精确JSON解析；事件时间在数据库按UTC格式化为保留微秒的ISO字符串，因此不依赖pg默认int8/JSON/时间解析器。poll先调用acquire_consumer_lock，失败不查事件；成功才poll_events，租约不自动释放。commit=false照常返回；recover直接重设位点。

```typescript
import { PgEventBusClient, type Queryable } from '@pg-eventbus/client-db';
export function createBus(database: Queryable): PgEventBusClient {
  return new PgEventBusClient(database, { schema: 'analytics' });
}
```

future固定6个月，cleanup批量10000，maintenance执行数据库原maintenance。只有query成功后才返回executed=true；数据库错误原样传播，可能包含SQL/业务数据，宿主负责日志脱敏。SQL权限、连接池容量、query_timeout、statement_timeout、取消和连接关闭均属于宿主；本包没有强制DB取消API，且客户端超时不证明服务器查询已取消。没有消费回调、重试/续租/释放调度。原SQL清理ctid/分区边界风险见[模型说明](../../../docs/PG-EventBus.md)。

类型见[index.d.ts](index.d.ts)，消费示例见[consume.ts](examples/consume.ts)。本次不安装或发布驱动/包，未执行数据库互通与并发验证。
