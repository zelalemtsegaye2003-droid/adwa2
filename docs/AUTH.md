# Authentication & Multi-Tenant RBAC — Module Reference

Phase 1 identity layer of the Adwa Cyber compliance platform
(Proclamation No. 1321/2024 · NCSP 2024 · Digital Ethiopia 2030).

## What is implemented

| Capability | Details |
|---|---|
| **Password auth** | scrypt hashing (N=16384, per-user salt, timing-safe compare), 10+ char policy |
| **JWT sessions** | HS256 access token (15 min) in httpOnly cookie + opaque refresh token (rotating, hashed at rest) |
| **MFA (OTP)** | RFC 6238 TOTP (Google Authenticator / Authy / Aegis / 1Password), QR enrollment, 8 single-use backup codes (SHA-256 hashed), secrets encrypted at rest with AES-256-GCM |
| **MFA precedence** | Accounts with platform MFA cannot sign in via Google — the native TOTP factor must be satisfied |
| **Sign in with Google** | OAuth 2.0 / OIDC authorization-code flow: CSRF `state` cookie, ID token verified against Google's JWKS (issuer + audience + signature), `email_verified` required |
| **Provisioning policy** | Google identities are auto-provisioned **only** for e-mail domains of known tenants (matched against `Organization.contactEmail`) and only as `VIEWER`; existing local accounts get their `googleId` linked |
| **Captcha** | Built-in distorted SVG captcha (unambiguous charset, single-use server-side challenges) with pluggable hCaptcha / reCAPTCHA when keys are configured |
| **Rate limiting & lockout** | Per-IP and per-account login limits, 5 failed passwords → 15 min lockout, limits on OAuth endpoints |
| **Audit trail** | Every auth event (success/failure, MFA enable/disable, lockout, token reuse, user CRUD, Google logins) persisted with IP + user agent |
| **Multi-tenant RBAC** | 5 roles — `SUPER_ADMIN` (platform), `ORG_ADMIN`, `COMPLIANCE_OFFICER`, `AUDITOR`, `VIEWER` — with permission matrix; every API scopes queries by `organizationId` |

## Roles & permissions (`src/lib/auth/rbac.ts`)

| Permission | SUPER_ADMIN | ORG_ADMIN | OFFICER | AUDITOR | VIEWER |
|---|---|---|---|---|---|
| catalogue:view | ✓ | ✓ | ✓ | ✓ | ✓ |
| org:view-own / orgs:view-all | all | own | own | own | own |
| orgs:manage / org:edit-own | all | own | – | – | – |
| scans:view-org / view-all | all | org | org | org | org |
| scans:run-org / run-any-org | any org | org | org | – | – |
| findings:view-org / view-all | all | org | org | org | org |
| findings:manage-org / manage-all | all | org | org | – | – |
| users:view-org / view-all | all | org | – | – | – |
| users:manage-org / manage-all | all | org | – | – | – |
| audit:view-org / view-all | all | org | – | org | – |

Tenant note: every non-SUPER_ADMIN user belongs to exactly one organization;
SUPER_ADMIN is platform staff with cross-tenant rights.

## Environment (`.env`)

See `.env.example`. Required: `AUTH_SECRET` (64+ chars — signs JWTs and derives the
AES-256-GCM key for TOTP secrets; rotating it invalidates sessions and MFA seeds —
re-run `bun scripts/fix-mfa-secret.ts` afterwards).

Optional: `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` for real Google sign-in
(callback URL `{origin}/api/auth/google/callback`), `RECAPTCHA_*` / `HCAPTCHA_*`
for external captcha providers.

### Development mock provider

`mini-services/mock-google` (port 3010) simulates Google's OAuth/OIDC endpoints so
the full code path can be exercised without real credentials. Enable via the
`GOOGLE_AUTH_BASE` / `GOOGLE_TOKEN_URL` / `GOOGLE_JWKS_URL` overrides in `.env`
(see current `.env`), then start it with `cd mini-services/mock-google && bun run dev`.
The login screen badges the Google button **DEMO PROVIDER** while the mock is active.
Remove the overrides + set real credentials for production.

