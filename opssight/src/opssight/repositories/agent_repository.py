from datetime import datetime
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.agent import Agent


def create_agent(
    session: Session,
    agent_id: str,
    host_id: UUID,
    version: str | None = None,
    last_config_version: str | None = None,
) -> Agent:
    agent = Agent(
        agent_id=agent_id,
        host_id=host_id,
        version=version,
        last_config_version=last_config_version,
    )

    session.add(agent)
    session.flush()

    return agent


def get_agent_by_agent_id(
    session: Session,
    agent_id: str,
) -> Agent | None:
    statement = select(Agent).where(
        Agent.agent_id == agent_id
    )

    return session.scalar(
        statement
    )


def mark_agent_seen(
    session: Session,
    agent: Agent,
    seen_at: datetime,
    version: str | None = None,
    last_config_version: str | None = None,
) -> Agent:
    agent.last_seen_at = seen_at

    if version is not None:
        agent.version = version

    if last_config_version is not None:
        agent.last_config_version = last_config_version

    session.flush()

    return agent