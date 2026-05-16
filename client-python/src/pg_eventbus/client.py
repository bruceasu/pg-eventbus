from __future__ import annotations

import json
import os
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from .errors import PgEventBusError
from .types import (
    contract.me.asu.pg_eventbus.CommitResultDto,
    contract.me.asu.pg_eventbus.MaintenanceResultDto,
    contract.me.asu.pg_eventbus.PollResultDto,
    contract.me.asu.pg_eventbus.PublishResultDto,
    contract.me.asu.pg_eventbus.RecoverResultDto,
)


class _Runner:
    def __init__(self, command: str | None = None, tool_dir: str | None = None, timeout_seconds: float = 30.0):
        self.command = command or self._resolve_command(tool_dir)
        self.timeout_seconds = timeout_seconds

    def run(self, sql: str) -> str:
        args = [self.command, "-q", "-t", "--no-psqlrc", "-c", sql]
        completed = subprocess.run(
            args,
            capture_output=True,
            text=True,
            timeout=self.timeout_seconds,
            check=False,
        )
        if completed.returncode != 0:
            raise PgEventBusError("PSQL_ERROR", completed.stderr.strip() or completed.stdout.strip())
        return completed.stdout

    @staticmethod
    def _resolve_command(tool_dir: str | None) -> str:
        explicit = os.environ.get("PGEVENTBUS_PSQL_COMMAND", "").strip()
        if explicit:
            return explicit
        system = shutil.which("psql")
        if system:
            return system
        candidate = _local_tool(tool_dir)
        if candidate:
            return candidate
        return "psql"


def _local_tool(tool_dir: str | None) -> str:
    base_dir = tool_dir or os.environ.get("PGEVENTBUS_PGSQL_CLIENT_DIR", "").strip()
    if not base_dir:
        base_dir = _find_local_tool_dir()
    bin_dir = Path(base_dir) / "bin"
    candidates = ["psql-go.exe", "psql-go"] if os.name == "nt" else ["psql-go", "psql-go.exe"]
    for name in candidates:
        path = bin_dir / name
        if path.is_file():
            return str(path)
    return ""


def _find_local_tool_dir() -> str:
    current = Path.cwd().resolve()
    for candidate in [current, *current.parents]:
        tool_dir = candidate / "tools" / "pgsql-client"
        if tool_dir.is_dir():
            return str(tool_dir)
    return ""


def _quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _quote_ident(value: str) -> str:
    return '"' + value.strip().replace('"', '""') + '"'


def _resolve_schema(schema: str | None) -> str:
    explicit = (schema or "").strip()
    if explicit:
        return explicit
    env_schema = os.environ.get("PGEVENTBUS_SCHEMA", "").strip()
    if env_schema:
        return env_schema
    return "bus"


def _json(value) -> str:
    if value is None:
        return "'{}'::jsonb"
    return _quote(json.dumps(value, ensure_ascii=False)) + "::jsonb"


def _schema_function(schema: str, name: str) -> str:
    return f"{_quote_ident(schema)}.{_quote_ident(name)}"


def _publish_sql(schema: str, source_system: str, target_system: str, event_type: str, payload) -> str:
    return (
        "SELECT json_build_object('eventId', "
        f"{_schema_function(schema, 'publish_event')}("
        f"{_quote(source_system)}, {_quote(target_system)}, {_quote(event_type)}, {_json(payload)}));"
    )


def _poll_sql(schema: str, consumer_name: str, instance_id: str, limit: int) -> str:
    return f"""
WITH events AS (
    SELECT id, source_system, target_system, event_type, payload, created_at
      FROM {_schema_function(schema, 'poll_events')}({_quote(consumer_name)}, {int(limit)})
),
consumer AS (
    SELECT consumer_name, last_event_id, lock_owner, lock_until
      FROM {_schema_function(schema, 'consumer_registry')}
     WHERE consumer_name = {_quote(consumer_name)}
)
SELECT json_build_object(
    'consumer', (
        SELECT json_build_object(
            'consumerName', c.consumer_name,
            'instanceId', {_quote(instance_id)},
            'locked', (c.lock_owner IS NOT NULL AND c.lock_owner = {_quote(instance_id)} AND c.lock_until >= now()),
            'lockOwner', COALESCE(c.lock_owner, ''),
            'lockAcquiredAt', NULL,
            'lockExpiresAt', c.lock_until,
            'lastCommittedEventId', c.last_event_id,
            'currentOffset', c.last_event_id,
            'batchSize', {int(limit)}
        )
          FROM consumer c
    ),
    'events', COALESCE((
        SELECT json_agg(json_build_object(
            'id', e.id,
            'sourceSystem', e.source_system,
            'targetSystem', e.target_system,
            'eventType', e.event_type,
            'payload', e.payload,
            'createdAt', e.created_at
        ))
          FROM events e
    ), '[]'::json),
    'batchSize', {int(limit)},
    'lockAcquired', true,
    'polledAt', now()
);
"""


