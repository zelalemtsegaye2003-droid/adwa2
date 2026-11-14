# Security Policy

## Reporting a vulnerability

Adwa Cyber processes compliance data for Ethiopian financial institutions —
we treat security reports with priority.

- **Email**: security@adwacyber.et (placeholder — replace before the public
  pilot; consider a dedicated address with PGP).
- Please include: affected component/route, reproduction steps, impact
  assessment, and any proof-of-concept.
- We will acknowledge within 72 hours and keep you informed of the fix.
- Please do not test against live pilot tenants; use your own deployment.

## Scope highlights (what we consider in scope)

- The Next.js application and its API routes (`src/app/api/**`)
- Authentication/session/MFA flows (`src/lib/auth/**`)
- The evidence vault (file upload allow-list, checksums, tenant isolation)
- Multi-tenant isolation across every query path

## Hardening already in place

- OWASP Top 10 (2021) verification: `scripts/test-owasp.ts` (67 checks)
- CSP / HSTS / X-Frame-Options / nosniff / Permissions-Policy headers
- Scrypt password hashing + breached-password deny list
- JWT (HS256) access cookies + rotating refresh tokens with reuse detection
- TOTP MFA with AES-256-GCM-encrypted secrets
- Single-use captcha challenges (crypto-secure randomness); third-party
  captcha supported for production (decision D4)
- Tenant isolation + RBAC enforced server-side on every route
- Audit trail with IP + user agent on every security-relevant event
- Evidence files: extension+MIME+magic-byte allow-list, random stored names
  outside the web root, SHA-256 verified on every download

## Known limitations (documented, not hidden)

- The in-memory rate limiter is single-instance (fine for pilot scale;
  requires a shared store for multi-replica deployments).
- The built-in SVG captcha is dev/pilot-only (glyphs are parseable by a
  determined bot — production must use hCaptcha/reCAPTCHA).
- `prisma db push` (no migrations history) means schema changes are not
  reviewable as SQL diffs — destructive changes fail loudly in the container
  entrypoint instead.
