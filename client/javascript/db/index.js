import { decode, encode, id, instant, int32, text } from './codec.js';

export class PgEventBusClient {
  #database;
  #schema;
  #batch;
  #ttl;

  /** @param {import('./index.js').Queryable} database @param {import('./index.js').ClientOptions} [options] */
  constructor(database, options = {}) {
    if (!database || typeof database.query !== 'function') throw new TypeError('A query(text, values) executor is required');
    this.#database = database;
    this.#schema = text(options.schema ?? 'bus', 'schema').trim();
    if (this.#schema.includes('\0')) throw new TypeError('schema must not contain NUL');
    this.#batch = int32(options.batchSize ?? 100, 'batchSize');
    this.#ttl = int32(options.lockTtlSeconds ?? 30, 'lockTtlSeconds');
    if (this.#batch <= 0 || this.#ttl <= 0) throw new RangeError('batchSize and lockTtlSeconds must be positive');
  }

  /** @param {string} name */
  #function(name) { return '"' + this.#schema.replaceAll('"', '""') + '"."' + name + '"'; }

  /** @param {string} sql @param {any[]} [values] @returns {Promise<any>} */
  async #single(sql, values = []) {
    const response = await this.#database.query(sql, values);
    if (!response || !Array.isArray(response.rows) || response.rows.length !== 1) throw new TypeError('Database must return one result row');
    return response.rows[0];
  }

  /** @param {string} name @param {string} instance @param {boolean} locked @param {number} limit @param {bigint} [offset] @returns {import('./index.js').ConsumerDto} */
  #state(name, instance, locked, limit, offset = 0n) {
    return { consumerName: name, instanceId: instance, locked, lockOwner: locked ? instance : null,
      lockAcquiredAt: null, lockExpiresAt: null, lastCommittedEventId: offset, currentOffset: offset, batchSize: limit };
  }

  /** @param {string} source @param {string | null} target @param {string} eventType @param {import('./index.js').JsonValue} payload @returns {Promise<import('./index.js').PublishResultDto>} */
  async publish(source, target, eventType, payload) {
    text(source, 'sourceSystem'); text(eventType, 'eventType');
    if (target !== null) text(target, 'targetSystem');
    const row = await this.#single('select ' + this.#function('publish_event') + '($1, $2, $3, $4::jsonb)::text as event_id', [source, target, eventType, encode(payload)]);
    return { eventId: id(row.event_id) };
  }

  /** @template [T=import('./index.js').JsonValue] @param {string} consumer @param {string} instance @param {number | null} [limit] @returns {Promise<import('./index.js').PollResultDto<T>>} */
  async poll(consumer, instance, limit) {
    text(consumer, 'consumerName'); text(instance, 'instanceId');
    if (limit != null) int32(limit, 'limit');
    const batch = limit == null || limit <= 0 ? this.#batch : limit;
    const lease = await this.#single('select ' + this.#function('acquire_consumer_lock') + '($1, $2, $3) as locked', [consumer, instance, this.#ttl]);
    if (typeof lease.locked !== 'boolean') throw new TypeError('Database returned an invalid lease result');
    /** @type {import('./index.js').PollResultDto<T>} */
    const result = { consumer: this.#state(consumer, instance, lease.locked, batch), events: [], batchSize: batch, lockAcquired: lease.locked, polledAt: '' };
    if (lease.locked) {
      const response = await this.#database.query(
        'select id::text, source_system, target_system, event_type, payload::text, ' +
        'to_char(created_at at time zone \'UTC\', \'YYYY-MM-DD"T"HH24:MI:SS.US"Z"\') as created_at ' +
        'from ' + this.#function('poll_events') + '($1, $2)', [consumer, batch]);
      result.events = response.rows.map(row => ({ id: id(row.id), sourceSystem: row.source_system,
        targetSystem: row.target_system, eventType: row.event_type, payload: decode(row.payload), createdAt: row.created_at }));
    }
    result.polledAt = new Date().toISOString();
    return result;
  }

  /** @param {string} consumer @param {string} instance @param {import('./index.js').IdInput} newLastId @returns {Promise<import('./index.js').CommitResultDto>} */
  async commit(consumer, instance, newLastId) {
    text(consumer, 'consumerName'); text(instance, 'instanceId');
    const offset = id(newLastId);
    const row = await this.#single('select ' + this.#function('commit_consumer_offset') + '($1, $2, $3::bigint) as committed', [consumer, instance, offset.toString()]);
    if (typeof row.committed !== 'boolean') throw new TypeError('Database returned an invalid commit result');
    return { committed: row.committed, newLastCommittedEventId: offset };
  }

  /** @param {string} consumer @param {string | Date} startTime @returns {Promise<import('./index.js').RecoverResultDto>} */
  async recover(consumer, startTime) {
    text(consumer, 'consumerName');
    const row = await this.#single('select ' + this.#function('init_consumer_at_time') + '($1, $2::timestamptz)::text as initialized_offset', [consumer, instant(startTime)]);
    const offset = id(row.initialized_offset);
    return { initializedOffset: offset, consumer: this.#state(consumer, '', false, this.#batch, offset) };
  }

  /** @param {string} name @param {string} label @param {number[]} [values] @returns {Promise<import('./index.js').MaintenanceResultDto>} */
  async #maintain(name, label, values = []) {
    await this.#database.query('select ' + this.#function(name) + '(' + values.map((_, index) => '$' + (index + 1)).join(', ') + ')', values);
    return { executed: true, actions: [label], completedAt: new Date().toISOString() };
  }

  ensureCurrentPartition() { return this.#maintain('ensure_current_partition', 'ensure_current_partition'); }
  ensureFuturePartitions() { return this.#maintain('ensure_future_partitions', 'ensure_future_partitions:6', [6]); }
  /** @param {number} keepMonths */
  dropOldPartitions(keepMonths) {
    int32(keepMonths, 'keepMonths');
    return this.#maintain('drop_partition_older_than', 'drop_partition_older_than:' + keepMonths, [keepMonths]);
  }
  runMaintenance() { return this.#maintain('maintenance', 'maintenance'); }
  /** @returns {Promise<number>} */
  async cleanupConsumed() {
    const row = await this.#single('select ' + this.#function('cleanup_consumed') + '($1) as deleted', [10000]);
    return int32(row.deleted, 'cleanup result');
  }
}
