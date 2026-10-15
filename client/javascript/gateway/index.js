import { decode, encode, id, instant, int32, result, text } from './codec.js';

export class PgEventBusError extends Error {
  /** @param {string} code @param {string} message @param {number | null} [status] @param {string[]} [details] */
  constructor(code, message, status = null, details = []) {
    super(message);
    this.name = 'PgEventBusError';
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

export class PgEventBusClient {
  #url;
  #fetch;
  #headers;
  #timeout;

  /** @param {string} baseURL @param {import('./index.js').ClientOptions} [options] */
  constructor(baseURL, options = {}) {
    let url;
    try { url = new URL(baseURL); } catch { throw new TypeError('baseURL must be a valid HTTP(S) URL'); }
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new TypeError('baseURL must be an HTTP(S) URL without credentials, query or fragment');
    this.#url = url.href.replace(/\/+$/, '');
    this.#fetch = options.fetch ?? globalThis.fetch;
    if (typeof this.#fetch !== 'function') throw new TypeError('A fetch implementation is required');
    this.#timeout = options.timeoutMs ?? 30000;
    if (!Number.isInteger(this.#timeout) || this.#timeout <= 0 || this.#timeout > 2147483647) throw new RangeError('timeoutMs must be in 1..2147483647');
    this.#headers = new Headers(options.headers);
    this.#headers.set('Content-Type', 'application/json');
    this.#headers.set('Accept', 'application/json');
  }

  /** @param {string} name */
  #consumer(name) {
    text(name, 'consumerName');
    // WHATWG URL normalizes literal and percent-encoded dot segments before fetch.
    if (name === '.' || name === '..') throw new TypeError('HTTP consumerName must not be a dot segment');
    return '/consumers/' + encodeURIComponent(name);
  }

  /** @param {string} source @param {string} target @param {string} eventType @param {import('./index.js').JsonValue} payload @param {import('./index.js').RequestOptions} [options] */
  publish(source, target, eventType, payload, options) {
    text(source, 'sourceSystem'); text(target, 'targetSystem'); text(eventType, 'eventType');
    if (payload == null) throw new TypeError('payload must not be null');
    return this.#call('/events', 'publish', { sourceSystem: source, targetSystem: target, eventType, payload }, options);
  }

  /** @template [T=import('./index.js').JsonValue] @param {string} consumer @param {string} instance @param {number | null} [limit] @param {import('./index.js').RequestOptions} [options] @returns {Promise<import('./index.js').PollResultDto<T>>} */
  poll(consumer, instance, limit, options) {
    text(instance, 'instanceId');
    if (limit != null) int32(limit, 'limit');
    return this.#call(this.#consumer(consumer) + '/poll', 'poll', { instanceId: instance, limit }, options);
  }

  /** @param {string} consumer @param {string} instance @param {import('./index.js').IdInput} newLastId @param {import('./index.js').RequestOptions} [options] */
  commit(consumer, instance, newLastId, options) {
    text(instance, 'instanceId');
    return this.#call(this.#consumer(consumer) + '/commit', 'commit', { instanceId: instance, newLastId: id(newLastId) }, options);
  }

  /** @param {string} consumer @param {string | Date} startTime @param {import('./index.js').RequestOptions} [options] */
  recover(consumer, startTime, options) {
    return this.#call(this.#consumer(consumer) + '/recover', 'recover', { startTime: instant(startTime) }, options);
  }

  /** @param {import('./index.js').RequestOptions} [options] */
  ensureCurrentPartition(options) { return this.#call('/maintenance/ensure-current-partition', 'maintenance', undefined, options); }
  /** @param {import('./index.js').RequestOptions} [options] */
  ensureFuturePartitions(options) { return this.#call('/maintenance/ensure-future-partitions', 'maintenance', undefined, options); }
  /** @param {number} keepMonths @param {import('./index.js').RequestOptions} [options] */
  dropOldPartitions(keepMonths, options) { return this.#call('/maintenance/drop-older-than/' + int32(keepMonths, 'keepMonths'), 'maintenance', undefined, options); }
  /** @param {import('./index.js').RequestOptions} [options] */
  runMaintenance(options) { return this.#call('/maintenance/run', 'maintenance', undefined, options); }
  /** @param {import('./index.js').RequestOptions} [options] */
  cleanupConsumed(options) { return this.#call('/maintenance/cleanup-consumed', 'cleanup', undefined, options); }

  /** @param {string} path @param {string} operation @param {unknown} body @param {import('./index.js').RequestOptions} [options] @returns {Promise<any>} */
  async #call(path, operation, body, options = {}) {
    const raw = body === undefined ? undefined : encode(body);
    const controller = new AbortController();
    const caller = options.signal;
    const cancel = () => controller.abort();
    if (caller?.aborted) cancel();
    else caller?.addEventListener('abort', cancel, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => {
      if (!controller.signal.aborted) { timedOut = true; controller.abort(); }
    }, this.#timeout);
    try {
      // Call as a standalone function: injected/native fetch must not receive this client as its receiver.
      const fetcher = this.#fetch;
      const response = await fetcher(this.#url + '/api/event-bus' + path, {
        method: 'POST', headers: this.#headers, body: raw, signal: controller.signal,
      });
      const responseText = await response.text();
      let envelope;
      try { envelope = decode(responseText); } catch {
        throw new PgEventBusError(response.ok ? 'INVALID_RESPONSE' : 'HTTP_ERROR', 'Gateway returned an invalid JSON response', response.status);
      }
      if (!response.ok || envelope?.success === false) {
        const error = envelope?.error;
        const code = typeof error?.errorCode === 'string' ? error.errorCode : typeof envelope?.code === 'string' ? envelope.code : 'HTTP_ERROR';
        const details = Array.isArray(error?.details) ? error.details.filter((/** @type {unknown} */ item) => typeof item === 'string') : [];
        throw new PgEventBusError(code, 'Gateway request failed', response.status, details);
      }
      if (envelope?.success !== true || !Object.hasOwn(envelope, 'data')) throw new PgEventBusError('INVALID_RESPONSE', 'Gateway response envelope is invalid', response.status);
      try { return result(operation, envelope.data); } catch {
        throw new PgEventBusError('INVALID_RESPONSE', 'Gateway result is invalid', response.status);
      }
    } catch (error) {
      if (controller.signal.aborted) throw new PgEventBusError(timedOut ? 'TIMEOUT' : 'ABORTED', timedOut ? 'Gateway request timed out' : 'Gateway request canceled');
      if (error instanceof PgEventBusError) throw error;
      throw new PgEventBusError('NETWORK_ERROR', 'Gateway request failed');
    } finally {
      clearTimeout(timer);
      caller?.removeEventListener('abort', cancel);
    }
  }
}
