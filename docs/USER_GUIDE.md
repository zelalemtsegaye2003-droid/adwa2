# Adwa Cyber — User Guide

How compliance officers, auditors, and administrators use the platform day
to day. For installation and operations see `docs/OPS.md`; for the identity
module reference see `docs/AUTH.md`.

## 1. Signing in

1. Open the platform URL. The sign-in page shows a CAPTCHA challenge.
2. Enter your email, password, and the CAPTCHA characters (case does not
   matter — click the refresh icon for a new challenge if it is hard to read).
3. If your account has multi-factor authentication (MFA), enter the 6-digit
   code from your authenticator app (Google Authenticator, Authy, Aegis).
4. **Forgot your password?** Click "Forgot password?" under the password
   field. A single-use reset link (valid 30 minutes) is emailed to you.
   Opening it signs out every active session and lets you set a new
   password. If your deployment has no mail server configured, contact
   your platform administrator.
5. The interface is bilingual — use the language toggle (EN / አማርኛ) any
   time. Dark mode is available from the account menu.

Accounts lock for 15 minutes after 5 failed attempts (protection against
password guessing). Locked out? Wait or ask an administrator to unlock.

## 2. Roles — who can do what

| Role | Typical user | Can |
|---|---|---|
| SUPER_ADMIN | Platform owner | Everything, across all organisations |
| ORG_ADMIN | Client's security lead | Manage the org's users, run scans, see all org data |
| COMPLIANCE_OFFICER | Client's compliance staff | Run scans, work findings, upload evidence |
| AUDITOR | External auditor | Read-only: scans, findings, evidence, reports |
| VIEWER | Stakeholders | Read-only dashboards and reports |

Every query is scoped to your organisation — one tenant can never see
another's data, even by ID guessing (verified by automated tests).

## 3. Running a compliance scan (the core workflow)

1. Open **Scan** from the sidebar.
2. Pick the organisation (admins) and the framework:
   - **PD-1321** — Personal Data Protection Proclamation No. 1321/2024 (47 obligations)
   - **NBE-IT** — NBE IT & Cybersecurity Directive SBB/83/2022 (30 obligations)
3. Answer the questionnaire. Each question cites the exact legal article,
   in English and Amharic. Progress is saved automatically — you can close
   the tab and resume.
4. Submit. The engine scores the four pillars (Legal 35%, Technical 30%,
   Organisational 20%, Awareness 15% for PD-1321) and produces:
   - a weighted compliance score per pillar and overall,
   - risk-ranked findings (every "No" becomes a finding),
   - a prioritised remediation plan (by severity × pillar weight),
   - predicted violation exposure.

## 4. Working findings

Findings are the actionable output of a scan. For each one you can:

- change status: **open → in progress → remediated** (or **waived** with a
  justification — auditors see waivers flagged),
- attach evidence from the vault (see §5),
- watch the remediation SLA: critical 30 days, high 60, medium 90
  (Proclamation 1426/2026 practice). The dashboard banner lists overdue
  items and each finding shows on-time vs late closure.

## 5. Evidence vault

Evidence (policies, logs, certificates) is uploaded once and linked to
obligations across scans:

- every file gets a SHA-256 checksum recorded at upload; integrity is
  re-verified on every download (a tampered file is rejected),
- evidence has freshness policies — an old policy document goes STALE and
  stops satisfying the obligation until renewed,
- AUTO_TAG: the rules engine suggests evidence↔obligation links from
  document metadata; you confirm or reject.

## 6. Executive report

**Report** produces a bilingual (EN/አማርኛ) PDF for management: pillar
charts, findings by severity, SLA table, and a per-page authenticity
footer with a payload hash. It is the artefact to hand a board or a
regulator.

## 7. Team & audit

- **Team** (admins): invite users, assign roles, force-reset MFA, disable
  accounts.
- **Audit** (admins + auditors): every security-relevant event — logins,
  MFA changes, lockouts, evidence uploads, finding changes, gate
  decisions — with IP, user agent, and timestamp. Non-repudiable trail
  for ISO-style reviews.

## 8. Account self-service

- **Change MFA**: Settings → enable authenticator (scan QR, verify one
  code, save the 8 backup codes somewhere safe — each works once).
- **Password reset**: see §1.4 — self-service, no administrator needed.
- **Email verification**: new accounts receive a confirmation link
  (valid 24 h). Unverified accounts show a badge to administrators.

## Getting help

- Login problems after 5 failed attempts: wait 15 min or ask an admin.
- Suspicious activity in the audit log: report it to your platform
  administrator — see `docs/INCIDENT_RUNBOOK.md` for the response process.
