"""Penjaga di level database, dipasang oleh migrasi dan oleh create_all saat dev/pengujian.

- Tabel jejak bersifat append-only: UPDATE dan DELETE ditolak trigger.
  Di produksi, cabut juga izin UPDATE/DELETE dari role aplikasi (lihat REVOKE_SQL).
- Token berstatus final tidak bisa diubah lagi (invarian #6).
"""

from sqlalchemy import text

APPEND_ONLY_TABLES = [
    "token_events",
    "unit_events",
    "wallet_entries",
    "ledger_entries",
    "audit_logs",
    "financing_agreements",
    "token_unit_history",
]
FINAL_TOKEN_STATUSES = ("RETURNED", "PRINTED", "CANCELLED")

REVOKE_SQL = "\n".join(f"REVOKE UPDATE, DELETE ON {t} FROM reidig_app;" for t in APPEND_ONLY_TABLES)


def _sqlite(conn) -> None:
    finals = ", ".join(f"'{s}'" for s in FINAL_TOKEN_STATUSES)
    for table in APPEND_ONLY_TABLES:
        for op in ("UPDATE", "DELETE"):
            conn.execute(
                text(
                    f"CREATE TRIGGER IF NOT EXISTS {table}_no_{op.lower()} BEFORE {op} ON {table} "
                    f"BEGIN SELECT RAISE(ABORT, '{table} is append-only'); END;"
                )
            )
    conn.execute(
        text(
            "CREATE TRIGGER IF NOT EXISTS tokens_final_immutable BEFORE UPDATE ON tokens "
            f"WHEN OLD.status IN ({finals}) "
            "BEGIN SELECT RAISE(ABORT, 'token in final status is immutable'); END;"
        )
    )


def _postgres(conn) -> None:
    finals = ", ".join(f"'{s}'" for s in FINAL_TOKEN_STATUSES)
    conn.execute(
        text(
            """
            CREATE OR REPLACE FUNCTION reidig_append_only() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION '% is append-only', TG_TABLE_NAME; END;
            $$ LANGUAGE plpgsql;
            """
        )
    )
    conn.execute(
        text(
            f"""
            CREATE OR REPLACE FUNCTION reidig_token_final_immutable() RETURNS trigger AS $$
            BEGIN
              IF OLD.status IN ({finals}) THEN
                RAISE EXCEPTION 'token in final status is immutable';
              END IF;
              RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            """
        )
    )
    for table in APPEND_ONLY_TABLES:
        conn.execute(text(f"DROP TRIGGER IF EXISTS {table}_append_only ON {table}"))
        conn.execute(
            text(
                f"CREATE TRIGGER {table}_append_only BEFORE UPDATE OR DELETE ON {table} "
                "FOR EACH ROW EXECUTE FUNCTION reidig_append_only()"
            )
        )
    conn.execute(text("DROP TRIGGER IF EXISTS tokens_final_immutable ON tokens"))
    conn.execute(
        text(
            "CREATE TRIGGER tokens_final_immutable BEFORE UPDATE ON tokens "
            "FOR EACH ROW EXECUTE FUNCTION reidig_token_final_immutable()"
        )
    )


def install_db_guards(conn) -> None:
    if conn.dialect.name == "sqlite":
        _sqlite(conn)
    elif conn.dialect.name == "postgresql":
        _postgres(conn)
