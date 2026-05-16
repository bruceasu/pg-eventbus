class PgEventBusError(RuntimeError):
    def __init__(self, code: str, message: str, details: list[str] | None = None):
        self.code = code
        self.message = message
        self.details = details or []
        detail_text = f" ({'; '.join(self.details)})" if self.details else ""
        super().__init__(f"{code}: {message}{detail_text}")
