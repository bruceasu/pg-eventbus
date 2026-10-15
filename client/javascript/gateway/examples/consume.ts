import { PgEventBusClient, type PollResultDto } from '../index.js';

// No automatic polling, retries or lease release. Call this from your scheduler.
export async function consumeOnce(client: PgEventBusClient, signal?: AbortSignal): Promise<void> {
  const batch: PollResultDto<{ price: number }> = await client.poll('pricing', 'worker-1', 100, { signal });
  if (!batch.lockAcquired) return;
  for (const event of batch.events) {
    console.log(event.id.toString(), event.payload.price);
    // Replace logging with durable, idempotent processing before committing.
  }
  const last = batch.events.at(-1);
  if (last) {
    const result = await client.commit('pricing', 'worker-1', last.id, { signal });
    if (!result.committed) throw new Error('Lease expired or changed; offset was not committed');
  }
}
