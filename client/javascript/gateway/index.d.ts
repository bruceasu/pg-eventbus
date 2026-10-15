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
export interface RequestOptions { signal?: AbortSignal }
export interface ClientOptions {
  timeoutMs?: number;
  headers?: HeadersInit;
  fetch?: typeof globalThis.fetch;
}
export class PgEventBusError extends Error {
  readonly code: string; readonly status: number | null; readonly details: string[];
  constructor(code: string, message: string, status?: number | null, details?: string[]);
}
export class PgEventBusClient {
  constructor(baseURL: string, options?: ClientOptions);
  publish(source: string, target: string, eventType: string, payload: JsonValue, options?: RequestOptions): Promise<PublishResultDto>;
  poll<T = JsonValue>(consumer: string, instance: string, limit?: number | null, options?: RequestOptions): Promise<PollResultDto<T>>;
  commit(consumer: string, instance: string, newLastId: IdInput, options?: RequestOptions): Promise<CommitResultDto>;
  recover(consumer: string, startTime: string | Date, options?: RequestOptions): Promise<RecoverResultDto>;
  ensureCurrentPartition(options?: RequestOptions): Promise<MaintenanceResultDto>;
  ensureFuturePartitions(options?: RequestOptions): Promise<MaintenanceResultDto>;
  dropOldPartitions(keepMonths: number, options?: RequestOptions): Promise<MaintenanceResultDto>;
  runMaintenance(options?: RequestOptions): Promise<MaintenanceResultDto>;
  cleanupConsumed(options?: RequestOptions): Promise<number>;
}
