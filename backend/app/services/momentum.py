from datetime import UTC, datetime, timedelta
from sqlalchemy import select
from app.models.entities import ChainLink, MomentumChain, Microtask, Hook, User
from app.services.timing import _aware


def expire_chains(db, user_id):
    now = datetime.now(UTC)
    for chain in db.scalars(select(MomentumChain).where(
        MomentumChain.user_id == user_id, MomentumChain.status == "active"
    )):
        if chain.expires_at and _aware(chain.expires_at) <= now:
            chain.status = "broken"
            chain.broken_at = now
    db.flush()


def record_completion(db, session):
    # Serialize a user's chain updates and make completion retries harmless.
    db.scalar(select(User).where(User.id == session.user_id).with_for_update())
    existing = db.scalar(select(ChainLink).where(ChainLink.session_id == session.id))
    if existing and existing.status == "completed":
        return existing
    expire_chains(db, session.user_id)
    micro = db.get(Microtask, session.microtask_id)
    link = existing or db.scalar(select(ChainLink).where(
        ChainLink.microtask_id == micro.id, ChainLink.user_id == session.user_id))
    if link and link.status == "completed":
        return link
    chain = db.get(MomentumChain, link.chain_id) if link else db.scalar(select(MomentumChain).where(
        MomentumChain.user_id == session.user_id, MomentumChain.task_id == micro.task_id,
        MomentumChain.status == "active"))
    if chain is None or chain.status != "active":
        chain = MomentumChain(user_id=session.user_id, task_id=micro.task_id,
                              links_completed=0, heat=0)
        db.add(chain)
        db.flush()
    if link is None:
        hook = db.get(Hook, session.hook_id) if session.hook_id else None
        link = ChainLink(chain_id=chain.id, user_id=session.user_id, microtask_id=micro.id,
                         link_index=chain.links_completed + 1,
                         technique=hook.technique if hook and hook.technique else "regla_2_min",
                         target_minutes=max(1, micro.estimated_seconds // 60))
        db.add(link)
    elif link.chain_id != chain.id:
        link.chain_id = chain.id
        link.link_index = 1
    now = datetime.now(UTC)
    link.session_id = session.id
    link.status = "completed"
    link.completed_at = now
    link.heat_points = 10 + min(chain.links_completed, 5) * 5
    chain.links_completed += 1
    chain.heat += link.heat_points
    chain.last_link_completed_at = now
    chain.expires_at = now + timedelta(hours=24)
    db.flush()
    return link


def category(heat):
    return "Fuego" if heat >= 100 else "Llama" if heat >= 40 else "Chispa" if heat else "Por encender"