## Demo accounts (after `bun scripts/seed-users.ts`)

| Account | Role | Password | MFA |
|---|---|---|---|
| admin@adwacyber.et | SUPER_ADMIN | `Adwa#Admin2026!` | off |
| admin@cbe.com.et | ORG_ADMIN (CBE) | `Adwa#Demo2026!` | off |
| officer@cbe.com.et | COMPLIANCE_OFFICER (CBE) | `Adwa#Demo2026!` | **TOTP on** |
| auditor@dashen.com.et | AUDITOR (Dashen) | `Adwa#Demo2026!` | off |
| viewer@ethiotelecom.et | VIEWER (Ethio Telecom) | `Adwa#Demo2026!` | off |

Officer TOTP seed (Google Authenticator): `UPRWOJXX2JVEYGDRDDXSWXDGCDIJLNLD`
→ `otpauth://totp/Adwa%20Cyber:officer@cbe.com.et?secret=UPRWOJXX2JVEYGDRDDXSWXDGCDIJLNLD&issuer=Adwa%20Cyber&algorithm=SHA1&digits=6&period=30`

## API surface (`src/app/api/auth/`)

```
POST /api/auth/register        tenant self-registration (org + first ORG_ADMIN)
GET  /api/auth/captcha         issue SVG captcha challenge
POST /api/auth/login           step 1: password (+captcha) → session or mfaToken
POST /api/auth/mfa/verify      step 2: TOTP / backup code → session
POST /api/auth/mfa/setup       generate TOTP secret + QR (auth required)
POST /api/auth/mfa/enable      confirm code → enable, issue backup codes
POST /api/auth/mfa/disable     password + code → disable, revoke all sessions
POST /api/auth/password        logged-in change: currentPassword + newPassword
                                → re-auth + policy + SAME_PASSWORD checks, then
                                hash update + sessionsRevokedAt + refresh wipe
                                (Google-only accounts may SET an initial password)
POST /api/auth/refresh         rotate refresh token (reuse detection)
POST /api/auth/logout          revoke session
GET  /api/auth/me              identity + tenant + permissions
GET  /api/auth/users           list tenant users (RBAC-scoped)
POST /api/auth/users           create tenant user
PATCH /api/auth/users/[id]     change role / status (RBAC-scoped)
GET  /api/auth/audit           auth event log (RBAC-scoped)
GET  /api/auth/google          start OAuth flow
GET  /api/auth/google/callback finish OAuth flow
GET  /api/auth/config          public capabilities (providers, captcha, policy)
POST /api/auth/password-reset/request   enumeration-safe reset link (30-min single-use token)
POST /api/auth/password-reset/confirm   redeem token → new password + revoke sessions
POST /api/auth/verify-email/request      send verification token (24 h)
POST /api/auth/verify-email/confirm      redeem token → emailVerifiedAt
```

## Tests

```bash
bun scripts/test-owasp.ts       # 67 checks — OWASP Top 10 (2021) A01–A10
bun scripts/test-auth.ts        # 54 checks — captcha, login, MFA, RBAC matrix,
                                # tenant isolation, rotation/reuse, lockout, logout
bun scripts/test-google.ts      # 15 checks — full OAuth dance via the mock provider
bun scripts/test-compliance.ts  # 44 checks — 47-rule catalogue integrity, scan
                                # lifecycle, engine maths, findings, tenant isolation
bun scripts/test-password-reset.ts  # 38 checks — reset lifecycle, enumeration
                                # resistance, single-use tokens, session revocation,
                                # email verification, logged-in change (WS-7),
                                # rate limiting, audit trail
```

All four run against the dev server. They are **order-independent and
self-healing** (429-aware retries, TOTP-window retries), but for a clean
single-pass run start from a fresh server (`pkill -f "next dev"` then
`bun run dev`) — the in-memory rate limiter keeps its buckets across hot
env reloads, which is by design. `scripts/reset-locks.ts` clears
`failedAttempts`/`lockedUntil` in the database between runs.

## OWASP Top 10 (2021) — hardening & verification

Verified by `scripts/test-owasp.ts` (67 checks). Hardening added during the pass:

