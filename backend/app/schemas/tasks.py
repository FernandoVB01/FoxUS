from __future__ import annotations

import uuid
from datetime import datetime
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field


class MicrotaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    task_id: uuid.UUID
    title: str
    description: str
    estimated_seconds: int
    success_criteria: str
    model_name: str
    created_at: datetime


class TaskCreate(BaseModel):
    title: str = Field(min_length=3, max_length=255, examples=["Preparar 12 diapositivas para mi exposición"])
    description: str | None = Field(
        default=None,
        max_length=2000,
        examples=["Exposición final de Sistemas Distribuidos con demo en vivo."],
    )
    deadline: datetime | None = None
    needs_pressure: bool = False
    source: Literal["manual", "google_tasks"] = "manual"
    external_id: str | None = None


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=3, max_length=255)
    description: str | None = Field(default=None, max_length=2000)
    deadline: datetime | None = None
    needs_pressure: bool | None = None
    status: Literal["pending", "in_progress", "completed"] | None = None


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    title: str
    description: str | None = None
    source: str
    external_id: str | None = None
    deadline: datetime | None = None
    needs_pressure: bool = False
    status: str
    created_at: datetime
    microtasks: list[MicrotaskRead] = Field(default_factory=list)
