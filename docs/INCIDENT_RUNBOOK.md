# Adwa Cyber — Incident Response Runbook

How the platform team responds to security and availability incidents.
Objectives: contain fast, preserve evidence, communicate honestly,
feed lessons back into the product. Aligned with NIST SP 800-61
(the same lifecycle the SOC module implements for customers).

## Severity levels

| Level | Definition | Examples | Response |
|---|---|---|---|
| SEV-1 | Customer data or platform integrity at risk | Confirmed tenant-data leak across organisations; evidence-vault tampering; full outage during a pilot review | Page on-call NOW; response begins < 15 min; founder informed |
| SEV-2 | Security control degraded or single-tenant impact | Auth bypass report; MFA failing for one org; SLA-missed backups; audit trail gaps | Response < 1 h during working hours |
| SEV-3 | Limited impact, workaround exists | Login CAPTCHA broken for some browsers; report PDF failing; degraded (not down) health check | Same/next business day |

## Phase 1 — Detect & triage (0–15 min)

Signals: uptime monitor on `/api/health`, CI failures, customer report,
anomalous audit events (e.g. burst of `LOGIN_FAILED`, `TOKEN_REUSE_DETECTED`).

1. Classify severity (table above). When unsure, over-classify.
2. Open an incident channel/note; timestamp everything.
3. **Preserve evidence first**: snapshot logs and DB BEFORE restarts —
   `sh scripts/backup-db.sh` plus copy of the app log.
   Restarting destroys in-memory state (rate-limiter buckets).

## Phase 2 — Contain (target: 30 min for SEV-1)

- **Suspected account compromise** (valid for any severity):
  disable the user (Team → disable), which kills sessions; if the
  platform itself is suspect, rotate `AUTH_SECRET` (invalidates ALL
  sessions + MFA seeds — re-run `bun scripts/fix-mfa-secret.ts`, users
  re-enroll; communicate first).
- **Data-leak suspicion between tenants**: freeze the affected orgs'
  scans, export audit events for the window, escalate to founder.
  Regulator notification duties (Procl. 1321/2024 breach article) are
  the CUSTOMER's obligation for their data — provide the audit export
  to support their filing.
- **Outage**: `docker compose ps`, `docker compose logs app`,
  `/api/health` checks show which component (db / authSecret).
  Restore path: `sh scripts/restore-db.sh backups/<latest>.gz` after
  stopping the app (the script refuses to run against a live app).
- **Malformed/attack traffic**: block at the reverse proxy (Caddy)
  or cloud firewall; the platform rate limiter absorbs brute force.

## Phase 3 — Eradicate & recover

1. Identify root cause from the preserved evidence.
2. Patch, rotate credentials touched, re-run the affected test suites
   (`bash scripts/run-api-tests.sh`, `bash scripts/run-recovery-tests.sh`).
3. Verify: `/api/health` ok, a full login → scan → report round trip.
4. Post-incident DB backup taken and kept.

## Phase 4 — Follow-up (within 5 working days)

Write the post-incident report (store under `docs/incidents/`):

- timeline (detect → contain → resolve), impact (tenants, data, downtime),
- root cause and the fix,
- what the audit trail contributed (or why not),
- action items with owners — engineering changes go through CI.

## Communication templates

**To affected customers (SEV-1/2):**
> On <date> we detected <issue> affecting <scope>. We contained it at
> <time> by <action>. Your data <was/was not> <exposed/unavailable>.
> Evidence preservation: <backups/audit export reference>. We will share
> the full post-incident report by <date>. Contact: <channel>.

**To the platform team (incident channel opener):**
> SEV-<n> — <one-line summary>
> Detected: <time> via <signal>. Impact: <tenants/users>.
> Containment: <action or "investigating">. Next update: <time>.

## Standing readiness (verify weekly)

- [ ] Uptime monitor green on `/api/health`
- [ ] Backup from last night exists (`backups/` — 30 retained)
- [ ] Restore drill passed (`bash scripts/restore-drill.sh`)
- [ ] CI green (all suites)
- [ ] On-call contact list current
