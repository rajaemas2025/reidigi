from app.admin.models import AuditLog


def audit(db, actor, action: str, entity: str, detail: str = "") -> None:
    """actor: AdminUser, User, atau None untuk sistem."""
    if actor is None:
        actor_type, actor_id, name = "SYSTEM", None, "Sistem"
    elif actor.__class__.__name__ == "AdminUser":
        actor_type, actor_id, name = "ADMIN", str(actor.id), actor.name
    else:
        actor_type, actor_id, name = "USER", str(actor.id), actor.name
    db.add(AuditLog(actor_type=actor_type, actor_id=actor_id, actor_name=name, action=action, entity=entity, detail=detail))


def actor_ref(actor) -> tuple[str, str | None]:
    if actor is None:
        return "SYSTEM", None
    return ("ADMIN" if actor.__class__.__name__ == "AdminUser" else "USER"), str(actor.id)
