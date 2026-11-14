# Adwa Cyber — Pilot Onboarding Runbook

Step-by-step procedure for onboarding a pilot customer organisation
(roadmap §4.3: pilots sign in Months 4–5 and onboard the moment
acceptance tests pass). Target duration: 60–90 minutes to first scan.

## Pre-onboarding checklist (before the kickoff call)

- [ ] Platform deployed and healthy: `curl -s https://<host>/api/health`
      returns `{"status":"ok"}` (also verify the uptime monitor is green).
- [ ] Fresh backup taken: `sh scripts/backup-db.sh` (and the weekly
      restore drill is current — `bash scripts/restore-drill.sh`).
- [ ] Customer questionnaire returned: org name (EN/አማርኛ), sector,
      compliance owner contact, expected number of users.
- [ ] Framework scope agreed: PD-1321 only, or PD-1321 + NBE-IT
      (banking directive SBB/83/2022).
- [ ] SMTP configured (`.env` `SMTP_HOST`…) — password-reset emails
      must reach real users; verify with a test send.

## Step 1 — Create the organisation (5 min, SUPER_ADMIN)

1. Sign in as platform admin → **Organizations** → create.
   Name (both languages), sector, status `onboarding`.
2. Record the organisation ID in the pilot tracker.

## Step 2 — Provision the first users (10 min)

1. **Team** → create the ORG_ADMIN (the customer's security lead).
   Their temporary password follows the policy (10+ chars, letters +
   numbers, not common); deliver it over a channel OTHER than email
   (phone or in person) — email is for the reset flow only.
2. Instruct the admin to sign in, set their own password via
   "Forgot password?", and enable MFA (Settings → authenticator).
   **Every pilot admin must have MFA before going live.**
3. The ORG_ADMIN then creates COMPLIANCE_OFFICER / AUDITOR / VIEWER
   accounts for their staff (principle of least privilege).

## Step 3 — Verify tenant isolation (5 min)

With the customer's admin signed in, confirm:
- [ ] Organizations list shows ONLY their organisation.
- [ ] The audit view shows only their events.
- [ ] (Platform side: the automated tenant-isolation suite is green in CI.)

## Step 4 — Upload the evidence baseline (20 min, customer-driven)

The compliance officer uploads under **Evidence**:
- data-protection policy, DPO appointment, processing register,
  consent forms/templates, breach-response procedure, vendor/DPAs,
  security certs (ISO 27001, PCI-DSS if held).

Verify each shows checksum OK. Tag the obvious links; let AUTO_TAG
suggest the rest and confirm.

## Step 5 — First compliance scan (30 min, the demonstration)

1. **Scan** → new scan → their organisation → PD-1321.
2. Walk the questionnaire together — this IS the gap-analysis meeting.
   Unknown answers are honestly "No"/"Unknown"; the report must reflect
   reality, not aspiration.
3. Submit and review the results with the customer:
   - overall + per-pillar scores,
   - the risk-ranked findings list,
   - the remediation plan with SLA deadlines.
4. Export the executive report PDF — deliver it same day.

## Step 6 — Agree the operating rhythm (10 min)

- Weekly: the officer works findings; overdue SLA banner reviewed.
- Monthly: steering-committee review (score trend vs baseline scan,
  new findings vs remediated).
- Per pilot contract: RQ1 metrics — audit duration tracked for the
  pre/post comparison (>70% reduction target vs the manual baseline).

## Step 7 — Close onboarding

- [ ] Organisation status → `active` (Organizations view).
- [ ] Pilot tracker updated: org ID, users, scan IDs, baseline score.
- [ ] Customer informed of the support channel and the incident
      reporting path (`docs/INCIDENT_RUNBOOK.md`).

## Common first-week issues

| Symptom | Cause | Fix |
|---|---|---|
| Reset email never arrives | SMTP unset (console transport logs the link to the server log) | Configure `SMTP_*` in `.env`; check spam; resend |
| User locked out | 5 failed attempts | Wait 15 min, or admin runs `bun scripts/reset-locks.ts` |
| "AUTH_SECRET is not configured" on login | Deployment env missing | Set `AUTH_SECRET` in the host env / `.env`, restart |
| Score lower than expected | Honest "No" answers — expected on scan 1 | That is the product working; track the trend, not scan 1 |
