from __future__ import annotations

import json
import os
import unittest
from datetime import datetime, timezone
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from pg_eventbus import PgEventBusClient


class FakeRunner:
    def __init__(self):
        self.last_sql = ""

    def run(self, sql: str) -> str:
        self.last_sql = sql
        if "publish_event" in sql:
            return '{"eventId":11}'
        if "poll_events" in sql:
            return '{"consumer":{"consumerName":"consumer-a","instanceId":"instance-a","locked":true,"lockOwner":"instance-a","lockAcquiredAt":null,"lockExpiresAt":null,"lastCommittedEventId":7,"currentOffset":7,"batchSize":5},"events":[],"batchSize":5,"lockAcquired":true,"polledAt":"2026-05-16T00:00:00Z"}'
        if "commit_consumer_offset" in sql:
            return '{"committed":true,"newLastCommittedEventId":7}'
        if "init_consumer_at_time" in sql:
            return '{"initializedOffset":7,"consumer":{"consumerName":"consumer-a","instanceId":"","locked":false,"lockOwner":"","lockAcquiredAt":null,"lockExpiresAt":null,"lastCommittedEventId":7,"currentOffset":7,"batchSize":0}}'
        if "cleanup_consumed" in sql:
            return "17"
        return '{"executed":true,"actions":["maintenance"],"completedAt":"2026-05-16T00:00:00Z"}'


class PgEventBusClientTests(unittest.TestCase):
    def test_flat_api(self):
        client = PgEventBusClient(runner=FakeRunner())
        published = client.publish("source", "target", "event", {"hello": "world"})
        self.assertEqual(11, published.event_id)
        self.assertIn('"bus"."publish_event"', client._runner.last_sql)

        polled = client.poll("consumer-a", "instance-a", 5)
        self.assertTrue(polled.lock_acquired)

        committed = client.commit("consumer-a", "instance-a", 7)
        self.assertTrue(committed.committed)

        recovered = client.recover("consumer-a", datetime(2026, 5, 16, tzinfo=timezone.utc))
        self.assertEqual(7, recovered.initialized_offset)

        self.assertTrue(client.run_maintenance().executed)
        self.assertTrue(client.ensure_current_partition().executed)
        self.assertTrue(client.ensure_future_partitions().executed)
        self.assertTrue(client.drop_old_partitions(3).executed)
        self.assertEqual(17, client.cleanup_consumed())

    def test_custom_schema(self):
        client = PgEventBusClient(runner=FakeRunner(), schema="analytics")
        client.run_maintenance()
        self.assertIn('"analytics"."maintenance"', client._runner.last_sql)


if __name__ == "__main__":
    unittest.main()
