# PG EventBus

This document is the readable companion to `PG-EventBus.mht`.

Purpose:
- keep the original OneNote export as archival source
- provide a clean Markdown summary for agents and reviewers
- point implementation work at small, focused SQL files under `docs/sql/`

## What This Component Does

- durable PostgreSQL-native event publication
- offset-based ordered consumption
- retry and deadletter handling
- monthly partitioned retention for the main event log
- function-only access with least-privilege execution

## What Is Intentionally Excluded From V1

- monitoring dashboards
- alerting views
- external brokers
- multi-region replication design
- workflow orchestration

## SQL File Layout

- `docs/sql/bootstrap.sql`
- `docs/sql/create_month_partition.sql`
- `docs/sql/ensure_current_partition.sql`
- `docs/sql/ensure_future_partitions.sql`
- `docs/sql/drop_partition_older_than.sql`
- `docs/sql/maintenance.sql`
- `docs/sql/publish_event.sql`
- `docs/sql/register_consumer.sql`
- `docs/sql/init_consumer_at_time.sql`
- `docs/sql/acquire_consumer_lock.sql`
- `docs/sql/renew_consumer_lock.sql`
- `docs/sql/poll_events.sql`
- `docs/sql/commit_consumer_offset.sql`
- `docs/sql/release_consumer_lock.sql`
- `docs/sql/cleanup_consumed.sql`
- `docs/sql/cleanup_older_than.sql`
- `docs/sql/schedule_retry.sql`
- `docs/sql/poll_retry.sql`
- `docs/sql/poll_deadletter.sql`
- `docs/sql/delete_retry.sql`
- `docs/sql/move_to_deadletter.sql`
- `docs/sql/grant_access.sql`

## Reading Order

1. `bootstrap.sql`
2. `create_month_partition.sql`
3. `ensure_current_partition.sql`
4. `ensure_future_partitions.sql`
5. `drop_partition_older_than.sql`
6. `maintenance.sql`
7. `publish_event.sql`
8. `register_consumer.sql`
9. `init_consumer_at_time.sql`
10. `acquire_consumer_lock.sql`
11. `renew_consumer_lock.sql`
12. `poll_events.sql`
13. `commit_consumer_offset.sql`
14. `release_consumer_lock.sql`
15. `cleanup_consumed.sql`
16. `cleanup_older_than.sql`
17. `schedule_retry.sql`
18. `poll_retry.sql`
19. `poll_deadletter.sql`
20. `delete_retry.sql`
21. `move_to_deadletter.sql`
22. `grant_access.sql`

## Notes

- The SQL is adapted from the original OneNote draft for readability.
- The repository keeps the original `.mht` file unchanged.
- The extracted SQL is organized for direct agent consumption.
