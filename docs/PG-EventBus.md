# PostgreSQL 事件总线：模型与实现

本页依据当前 Java 服务、`docs/sql/` 和 CLI 合并初始化脚本整理。原始 `PG-EventBus.mht` 为历史资料，不代表当前运行行为。

CLI、Gateway 与各语言客户端是独立构建单元。CLI 使用 Go/pgx，Gateway 保持 Java/JDBC，各自持有 SQL 调用及结果模型；Java 直连库内部保留 core、jdbc 和 contract 包，但不再拆成独立模块。数据访问均直接执行 SQL，不使用 ORM。

## 数据模型

| 表 | 主要字段与职责 |
|---|---|
| `bus.event_log` | `id`、来源、目标、类型、JSONB 负载、`created_at`；按时间月分区，主键为 `(id, created_at)` |
| `bus.consumer_registry` | 消费者名称主键、`last_event_id`、注册/更新时间、`lock_owner` 和 `lock_until` |
| `bus.event_retry` | 原事件 ID、消费者、负载、重试计数、下次重试时间 |
| `bus.event_deadletter` | 原事件 ID、消费者、负载、错误信息、失败时间 |

schema 可配置，表名不变。事件日志分区名为 `event_log_YYYYMM`，分区建立 `(target_system, id)` 索引。时间边界通过数据库会话时区计算。

## 发布与路由

`publish_event(source, target, type, payload)` 先确保当前月份分区存在，再写入日志并返回 ID。

`poll_events(consumerName, limit)` 自动注册消费者，从其位点之后按 ID 升序读取，过滤条件为：

```sql
e.id > last_event_id
AND (e.target_system = consumerName OR e.target_system IS NULL)
```

因此，目标名称就是消费者路由名；NULL 目标为广播，空字符串不是广播。SQL/JDBC 可传 NULL 目标，网关请求 DTO 的 `targetSystem` 标有 `@NotBlank`，HTTP 公共输入契约不提供空目标广播。

消费者彼此拥有独立位点；同名消费者的实例共享位点。读取本身不更新位点。

## 位点与锁

`acquire_consumer_lock` 注册消费者，仅在锁为空或已过期时写入实例和 TTL。同一持有者也不能在租约仍有效时重新获取。

`renew_consumer_lock` 需要同一持有者且租约有效；`release_consumer_lock` 需要同一持有者。

`commit_consumer_offset` 需要同一持有者且租约未过期，使用 `GREATEST(last_event_id, newLastId)` 保持位点不倒退。提交不会释放锁，也不检查该 ID 是否实际拉取或处理过，调用方只能提交已成功处理的连续前缀。

Java 门面及网关的 `poll` 先获取锁，失败则返回空事件列表和 `lockAcquired=false`；成功后拉取，不释放锁。平面客户端未公开续租/释放接口，Java 可使用 recovery service 或 repository。

Go/Python 直连 `poll` 只调用查询函数并读取 registry，不调用锁获取函数，且将顶层 `lockAcquired` 固定为 true。不能依靠该字段确认真实持锁。

`init_consumer_at_time` 注册消费者并将位点设为全日志中 `created_at <= startTime` 的最大 ID（无数据则为 0）。它不按目标过滤，也不检查锁，允许位点倒退。恢复只影响后续拉取，不能恢复已经删除的数据。

## Java 回调与重试

平面客户端的 publish/poll/commit/recover 与处理回调相互独立。

`PgEventBusConsumerService.pollAndHandle`：

1. 获取锁并拉取事件。
2. 顺序调用 handler，记录成功处理数和末条成功 ID。
3. 第一次异常时为失败事件调用 `schedule_retry(..., 0)`，停止处理后续事件。
4. 提交成功前缀（如有），并在 finally 中释放锁。

失败事件的位点没有被跳过，因此后续正常拉取与重试队列都可能再次处理它。提交的布尔结果未被写入 `PgEventBusRunResult`，`committedOffset` 实际来自末条成功处理事件，不能单凭该字段确认数据库提交成功。

