from enum import Enum
from typing import Optional, Any
from pydantic import BaseModel
from datetime import datetime

class EventType(str, Enum):
    temp = "temp"
    timer = "timer"
    corrective_action = "corrective_action"
    alert = "alert"

class Severity(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"
    critical = "critical"

class FoodSafetyEvent(BaseModel):
    id: Optional[str] = None
    device_id: str
    site_id: str
    station: str
    probe_id: str
    type: EventType
    value: Any
    ts: datetime
    payload: Optional[dict] = None
