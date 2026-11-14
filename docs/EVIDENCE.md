# Evidence Vault (Document Store) — Module Reference

Phase 2 module of the Adwa Cyber compliance platform: the tenant-scoped
evidence library that backs compliance answers with verifiable artefacts
(Proclamation No. 1321/2024 · NBE SBB/83/2022 · MFI/33/2022).

## What is implemented

| Capability | Details |
|---|---|
| **Upload** | Drag & drop or browse; real progress bar (XHR); auto-title from filename; categories, description and rule-reference tags |
| **Type safety** | Strict allow-list — extension AND declared MIME AND magic-byte sniffing must agree: PDF, PNG, JPG, DOCX, XLSX, CSV, TXT, ZIP; hard 10 MB cap; empty files rejected |
| **Integrity evidence** | SHA-256 recorded at upload; recomputed on **every download** — mismatch blocks the download, raises `EVIDENCE_INTEGRITY_WARNING` in the audit trail (tamper evidence, NBE SBB/83/2022 record integrity) |
| **Storage isolation** | Files live outside the web root in `.data/evidence/<orgId>/<server-generated-hex>.<ext>` (mode 600). Original filenames are display metadata only — path traversal is structurally impossible |
| **Versioning** | "Upload new version" supersedes the previous record (`SUPERSEDED`), chains anchor on `rootId`; per-document version history with per-version download |
| **Lifecycle** | Active → Archived → Restored; soft delete hides a document while the file and audit trail are retained for forensic review |
| **Tenant isolation** | Every query and download is scoped by `organizationId`; auditors/viewers get read-only access; officers can curate their own uploads; org admins manage all tenant documents |
| **Audit trail** | `EVIDENCE_UPLOADED · DOWNLOAD · UPDATED · ARCHIVED · RESTORED · DELETED · REJECTED · INTEGRITY_WARNING` with IP + user agent + checksum prefix |
| **Preview** | Images and PDFs open inline (`?inline=1`); downloads served with `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `X-Checksum-Sha256` |

## Roles & permissions (`src/lib/auth/rbac.ts`)

| Permission | SUPER_ADMIN | ORG_ADMIN | OFFICER | AUDITOR | VIEWER |
|---|---|---|---|---|---|
| evidence:view-org / view-all | all | org | org | org | org |
| evidence:upload | ✓ | ✓ | ✓ | – | – |
| evidence:manage-org / manage-all | all | org | own uploads (metadata + archive) | – | – |

- **AUDITOR / VIEWER** see the library and can download (read-only badge, no upload UI).
- **COMPLIANCE_OFFICER** uploads new documents and new versions; may edit metadata and archive **their own** uploads.
- **ORG_ADMIN** additionally archives/restores/deletes any document in the tenant.
- **SUPER_ADMIN** works across tenants: org column, org filter, and an organization selector in the upload dialog.

## API surface

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/evidence` | GET | List + roll-up stats. Query: `search`, `category`, `status` (ACTIVE \| ARCHIVED \| SUPERSEDED \| ALL), `org` (super admin), `rootId` (version chain) |
| `/api/evidence/upload` | POST | Multipart: `file`, `title`, `category`, `description?`, `tags?`, `organizationId?` (super admin), `replaceId?` (new version) |
| `/api/evidence/[id]` | PATCH | Metadata edit (`title`, `description`, `category`, `tags`) or lifecycle `action: archive \| restore` |
| `/api/evidence/[id]` | DELETE | Soft delete (manage permission; file retained for forensics) |
| `/api/evidence/[id]/download` | GET | Streams the file. Integrity gate + download counter + audit. `?inline=1` for preview |

Error codes: `FILE_REJECTED` (type/size/magic-byte), `VALIDATION`,
`BAD_REQUEST` (multipart body expected), `ORG_REQUIRED` (super admin must
pick a target organization), `ORG_NOT_FOUND`→`VALIDATION` (unknown
organization id), `CROSS_TENANT` (replaceId from another organization),
`CHAIN_ACTIVE` (only the active version can be replaced / restore blocked —
newer version active), `INTEGRITY_FAILED` (checksum mismatch — download
blocked), `FILE_MISSING`.

Storage root: `DATA_DIR/evidence/<orgId>/` when `DATA_DIR` is set (the
compose stack mounts a named volume at `/data`), else `./.data/evidence/`
(see `src/lib/evidence.ts → dataRoot()`).

## Data model (`prisma/schema.prisma` → `EvidenceDocument`)

`title · description · category · tags · fileName · storedName (unique) ·
mimeType · fileSize · checksum · status · version · rootId · downloads ·
lastVerifiedAt · organizationId · uploadedById`

Categories: `POLICY · AUDIT_REPORT · CERTIFICATE · TRAINING_RECORD ·
INCIDENT_LOG · ASSESSMENT · CONFIG_EXPORT · OTHER` (bilingual labels).

## Demo artefacts (seeded)

`bun scripts/seed-evidence.ts` (idempotent) creates 10 realistic artefacts for
CBE and Wasasa MFI — policies, training CSV, a version chain (ICT Risk
Appetite Statement v1.0 → v2.0), an archived firewall export, a real PDF
certificate, the branch access-review log and the disaster-recovery plan —
each with an on-disk file and a recorded SHA-256.

`bun scripts/seed-evidence.ts --heal` restores artefact files that were lost
on disk while their DB rows survived (e.g. after a sandbox recycle): the
deterministic content is rewritten and `fileSize`/`checksum` refreshed, so
the integrity gate stays self-consistent.
