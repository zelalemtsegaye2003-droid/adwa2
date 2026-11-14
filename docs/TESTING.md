# Adwa Cyber — Testing Guide

683 checks across 15 suites, all green (2026-11-12). Three runner scripts,
each self-contained (they start/stop their own dev stack because background
processes do not survive between shell calls in some environments):

```bash
bash scripts/run-api-tests.sh       # 10 API suites — 465 checks
bash scripts/ci-run-auth-suites.sh  # 4 auth suites — 180 checks
bash scripts/run-recovery-tests.sh  # recovery suite — 38 checks
```

`bun run test:integration` is an alias for the first. CI
(`.github/workflows/ci.yml`) runs lint → typecheck → build → the full battery
→ a Docker compose boot/health smoke.

## Seed prerequisites per suite

Suites are deterministic; several depend on seeded fixtures. The runner
(`scripts/run-api-tests.sh`) re-seeds automatically before these suites:

| Suite | Checks | Requires (auto-seeded) |
|---|---|---|
| `ethiocert-bind-test` | 26 | `seed-soc.cjs` (deterministic indicators) |
| `soc-test` | 91 | `seed-soc.cjs` (RQ3 latencies 6/8/21 min, median 8) |
| `sla-test` | 32 | `seed-sla.cjs` (Wasasa overdue + "historical" scan) |
| `report-test` | 25 | completed demo scan (base `db:seed`) |
| `rules-engine-test` | 27 | `seed-evidence-links.cjs` (ART17-02, NBE-05/12/17/21/24/28 links, NBE-14 manual) |
| `scan-lifecycle-test` | 47 | base seed only — includes the WS-2 submit-hardening matrix (INCOMPLETE / NA_JUSTIFICATION_REQUIRED / DUPLICATE_RULE / …) |
| `evidence-security-test` | 11 | base seed only |
| `gates-test` | 56 | `seed-gates.ts` (8 criteria) |
| `trackc-test` | 36 | `seed-trackc.cjs` (4 operators, mixed gate states, ACFS 13 preconditions) |
| `awareness-test` | 82 | `seed-awareness.cjs` (5 schools, 15 modules, RCT dataset) |
| `awareness-tools-api-test` | 32 | base seed only |
| `test-auth` / `test-owasp` / `test-compliance` / `test-google` | 54 / 67 / 44 / 15 | base seed + demo users; mock Google on :3010 |
| `test-password-reset` | 38 | demo users; dev-server console mail log at `/tmp/adwa-dev.log` |

## Conventions

- Every suite logs in through the real captcha flow (parses the SVG) — no
  auth bypasses in tests.
- Suites are order-independent and self-healing (429-aware retries,
  TOTP-window retries). `scripts/reset-locks.ts` clears account lockouts.
- The runner restarts `next dev` per suite: the in-memory login rate limiter
  is intentionally persistent per process.
- Suites that create rows clean up after themselves (or are idempotent on
  rerun) so the demo state stays stable.

## Adding a suite

1. Create `scripts/<name>-test.mjs` (node, no deps) following the
   `check("name", cond, extra)` + exit-code pattern.
2. If it needs fixtures, seed them idempotently and add the seed to the
   runner's `case` block.
3. Add the suite to `SUITES` in `scripts/run-api-tests.sh` and to
   `docs/TESTING.md`.
