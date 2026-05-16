from __future__ import annotations

import json
import os
import sys
import unittest
from datetime import datetime, timezone
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from pg_eventbus import PgEventBusClient


class FakeResponse:
    def __init__(self, payload: str):
        self._payload = payload.encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return self._payload


class PgEventBusClientTests(unittest.TestCase):
    def test_flat_api(self):
        responses = {
            "http://example.test/api/event-bus/events": {"success": True, "code": "OK", "message": "event published", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"eventId": 11}, "error": None},
            "http://example.test/api/event-bus/consumers/consumer-a/poll": {"success": True, "code": "OK", "message": "poll completed", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"consumer": None, "events": [], "batchSize": 5, "lockAcquired": True, "polledAt": "2026-05-16T00:00:00Z"}, "error": None},
            "http://example.test/api/event-bus/consumers/consumer-a/commit": {"success": True, "code": "OK", "message": "commit completed", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"committed": True, "newLastCommittedEventId": 7}, "error": None},
            "http://example.test/api/event-bus/consumers/consumer-a/recover": {"success": True, "code": "OK", "message": "consumer recovered", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"initializedOffset": 7, "consumer": None}, "error": None},
            "http://example.test/api/event-bus/maintenance/run": {"success": True, "code": "OK", "message": "maintenance completed", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"executed": True, "actions": ["maintenance"], "completedAt": "2026-05-16T00:00:00Z"}, "error": None},
            "http://example.test/api/event-bus/maintenance/ensure-current-partition": {"success": True, "code": "OK", "message": "current partition ensured", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"executed": True, "actions": ["ensure-current"], "completedAt": "2026-05-16T00:00:00Z"}, "error": None},
            "http://example.test/api/event-bus/maintenance/ensure-future-partitions": {"success": True, "code": "OK", "message": "future partitions ensured", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"executed": True, "actions": ["ensure-future"], "completedAt": "2026-05-16T00:00:00Z"}, "error": None},
            "http://example.test/api/event-bus/maintenance/drop-older-than/3": {"success": True, "code": "OK", "message": "old partitions dropped", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": {"executed": True, "actions": ["drop-old"], "completedAt": "2026-05-16T00:00:00Z"}, "error": None},
            "http://example.test/api/event-bus/maintenance/cleanup-consumed": {"success": True, "code": "OK", "message": "consumed rows cleaned", "requestId": "", "timestamp": "2026-05-16T00:00:00Z", "data": 17, "error": None},
        }

        def fake_urlopen(request, timeout=30.0):
            return FakeResponse(json.dumps(responses[request.full_url]))

        client = PgEventBusClient("http://example.test")

        with patch("urllib.request.urlopen", side_effect=fake_urlopen):
            published = client.publish("source", "target", "event", {"hello": "world"})
            self.assertEqual(11, published.event_id)

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


if __name__ == "__main__":
    unittest.main()
