# Adwa Cyber — Deployment Guide

Three supported paths. The **reference deployment** is Docker Compose
(decision D5); Neon (or any managed Postgres) works for hosted dev; the
standalone build suits a single VM behind your own reverse proxy.

## 0. Non-negotiables (every path)

| Variable | Rule |
|---|---|
| `AUTH_SECRET` | 64+ chars (`openssl rand -base64 48`). Signs JWTs and derives the AES-256-GCM key for TOTP secrets. Missing/short → the server **refuses to boot** in production (`src/lib/env.ts`). Rotating it invalidates sessions + MFA seeds (then run `bun scripts/fix-mfa-secret.ts`). |
| `DATABASE_URL` | `file:…` (dev) or `postgresql://…` (production). Invalid scheme → boot refusal. |
| `DATA_DIR` | Where evidence/gate artefacts are stored. Compose sets `/data` on a named volume. **Back it up with the database** — artefacts are checksum-verified on download. |
| `APP_ORIGIN` | Public origin for reset/verification links when running behind a proxy. |
| `SMTP_*` | Unset → reset links are printed to the app log (console transport). Do not run a real pilot without SMTP. |

Seeding is guarded in production (decision D6): bootstrap seeds
(catalogue/pilot users/SOC/gates) need `SEED_ON_BOOT=1`; demo seeds need
`ALLOW_DEMO_SEED=1`. Never set `ALLOW_DEMO_SEED=1` on a tenant database.

## 1. Docker Compose (reference)

```bash
cp .env.example .env
# set: AUTH_SECRET, POSTGRES_PASSWORD, SITE_ADDRESS (real domain → Let's Encrypt)
docker compose up -d --build
curl -s https://<host>/api/health     # {"status":"ok",...}
```

- **db** — PostgreSQL 16 alpine, health-checked (`pg_isready`), data on the
  `pgdata` volume.
- **app** — Next.js standalone (Bun). Entrypoint applies the schema with
  `prisma db push` (retry loop; **no** `--accept-data-loss` — destructive
  schema changes fail loudly, decision D7), runs the bootstrap seeds when
  `SEED_ON_BOOT=1`, then serves. Evidence artefacts on the `app-data` volume.
- **caddy** — automatic HTTPS termination, proxies to `app:3000`.
- Container healthcheck: `curl /api/health` (30 s interval).

CI proves this path on every push (compose boot + health assertions).

Upgrade: `git pull && docker compose up -d --build` — schema + seeds are
idempotent.

## 2. Neon / managed PostgreSQL (hosted dev)

1. Create a project; copy the **direct (non-pooler)** connection string.
2. Use the direct URL for schema application (PgBouncer transaction mode can
   break long DDL):
   ```bash
   DATABASE_URL="postgresql://…direct…" bunx prisma db push --skip-generate
   ```
   The app itself can run on the pooled URL.
3. `DATABASE_URL=… bun prisma/seed.ts` + `bun scripts/seed-users.ts` (guards
   pass outside production).

## 3. Standalone (`bun run build`)

```bash
bun run build
NODE_ENV=production PORT=3000 \
DATABASE_URL=postgresql://… AUTH_SECRET=… DATA_DIR=/var/lib/adwa \
bun .next/standalone/server.js
```

Verified by `scripts/prod-smoke.py` (captcha → login → authed round-trip).
Gotchas: runtime env must be real (the standalone server does not read the
project root `.env`); cookies are `Secure` in production → serve over HTTPS.

## Operations

- Backups: `sh scripts/backup-db.sh` (auto-detects Postgres via
  `DATABASE_URL`) **plus** the evidence volume. Restore:
  `sh scripts/restore-db.sh backups/<file>.sql.gz`; drill:
  `sh scripts/restore-drill.sh`.
- Monitoring: `/api/health` (liveness + DB probe), `/api` (service banner),
  Caddy access logs, `docker compose logs app`.
- Lockouts: `bun scripts/reset-locks.ts` clears brute-force lockouts.
- Day-to-day runbook: `docs/OPS.md`; incidents: `docs/INCIDENT_RUNBOOK.md`.

## Hardening checklist for a public pilot

- [ ] Real `GOOGLE_CLIENT_ID/SECRET` (remove the mock `GOOGLE_*_URL` overrides)
- [ ] `RECAPTCHA_*` or `HCAPTCHA_*` configured (built-in captcha is dev-only, D4)
- [ ] `SMTP_HOST/PORT/USER/PASS` set and tested
- [ ] `SEED_ON_BOOT=0` once real tenants are onboarded; rotate demo passwords
- [ ] Remove the sandbox-only CSP `frame-ancestors https://*.space-z.ai`
      (`next.config.ts`) if you deploy outside this environment
- [ ] Rate limiter is in-memory (single instance) — add a shared store before
      scaling to multiple replicas
- [ ] Schedule `backup-db.sh` + evidence-volume snapshots
