# Adwa Cyber — Engineering Decisions

Short, dated record of product/ops decisions that shape the implementation.
Each entry: the decision, why, and where it lives in the code.

## D1 — An N/A answer must carry a justification note (min 5 characters) — 2026-11-12

An N/A in a compliance product is a **legal claim** ("this obligation does not
apply to us"). An unjustified N/A is unauditable and indistinguishable from a
skipped question at scoring time.

- Server: `src/app/api/scans/[id]/submit/route.ts` rejects with
  `400 NA_JUSTIFICATION_REQUIRED` (all offending codes listed).
- Client: `scan-wizard.tsx` shows a required "Why is this not applicable?"
  field and blocks submit until every N/A is justified.
- The same rule is mirrored in `scripts/scan-lifecycle-test.mjs` (7g) and
  `scripts/test-compliance.ts`.

## D2 — A scan cannot be submitted incomplete — 2026-11-12

Drafts are partial by design (autosave per question), but **submit requires
100% explicit answers**. Unanswered obligations return `400 INCOMPLETE` with
the `missing` codes — they can never silently score as "na" and inflate the
result. Completeness is tracked via `ScanResult.answeredAt` (stamped on every
explicit answer), never via `answer != "na"` — an intentional N/A is an
answer; the pre-set "na" placeholder is not.

## D3 — Password reset delivery: console/SMTP adapter, no fake email — 2026-11-12

Phase 1 (shipped): console transport logs the reset link (dev/pilot) and a
real SMTP adapter activates when `SMTP_HOST` is set (`src/lib/auth/mail.ts`).
A logged-in password change exists (`POST /api/auth/password`) with current-
password re-authentication and full session revocation. We never claim "email
sent" when no mail transport is configured.

## D4 — Built-in captcha is dev/pilot-only; production uses a third party — 2026-11-12

The SVG captcha's codes are drawn from **crypto-secure** randomness
(`crypto.randomInt`, WS-8), but the glyphs are still `<text>` nodes — a
determined bot can parse them. For public deployments configure
`RECAPTCHA_*` or `HCAPTCHA_*` (`verifyCaptcha` auto-detects; no code change).
The sandbox-only CSP `frame-ancestors https://*.space-z.ai` entry is the same
category of environment-specific exception — remove it for other deployments.

## D5 — Reference deployment is Docker Compose + PostgreSQL 16 — 2026-11-12

`docker compose up --build` boots app + Postgres (health-checked) + Caddy
(automatic TLS). Dev keeps SQLite (zero setup); the Prisma schemas
`schema.prisma` (sqlite) and `schema.postgres.prisma` are kept in sync and the
Docker build swaps in the Postgres variant. CI runs a compose boot +
`/api/health` smoke job on every push.

## D6 — Demo seeds are explicit and refuse to run in production — 2026-11-12

`bun run seed:demo` chains every demo seed. Each seed self-guards
(`scripts/seed-guard.ts` + inline guards in the `.cjs` seeds):

| Scope | Scripts | Runs in production only with |
|---|---|---|
| bootstrap | `prisma/seed.ts`, `seed-users`, `seed-soc`, `seed-gates` | `SEED_ON_BOOT=1` (compose pilot default) or `ALLOW_DEMO_SEED=1` |
| demo | `seed-evidence(-links)`, `seed-awareness`, `seed-sla`, `seed-trackc`, `seed-gate-evidence` | `ALLOW_DEMO_SEED=1` |

## D7 — Schema changes ship via `prisma db push`, not `migrate deploy` — 2026-11-12

The repo deliberately maintains two schema files (sqlite dev / postgres
docker) and has no migrations directory; every environment (dev, CI, the
container entrypoint) applies the schema with `db push`. The container
entrypoint runs `db push` **without** `--accept-data-loss`: additive upgrades
apply silently, destructive ones fail loudly and the operator decides.

## D8 — API errors converge on `{ error: { code, message, fieldErrors? } }` — 2026-11-12

New/edited routes use `src/lib/api-error.ts`. Legacy routes still returning
`{ error: "string" }` are normalized client-side (`src/lib/auth-client.ts`)
so a toast never renders `undefined`; they migrate to the canonical shape as
they are touched.

## D9 — Track C is enforcement, not decoration — 2026-11-12

Phase 4 (ACFS) and Phase 5 (CII) features exist behind gates that are
**enforced server-side**: an ACFS unlock is refused unless the register
actually evaluates open (`409` + audit); a CII system cannot advance to
`MONITORING_AUTHORIZED` while its operator's MOU × INSA gate is shut (`409`
+ audit). The platform ships no collection agents — readiness state only.
