/** @typedef {import('./index.js').IdInput} IdInput */

/** @param {IdInput} value @returns {bigint} */
export function id(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) throw new TypeError('ID must be a safe integer, bigint or decimal string');
  if (typeof value !== 'bigint' && typeof value !== 'number' && (typeof value !== 'string' || !/^-?\d{1,19}$/.test(value))) throw new TypeError('Invalid ID');
  const result = BigInt(value);
  if (result < -(1n << 63n) || result > (1n << 63n) - 1n) throw new RangeError('ID exceeds signed int64');
  return result;
}

/** @param {unknown} value @param {string} name @returns {string} */
export function text(value, name) {
  if (typeof value !== 'string' || !value.trim()) throw new TypeError(`${name} must not be blank`);
  return value;
}

/** @param {unknown} value @param {string} name @returns {number} */
export function int32(value, name) {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < -2147483648 || value > 2147483647) throw new RangeError(`${name} must be an int32`);
  return value;
}

/** @param {string | Date} value @returns {string} */
export function instant(value) {
  if (value instanceof Date) return value.toISOString();
  if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?(?:Z|[+-]\d\d:\d\d)$/.test(value) || !Number.isFinite(Date.parse(value))) throw new TypeError('startTime must be a valid ISO timestamp with timezone');
  const year = Number(value.slice(0, 4)), month = Number(value.slice(5, 7)), day = Number(value.slice(8, 10));
  const days = [31, year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (month < 1 || month > 12 || day < 1 || day > days[month - 1] || Number(value.slice(11, 13)) > 23 || Number(value.slice(14, 16)) > 59 || Number(value.slice(17, 19)) > 59) throw new TypeError('startTime has invalid calendar fields');
  return value;
}

/** @param {unknown} value @returns {string} */
export function encode(value) {
  const result = JSON.stringify(value, (_key, item) => {
    if (typeof item === 'bigint') {
      if (typeof JSON.rawJSON !== 'function') throw new TypeError('Runtime must support JSON.rawJSON for bigint');
      return JSON.rawJSON(item.toString());
    }
    if (typeof item === 'number' && (!Number.isFinite(item) || (Number.isInteger(item) && !Number.isSafeInteger(item)))) throw new RangeError('Use bigint for unsafe integer payload values');
    return item;
  });
  if (result === undefined) throw new TypeError('JSON value is required');
  return result;
}

/** @param {string} raw @returns {any} */
export function decode(raw) {
  return JSON.parse(raw, (_key, value, context) => {
    if (typeof value === 'number' && Number.isInteger(value) && !Number.isSafeInteger(value)) {
      if (!context?.source || !/^-?\d+$/.test(context.source)) throw new RangeError('Unsafe integer requires exact JSON source; exponential notation is unsupported');
      return BigInt(context.source);
    }
    if (typeof value === 'number' && !Number.isFinite(value)) throw new RangeError('Non-finite JSON number');
    return value;
  });
}

/** @param {any} value @returns {any} */
export function consumer(value) {
  if (!value || typeof value !== 'object') throw new TypeError('Missing consumer result');
  return { ...value, lastCommittedEventId: id(value.lastCommittedEventId), currentOffset: id(value.currentOffset) };
}

/** @param {string} operation @param {any} value @returns {any} */
export function result(operation, value) {
  if (operation === 'cleanup') return int32(value, 'cleanup result');
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError('Invalid result object');
  switch (operation) {
    case 'publish': return { ...value, eventId: id(value.eventId) };
    case 'commit':
      if (typeof value.committed !== 'boolean') throw new TypeError('Invalid commit result');
      return { ...value, newLastCommittedEventId: id(value.newLastCommittedEventId) };
    case 'recover': return { ...value, initializedOffset: id(value.initializedOffset), consumer: consumer(value.consumer) };
    case 'poll':
      if (!Array.isArray(value.events) || typeof value.lockAcquired !== 'boolean') throw new TypeError('Invalid poll result');
      return { ...value, consumer: consumer(value.consumer), events: value.events.map((/** @type {any} */ event) => ({ ...event, id: id(event.id) })) };
    default:
      if (value.executed !== true || !Array.isArray(value.actions) || typeof value.completedAt !== 'string') throw new TypeError('Invalid maintenance result');
      return value;
  }
}
