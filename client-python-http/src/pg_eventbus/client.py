from __future__ import annotations

import json
from datetime import datetime, timezone
from urllib import error as urllib_error
from urllib import request as urllib_request

from .errors import PgEventBusError
from .types import (
    contract.me.asu.pg_eventbus.ApiResponse,
    contract.me.asu.pg_eventbus.CommitResultDto,
    contract.me.asu.pg_eventbus.MaintenanceResultDto,
    contract.me.asu.pg_eventbus.PollResultDto,
    contract.me.asu.pg_eventbus.PublishResultDto,
    contract.me.asu.pg_eventbus.RecoverResultDto,
)


class PgEventBusClient:
    def __init__(self, base_url: str, timeout_seconds: float = 30.0):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds

    def publish(self, source_system: str, target_system: str, event_type: str, payload) -> contract.me.asu.pg_eventbus.PublishResultDto:
        data = self._post("/api/event-bus/events", {
            "sourceSystem": source_system,
            "targetSystem": target_system,
            "eventType": event_type,
            "payload": payload,
        })
        return contract.me.asu.pg_eventbus.PublishResultDto.from_dict(data)

    def poll(self, consumer_name: str, instance_id: str, limit: int | None = None) -> contract.me.asu.pg_eventbus.PollResultDto:
        body = {"instanceId": instance_id}
        if limit is not None:
            body["limit"] = limit
        data = self._post(f"/api/event-bus/consumers/{consumer_name}/poll", body)
        return contract.me.asu.pg_eventbus.PollResultDto.from_dict(data)

    def commit(self, consumer_name: str, instance_id: str, new_last_id: int) -> contract.me.asu.pg_eventbus.CommitResultDto:
        data = self._post(f"/api/event-bus/consumers/{consumer_name}/commit", {
            "instanceId": instance_id,
            "newLastId": new_last_id,
        })
        return contract.me.asu.pg_eventbus.CommitResultDto.from_dict(data)

    def recover(self, consumer_name: str, start_time: datetime | None = None) -> contract.me.asu.pg_eventbus.RecoverResultDto:
        if start_time is None:
            start_time = datetime.now(timezone.utc)
        data = self._post(f"/api/event-bus/consumers/{consumer_name}/recover", {
            "startTime": start_time.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
        })
        return contract.me.asu.pg_eventbus.RecoverResultDto.from_dict(data)

    def run_maintenance(self) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._post("/api/event-bus/maintenance/run", None))

    def ensure_current_partition(self) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._post("/api/event-bus/maintenance/ensure-current-partition", None))

    def ensure_future_partitions(self) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._post("/api/event-bus/maintenance/ensure-future-partitions", None))

    def drop_old_partitions(self, keep_months: int) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._post(f"/api/event-bus/maintenance/drop-older-than/{keep_months}", None))

    def cleanup_consumed(self) -> int:
        data = self._post("/api/event-bus/maintenance/cleanup-consumed", None)
        if isinstance(data, int):
            return data
        raise PgEventBusError("INVALID_RESPONSE", "cleanup-consumed should return an integer")

    def _post(self, path: str, body) -> object:
        request = urllib_request.Request(
            url=f"{self.base_url}{path}",
            method="POST",
            headers={"Content-Type": "application/json"},
        )
        if body is not None:
            request.data = json.dumps(body).encode("utf-8")
        try:
            with urllib_request.urlopen(request, timeout=self.timeout_seconds) as response:
                raw = response.read().decode("utf-8")
        except urllib_error.HTTPError as exc:
            raw = exc.read().decode("utf-8") if exc.fp else ""
            raise PgEventBusError("HTTP_ERROR", f"unexpected http status {exc.code}: {raw}") from exc

        envelope = contract.me.asu.pg_eventbus.ApiResponse.from_dict(json.loads(raw))
        if not envelope.success:
            if envelope.error:
                raise PgEventBusError(envelope.error.error_code, envelope.error.error_message, envelope.error.details)
            raise PgEventBusError(envelope.code, envelope.message)
        return envelope.data