def _commit_sql(schema: str, consumer_name: str, instance_id: str, new_last_id: int) -> str:
    return (
        "SELECT json_build_object("
        f"'committed', {_schema_function(schema, 'commit_consumer_offset')}({_quote(consumer_name)}, {_quote(instance_id)}, {int(new_last_id)}), "
        f"'newLastCommittedEventId', {int(new_last_id)});"
    )


def _recover_sql(schema: str, consumer_name: str, start_time: datetime) -> str:
    ts = _quote(start_time.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"))
    return f"""
WITH init AS (
    SELECT {_schema_function(schema, 'init_consumer_at_time')}({_quote(consumer_name)}, {ts}::timestamptz) AS initialized_offset
),
consumer AS (
    SELECT consumer_name, last_event_id, lock_owner, lock_until
      FROM {_schema_function(schema, 'consumer_registry')}
     WHERE consumer_name = {_quote(consumer_name)}
)
SELECT json_build_object(
    'initializedOffset', (SELECT initialized_offset FROM init),
    'consumer', (
        SELECT json_build_object(
            'consumerName', c.consumer_name,
            'instanceId', '',
            'locked', false,
            'lockOwner', COALESCE(c.lock_owner, ''),
            'lockAcquiredAt', NULL,
            'lockExpiresAt', c.lock_until,
            'lastCommittedEventId', c.last_event_id,
            'currentOffset', c.last_event_id,
            'batchSize', 0
        )
          FROM consumer c
    )
);
"""


def _maintenance_sql(schema: str, action: str, statement: str = "") -> str:
    if not statement:
        statement = f"SELECT {_schema_function(schema, 'maintenance')}();"
    return f"""
WITH effect AS (
    {statement}
)
SELECT json_build_object(
    'executed', true,
    'actions', json_build_array({_quote(action)}),
    'completedAt', now()
);
"""


class PgEventBusClient:
    def __init__(self, command: str | None = None, tool_dir: str | None = None, timeout_seconds: float = 30.0, runner: _Runner | None = None, schema: str | None = None):
        self._runner = runner or _Runner(command=command, tool_dir=tool_dir, timeout_seconds=timeout_seconds)
        self._schema = _resolve_schema(schema)

    def publish(self, source_system: str, target_system: str, event_type: str, payload) -> contract.me.asu.pg_eventbus.PublishResultDto:
        return contract.me.asu.pg_eventbus.PublishResultDto.from_dict(self._run_json(_publish_sql(self._schema, source_system, target_system, event_type, payload)))

    def poll(self, consumer_name: str, instance_id: str, limit: int | None = None) -> contract.me.asu.pg_eventbus.PollResultDto:
        return contract.me.asu.pg_eventbus.PollResultDto.from_dict(self._run_json(_poll_sql(self._schema, consumer_name, instance_id, limit or 100)))

    def commit(self, consumer_name: str, instance_id: str, new_last_id: int) -> contract.me.asu.pg_eventbus.CommitResultDto:
        return contract.me.asu.pg_eventbus.CommitResultDto.from_dict(self._run_json(_commit_sql(self._schema, consumer_name, instance_id, new_last_id)))

    def recover(self, consumer_name: str, start_time: datetime | None = None) -> contract.me.asu.pg_eventbus.RecoverResultDto:
        start_time = start_time or datetime.now(timezone.utc)
        return contract.me.asu.pg_eventbus.RecoverResultDto.from_dict(self._run_json(_recover_sql(self._schema, consumer_name, start_time)))

    def run_maintenance(self) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._run_json(_maintenance_sql(self._schema, "maintenance")))

    def ensure_current_partition(self) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._run_json(_maintenance_sql(self._schema, "ensure-current", f"SELECT {_schema_function(self._schema, 'ensure_current_partition')}();")))

    def ensure_future_partitions(self) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._run_json(_maintenance_sql(self._schema, "ensure-future", f"SELECT {_schema_function(self._schema, 'ensure_future_partitions')}(6);")))

    def drop_old_partitions(self, keep_months: int) -> contract.me.asu.pg_eventbus.MaintenanceResultDto:
        return contract.me.asu.pg_eventbus.MaintenanceResultDto.from_dict(self._run_json(_maintenance_sql(self._schema, "drop-old", f"SELECT {_schema_function(self._schema, 'drop_partition_older_than')}({int(keep_months)});")))

    def cleanup_consumed(self) -> int:
        return self._run_int(f"SELECT {_schema_function(self._schema, 'cleanup_consumed')}();")

    def _run_json(self, sql: str):
        raw = self._runner.run(sql).strip()
        return json.loads(raw)

    def _run_int(self, sql: str) -> int:
        raw = self._runner.run(sql).strip()
        return int(raw)