| Category | Controls |
|---|---|
| **A01** Broken Access Control | Every API route behind `requireAuth` + RBAC permission; tenant scoping on every query; cross-origin state-changing requests rejected (403); tampered/forged/`alg:none`/expired JWTs all 401; role changes + `sessionsRevokedAt` kill live access tokens (exact-millisecond `iatMs` comparison — tokens issued after a revocation survive even within the same second); route ids validated against `^[a-zA-Z0-9_-]{10,}$` |
| **A02** Cryptographic Failures | scrypt (N=16384) passwords; refresh tokens stored as sha256 only; TOTP secrets AES-256-GCM encrypted at rest; cookies `httpOnly` + `SameSite=Lax`, refresh cookie scoped to `/api/auth`; JWT carries identity claims only; API responses never include `passwordHash`/`mfaSecret`/`backupCodes` |
| **A03** Injection | Prisma parameterized queries (zero raw SQL); all request fields JSON-type-guarded (type confusion → clean 4xx, never 500); React auto-escaping, no `dangerouslySetInnerHTML` outside shadcn chart theming; prototype-pollution keys inert |
| **A04** Insecure Design | Captcha on login + register; per-IP/per-account/per-endpoint rate limits; account lockout (5 fails → 15 min); length caps on every input (254 email / 200 password / 120 org) — blocks scrypt-CPU DoS (1 MB password rejected in ~12 ms); common-password deny-list; explicit field picking (mass-assignment-proof); MFA precedence over Google |
| **A05** Security Misconfiguration | CSP, `X-Frame-Options`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`, HSTS, COOP/CORP via `next.config.ts`; `poweredByHeader: false`; structured JSON errors only — no stack traces |
| **A06** Vulnerable Components | `bun audit`: **0 critical** (removed unused `next-auth` + `@reactuses/core`; Next.js 16.1.1 → 16.4.5). Remaining advisories are dev/build-tooling only |
| **A07** Ident. & Auth Failures | Generic error message + dummy-scrypt timing equalization for unknown accounts (measured ratio ≈ 1.0); TOTP **replay protection** (consumed step counters persist — same code never validates twice); per-ACCOUNT MFA attempt limit (10/5 min → 429) on top of per-IP; token-type confusion impossible (mfa token ≠ access token both ways) |
| **A08** Software & Data Integrity | HS256 pinned via symmetric key (alg confusion impossible); signature + issuer + audience verified; refresh tokens opaque 256-bit CSPRNG (not JWTs) |
| **A09** Logging & Monitoring | All auth events audited with IP + user agent (LOGIN_*, MFA_*, ACCOUNT_LOCKED, TOKEN_REUSE_DETECTED, ACCESS_DENIED, GOOGLE_LOGIN, USER_*); probes confirmed credentials never appear in the audit trail |
| **A10** SSRF | No server-side fetch of user-controlled URLs (statically verified — all fetch targets are constants/env); OAuth endpoints fixed via environment |

`frame-ancestors` allows `https://*.space-z.ai` in addition to `'self'`
because the sandbox preview renders the app in a frame.

## Key source files

```
src/lib/auth/crypto.ts       scrypt, AES-256-GCM, SHA-256, CSPRNG tokens
src/lib/auth/jwt.ts          access/MFA token sign+verify (jose, HS256, exact-ms iatMs claim)
src/lib/auth/session.ts      cookie handling, refresh rotation + reuse detection
src/lib/auth/totp.ts         RFC 6238 TOTP + backup codes
src/lib/auth/captcha.ts      built-in SVG captcha + hCaptcha/reCAPTCHA adapters
src/lib/auth/google.ts       OAuth/OIDC helpers, endpoint overrides
src/lib/auth/rbac.ts         role → permission matrix
src/lib/auth/guard.ts        requireAuth / requirePermission middleware
src/lib/auth/rate-limit.ts   in-memory sliding window limiter
src/lib/auth/audit.ts        AuthEvent persistence
src/lib/auth/passwords.ts    common-password deny list (A04)
```

Security-relevant config lives in `next.config.ts` (CSP + hardening headers).
