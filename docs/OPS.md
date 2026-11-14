# Adwa Cyber — Operations Guide

Everything an operator needs to deploy, monitor, back up, and upgrade
the platform. User-facing documentation lives in `docs/USER_GUIDE.md`.

## 1. Deployment (Docker — recommended)

```bash
cp .env.example .env
# edit .env: AUTH_SECRET (openssl rand -base64 48), POSTGRES_PASSWORD,
#            SITE_ADDRESS, SMTP_*
docker compose up -d
curl -s https://<host>/api/health   # expect {"status":"ok",...}
```

The stack: **Caddy** (automatic HTTPS via Let's Encrypt) → **app**
(Next.js standalone; runs migrations + idempotent seeds on boot) →
**PostgreSQL 16** (persistent volume `pgdata`).

First-boot accounts: the seed creates the demo users (change or disable
them for real pilots — see `scripts/seed-users.ts`). Production
hardening notes:

- `SEED_ON_BOOT=0` after first boot (prevents reseeding on restart),
- remove the dev `ports:` block on the db service if you opened it,
- keep `.env` out of version control and backups of it encrypted.

### Non-Docker deploy

```bash
bun install && bun run build
AUTH_SECRET=... DATABASE_URL=postgresql://... \
  NODE_ENV=production PORT=3000 bun .next/standalone/server.js
```

Behind nginx/Caddy with TLS (session cookies are `Secure` in production —
plain HTTP will break logins). Migrations: `bunx prisma db push` (or
`prisma migrate` once migration history is adopted).

## 2. Monitoring

- **Liveness/readiness**: `GET /api/health` — 200 `ok` / 503 `degraded`,
  reports `database` (SELECT 1 latency) and `authSecret` checks, uptime,
  version. The Docker healthcheck uses it; point any uptime monitor
  (UptimeRobot, Better Stack, …) at it.
- **Container health**: `docker compose ps` shows health status per service.
- **Logs**: `docker compose logs -f app` (or the journal/unit for bare
  metal). Auth events are queryable in-app under **Audit**.
- **Alerting (minimum viable)**: external uptime check every 5 min with
  alert on 2 consecutive failures + a daily cron that verifies the
  newest backup exists.

## 3. Backups & restores

| Task | Command |
|---|---|
| Backup now | `sh scripts/backup-db.sh` (auto-detects SQLite/PostgreSQL; sha256 sidecar; keeps last 30) |
| Scheduled | cron: `30 2 * * * cd /opt/adwa && sh scripts/backup-db.sh >> /var/log/adwa-backup.log 2>&1` |
| Restore | `sh scripts/restore-db.sh backups/adwa-YYYYMMDD-HHMMSS.db.gz` (verifies checksum, checks integrity, keeps the pre-restore copy; refuses while the app is running) |
| Restore drill | `bash scripts/restore-drill.sh` — backup → restore to scratch → verify counts + integrity |

A backup that has never been restored is a hope, not a backup: run the
drill weekly (SEV-3 if it lapses two weeks — see INCIDENT_RUNBOOK).

## 4. Upgrades

1. `sh scripts/backup-db.sh` (pre-upgrade point-in-time)
2. `git pull` (or deploy the new image)
3. `docker compose up -d --build` — the entrypoint applies schema changes
   (`prisma db push`) and reseeds idempotently
4. `curl -s https://<host>/api/health` + a login round trip
5. CI must be green on the commit being deployed.

**`AUTH_SECRET` rotation** invalidates every session AND every encrypted
TOTP secret: after rotating, run `bun scripts/fix-mfa-secret.ts` (demo
officer) and tell users to re-enroll MFA. Do it only for a suspected
key compromise.

## 5. Routine administration

| Task | Command / path |
|---|---|
| Unlock all accounts (after an attack or a test run) | `bun scripts/reset-locks.ts` |
| Inspect DB state (orgs, users, scans) | `bun scripts/check-db.ts` |
| Re-seed demo data | `bun prisma/seed.ts` + the `scripts/seed-*` scripts (all idempotent) |
| Audit trail export | in-app Audit view (filterable); DB table `AuthEvent` |

## 6. Environment variables (see `.env.example` for the full annotated list)

Critical: `DATABASE_URL`, `AUTH_SECRET` (64+ chars), `APP_ORIGIN`
(used in emailed reset links — set when behind a proxy), `SMTP_*`
(unset → reset links go to the server log, never do this in production).

## 7. Known operational limits (MVP)

- **Rate limiting is in-memory and per-instance**: one app container
  only. Horizontal scaling requires a shared store (Redis) — post-MVP.
- **SQLite is dev-only**: use the PostgreSQL path in production.
- **Seeds on boot**: convenient for pilots; set `SEED_ON_BOOT=0` later.
- **Backups cover the database only**: uploaded evidence files (vault)
  live in the app container volume — mount and back up the evidence
  directory as well when the vault is in active use.
