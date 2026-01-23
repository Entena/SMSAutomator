from pydantic import BaseModel


class SMSFilterPrediction(BaseModel):
    blocked: bool
    reason: str | None = None
    included_categories: list[str]
    excluded_categories: list[str]
