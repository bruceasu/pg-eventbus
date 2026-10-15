from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Generic, TypeVar

T = TypeVar("T")


def _parse_dt(value: str | None) -> datetime | None:
    if value is None:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


@dataclass(slots=True)
class FieldErrorDto:
    field: str
    rejected_value: str | None
    message: str


@dataclass(slots=True)
class ErrorDto:
    error_code: str
    error_message: str
    details: list[str]
    field_errors: list[FieldErrorDto]

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ErrorDto":
        return cls(
            error_code=data.get("errorCode", ""),
            error_message=data.get("errorMessage", ""),
            details=list(data.get("details") or []),
            field_errors=[
                FieldErrorDto(
                    field=item.get("field", ""),
                    rejected_value=item.get("rejectedValue"),
                    message=item.get("message", ""),
                )
                for item in (data.get("fieldErrors") or [])
            ],
        )


@dataclass(slots=True)
class ApiResponse(Generic[T]):
    success: bool
    code: str
    message: str
    request_id: str
    timestamp: datetime
    data: T | None
    error: ErrorDto | None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ApiResponse[Any]":
        return cls(
            success=bool(data.get("success")),
            code=data.get("code", ""),
            message=data.get("message", ""),
            request_id=data.get("requestId", ""),
            timestamp=_parse_dt(data.get("timestamp")) or datetime.now().astimezone(),
            data=data.get("data"),
            error=ErrorDto.from_dict(data["error"]) if data.get("error") else None,
        )


@dataclass(slots=True)
class EventDto:
    id: int
    source_system: str
    target_system: str
    event_type: str
    payload: Any
    created_at: datetime

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "EventDto":
        return cls(
            id=int(data.get("id", 0)),
            source_system=data.get("sourceSystem", ""),
            target_system=data.get("targetSystem", ""),
            event_type=data.get("eventType", ""),
            payload=data.get("payload"),
            created_at=_parse_dt(data.get("createdAt")) or datetime.now().astimezone(),
        )


@dataclass(slots=True)
class ConsumerDto:
    consumer_name: str
    instance_id: str
    locked: bool
    lock_owner: str
    lock_acquired_at: datetime | None
    lock_expires_at: datetime | None
    last_committed_event_id: int
    current_offset: int
    batch_size: int

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ConsumerDto":
        return cls(
            consumer_name=data.get("consumerName", ""),
            instance_id=data.get("instanceId", ""),
            locked=bool(data.get("locked")),
            lock_owner=data.get("lockOwner", ""),
            lock_acquired_at=_parse_dt(data.get("lockAcquiredAt")),
            lock_expires_at=_parse_dt(data.get("lockExpiresAt")),
            last_committed_event_id=int(data.get("lastCommittedEventId", 0)),
            current_offset=int(data.get("currentOffset", 0)),
            batch_size=int(data.get("batchSize", 0)),
        )


@dataclass(slots=True)
class PublishResultDto:
    event_id: int

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PublishResultDto":
        return cls(event_id=int(data.get("eventId", 0)))


@dataclass(slots=True)
class PollResultDto:
    consumer: ConsumerDto | None
    events: list[EventDto]
    batch_size: int
    lock_acquired: bool
    polled_at: datetime

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PollResultDto":
        consumer = data.get("consumer")
        return cls(
            consumer=ConsumerDto.from_dict(consumer) if consumer else None,
            events=[EventDto.from_dict(item) for item in (data.get("events") or [])],
            batch_size=int(data.get("batchSize", 0)),
            lock_acquired=bool(data.get("lockAcquired")),
            polled_at=_parse_dt(data.get("polledAt")) or datetime.now().astimezone(),
        )


@dataclass(slots=True)
class CommitResultDto:
    committed: bool
    new_last_committed_event_id: int

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "CommitResultDto":
        return cls(
            committed=bool(data.get("committed")),
            new_last_committed_event_id=int(data.get("newLastCommittedEventId", 0)),
        )


@dataclass(slots=True)
class RecoverResultDto:
    initialized_offset: int
    consumer: ConsumerDto | None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "RecoverResultDto":
        consumer = data.get("consumer")
        return cls(
            initialized_offset=int(data.get("initializedOffset", 0)),
            consumer=ConsumerDto.from_dict(consumer) if consumer else None,
        )


@dataclass(slots=True)
class MaintenanceResultDto:
    executed: bool
    actions: list[str]
    completed_at: datetime

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MaintenanceResultDto":
        return cls(
            executed=bool(data.get("executed")),
            actions=list(data.get("actions") or []),
            completed_at=_parse_dt(data.get("completedAt")) or datetime.now().astimezone(),
        )