`PgEventBusRetryService.processDueRetries` 获取消费者锁，读取到期重试记录，处理成功后删除记录。失败时：

- 若 `retryCount + 1 >= maxRetries`，写入死信并删除重试记录。
- 否则再次排队并删除旧记录。

SQL 的 `schedule_retry` 按传入计数计算延迟：`min(2^retryCount 秒, 1 小时)`，存储计数为传入值加 1。首条重试记录计数为 1。重试 handler 收到合成事件：source 为 `bus.retry`，target 为消费者，type 为 `retry`，时间为当前时间；不保留原事件全部元数据。

这两个服务要求名为 `busTransactionManager` 的 Spring 事务管理器，仓库没有提供该 Bean。回调若操作外部服务或另一数据源，不会自动参与该事务。业务 handler 需支持幂等，不能由本实现推导 exactly-once 或全局提交顺序保证。

## 分区与清理

| 函数 | 实际职责 |
|---|---|
| `create_month_partition(year, month)` | 建月分区及目标/ID 索引 |
| `ensure_current_partition()` | 检查当前分区；使用 advisory lock 协调创建 |
| `ensure_future_partitions(monthsAhead=6)` | 建后续月份分区，不包含当前月份 |
| `drop_partition_older_than(keepMonths)` | 以月份阈值筛选分区并 `DROP TABLE ... CASCADE` |
| `cleanup_consumed(batchSize=10000)` | 按最小消费位点选择旧事件清理批次 |
| `cleanup_older_than(interval, batchSize=10000)` | 按时间选择旧事件清理批次，仅 SQL 层提供 |
| `maintenance()` | 预建后续 6 个月并调用保留参数为 3 的分区删除函数 |

分区保留策略不依据消费完成情况，也不删除 retry/deadletter。Java 默认 `dropOldPartitions()` 使用 3；带参数方法及 HTTP/CLI 可显式指定。

## SQL 安装与权限

`docs/sql/bootstrap.sql` 仅创建 schema、表和索引，不安装完整函数集。CLI 内嵌模板为 `cli/sql/init.sql`，按依赖顺序合并 SQL，并包含事务和授权。Go 在限定标记处引用 schema、将角色作为 format(%I) 的字符串参数渲染；模板不可直接手工执行。

修改拆分 SQL 后，还需同步合并脚本；构建配置没有自动生成步骤。合并脚本不包含预建当前/未来分区的执行语句。

函数使用 `SECURITY DEFINER`，通过 `SET search_path FROM CURRENT` 保存安装时选择的 schema 和末尾的 `pg_temp`；`pg_catalog` 隐式优先查找。`grant_access.sql` 撤销授权角色对 schema 中表和序列的权限，再授予 schema USAGE 和全部函数 EXECUTE。该脚本没有撤销 PUBLIC 的函数执行权限，也没有区分发布、消费和维护权限。

Go/Python 直连版查询 `consumer_registry` 表；仅按上述函数授权无法满足该查询，需额外的表读权限或调整客户端实现。

## 当前实现限制

- Go/Python 直连维护 SQL 将带分号的 SELECT 嵌入 `WITH effect AS (...)`，并且外层不引用该 CTE；不能将返回的 `executed` 视为维护已生效的可靠证据。
- 两个行清理函数用 `ctid` 跨分区匹配删除，没有同时匹配 `tableoid`。不同分区可能有相同 ctid，实际删除范围和数量可能超过选出的批次，不能据此保证严格按位点/时间安全清理。
- 网关没有自定义的全局错误信封转换、认证或消费者权限控制；正常返回的 `ApiResponse.success=true` 不等于拿锁或提交成功。
- Java 拉取 DTO 中的锁时间为 null、位点为 0，是服务组装的占位字段，不是 registry 的完整实时状态。
- 仓库没有自动轮询、续租、重试或维护调度器。

这些限制是当前代码事实，本文没有修改实现。接口与接入示例见[客户端指南](pg-eventbus-client-guide.md)，操作入口见[CLI 文档](pg-eventbus-schema-and-cli-addendum.md)。
