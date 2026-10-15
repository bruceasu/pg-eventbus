export type IdInput = bigint | string | number;
export type JsonValue = null | boolean | string | number | bigint | JsonValue[] | { [key: string]: JsonValue };
export interface ConsumerDto {
  consumerName: string; instanceId: string; locked: boolean; lockOwner: string | null;
  lockAcquiredAt: string | null; lockExpiresAt: string | null;
  lastCommittedEventId: bigint; currentOffset: bigint; batchSize: number;
}
export interface EventDto<T = JsonValue> {
  id: bigint; sourceSystem: string; targetSystem: string | null; eventType: string;
  payload: T; createdAt: string;
}
export interface PublishResultDto { eventId: bigint }
export interface PollResultDto<T = JsonValue> { consumer: ConsumerDto; events: EventDto<T>[]; batchSize: number; lockAcquired: boolean; polledAt: string }
export interface CommitResultDto { committed: boolean; newLastCommittedEventId: bigint }
export interface RecoverResultDto { initializedOffset: bigint; consumer: ConsumerDto }
export interface MaintenanceResultDto { executed: boolean; actions: string[]; completedAt: string }
export interface QueryResult { rows: Record<string, any>[] }
/** Accepts pg Pool/Client or an equivalent executor. No transaction is started. */
export interface Queryable { query(text: string, values?: any[]): Promise<QueryResult> }
export interface ClientOptions { schema?: string; batchSize?: number; lockTtlSeconds?: number }
export class PgEventBusClient {
  constructor(database: Queryable, options?: ClientOptions);
  publish(source: string, target: string | null, eventType: string, payload: JsonValue): Promise<PublishResultDto>;
  poll<T = JsonValue>(consumer: string, instance: string, limit?: number | null): Promise<PollResultDto<T>>;
  commit(consumer: string, instance: string, newLastId: IdInput): Promise<CommitResultDto>;
  recover(consumer: string, startTime: string | Date): Promise<RecoverResultDto>;
  ensureCurrentPartition(): Promise<MaintenanceResultDto>;
  ensureFuturePartitions(): Promise<MaintenanceResultDto>;
  dropOldPartitions(keepMonths: number): Promise<MaintenanceResultDto>;
  runMaintenance(): Promise<MaintenanceResultDto>;
  cleanupConsumed(): Promise<number>;
}
