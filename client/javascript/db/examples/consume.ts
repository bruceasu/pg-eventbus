import { PgEventBusClient, type Queryable, type PollResultDto } from '../index.js';

// Pass a pg Pool/Client configured by the host, outside an unrelated transaction.
export async function consumeOnce(database: Queryable): Promise<void> {
  const client = new PgEventBusClient(database, { schema: 'bus', batchSize: 100, lockTtlSeconds: 30 });
  const batch: PollResultDto<{ price: number }> = await client.poll('pricing', 'worker-1');
  if (!batch.lockAcquired) return;
  for (const event of batch.events) {
    console.log(event.id.toString(), event.payload.price);
    // Replace logging with durable, idempotent processing before committing.
  }
  const last = batch.events.at(-1);
  if (last && !(await client.commit('pricing', 'worker-1', last.id)).committed) {
    throw new Error('Lease expired or changed; offset was not committed');
  }
}
