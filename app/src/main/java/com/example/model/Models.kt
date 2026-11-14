package com.example.model

enum class AppRole(val shortLabel: String, val fullLabel: String) {
    SUPER_ADMIN("SUPER ADMIN", "Platform Super Admin"),
    ORG_ADMIN("ORG ADMIN", "Organization Admin"),
    COMPLIANCE_OFFICER("OFFICER", "Compliance Officer"),
    AUDITOR("AUDITOR", "Auditor (read-only)"),
    VIEWER("VIEWER", "Viewer"),
}

enum class Permission {
    ORGS_VIEW_ALL,
    ORGS_MANAGE,
    ORG_VIEW_OWN,
    ORG_EDIT_OWN,
    USERS_VIEW_ORG,
    USERS_MANAGE_ORG,
    USERS_VIEW_ALL,
    USERS_MANAGE_ALL,
    CATALOGUE_VIEW,
    SCANS_VIEW_ORG,
    SCANS_VIEW_ALL,
    SCANS_RUN_ORG,
    SCANS_RUN_ANY_ORG,
    FINDINGS_VIEW_ORG,
    FINDINGS_VIEW_ALL,
    FINDINGS_MANAGE_ORG,
    FINDINGS_MANAGE_ALL,
    AUDIT_VIEW_ORG,
    AUDIT_VIEW_ALL,
    EVIDENCE_VIEW_ORG,
    EVIDENCE_VIEW_ALL,
    EVIDENCE_UPLOAD,
    EVIDENCE_MANAGE_ORG,
    EVIDENCE_MANAGE_ALL,
    SOC_VIEW_ORG,
    SOC_VIEW_ALL,
    SOC_MANAGE_ORG,
    SOC_MANAGE_ALL,
    THREATS_VIEW,
    THREATS_CONTRIBUTE,
    THREATS_SYNC,
    AWARENESS_MANAGE,
    GOVERNANCE_VIEW,
    GOVERNANCE_MANAGE,
    ACFS_MANAGE,
    CII_MANAGE,
    CII_VIEW_OWN,
}

enum class Lang { EN, AM }

enum class Answer(val score: Double) {
    YES(100.0),
    PARTIAL(50.0),
    NO(0.0),
    NA(0.0);

    companion object {
        fun fromKey(k: String?): Answer = when (k?.lowercase()) {
            "yes" -> YES
            "partial" -> PARTIAL
            "no" -> NO
            else -> NA
        }
    }

    fun key(): String = name.lowercase()
}

data class PillarWeight(
    val name: String,
    val nameAm: String,
    val weight: Double,
)

data class Framework(
    val id: String,
    val code: String,
    val name: String,
    val nameAm: String,
    val issuer: String,
    val instrument: String,
    val instrumentNo: String,
    val descriptionEn: String,
    val descriptionAm: String,
    val pillars: List<PillarWeight>,
    val sectorScope: String,
)

data class Rule(
    val id: String,
    val code: String,
    val frameworkCode: String,
    val sortOrder: Int,
    val article: String,
    val articleSubject: String,
    val titleEn: String,
    val titleAm: String,
    val statementEn: String,
    val statementAm: String,
    val questionEn: String,
    val questionAm: String,
    val checkLogic: String,
    val remediationEn: String,
    val remediationAm: String,
    val pillar: String,
    val severity: String, // critical | high | medium
    val priority: String, // P0 | P1
    val penaltyRef: String,
    val weight: Int, // 3 critical | 2 high | 1 medium
)

data class Organization(
    val id: String,
    val name: String,
    val nameAm: String,
    val slug: String,
    val sector: String,
    val tier: String,
    val contactEmail: String?,
    val status: String,
    val notes: String?,
)

data class ScanResult(
    val id: String,
    val scanId: String,
    val ruleCode: String,
    val answer: Answer,
    val answeredAtEpochMs: Long?,
    val evidenceNote: String?,
    val score: Double,
    val riskRank: Double,
    val isFinding: Boolean,
    val predictive: Boolean,
    val findingEn: String?,
    val findingAm: String?,
    val status: String, // open | in_progress | remediated | waived
    val waiverNote: String? = null,
    val dueAtEpochMs: Long? = null,
    val remediatedAtEpochMs: Long? = null,
)

data class ComplianceScan(
    val id: String,
    val organizationId: String,
    val frameworkCode: String,
    val name: String,
    val status: String, // in_progress | complete
    val auditorName: String,
    val overallScore: Double?,
    val pillarScores: Map<String, Double>,
    val findingsCount: Int,
    val criticalFindings: Int,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val durationMinutes: Double?,
    val results: List<ScanResult>,
)

enum class EvidenceCategory(val labelEn: String, val labelAm: String, val freshnessMonths: Int) {
    POLICY("Policy / procedure", "ፖሊሲ / አሠራር", 24),
    AUDIT_REPORT("Audit report", "የኦዲት ሪፖርት", 12),
    CERTIFICATE("Certificate", "ማረጋገጫ", 24),
    TRAINING_RECORD("Training record", "የስልጠና መዝገብ", 12),
    INCIDENT_LOG("Incident log", "የክስተት መዝገብ", 3),
    ASSESSMENT("Risk assessment", "የስጋት ግምገማ", 12),
    CONFIG_EXPORT("Technical config", "ቴክኒካል ማስተካከያ", 3),
    OTHER("Other", "ሌላ", 24),
}

data class EvidenceDocument(
    val id: String,
    val title: String,
    val description: String,
    val category: EvidenceCategory,
    val tags: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val checksumSha256: String,
    val status: String, // ACTIVE | ARCHIVED | SUPERSEDED | DELETED
    val version: Int,
    val rootId: String?,
    val downloads: Int,
    val lastVerifiedAtEpochMs: Long?,
    val organizationId: String,
    val uploadedByName: String,
    val createdAtEpochMs: Long,
    val contentPreview: String,
)

data class EvidenceLink(
    val id: String,
    val ruleCode: String,
    val evidenceId: String,
    val linkType: String, // SATISFIES | SUPPORTS
    val source: String, // MANUAL | AUTO_TAG
)

data class UserAccount(
    val id: String,
    val email: String,
    val password: String,
    val name: String,
    val role: AppRole,
    val organizationId: String?,
    val status: String, // ACTIVE | DISABLED
    val mfaEnabled: Boolean,
    val totpSecretBase32: String?,
    val backupCodes: List<String>,
    val lastLoginAtEpochMs: Long?,
)

data class AuthEvent(
    val id: String,
    val email: String,
    val organizationId: String?,
    val type: String,
    val detail: String,
    val createdAtEpochMs: Long,
)

// ── Phase 2: SOC / CERT-as-a-Service ────────────────────────────────────────

enum class IncidentStatus(val labelEn: String, val labelAm: String, val eventType: String) {
    DETECTED("Detected", "ተገኝቷል", "DETECTED"),
    TRIAGED("Triaged", "ተለይቷል", "TRIAGE"),
    CONTAINED("Contained", "ተገብሯል", "CONTAIN"),
    ERADICATED("Eradicated", "ተሟጠሯል", "ERADICATE"),
    RECOVERED("Recovered", "ተመልሷል", "RECOVER"),
    CLOSED("Closed", "ተዘግቷል", "CLOSE");

    fun next(): IncidentStatus? = entries.getOrNull(ordinal + 1)
}

data class IncidentEvent(
    val id: String,
    val type: String,
    val note: String?,
    val actorName: String?,
    val createdAtEpochMs: Long,
)

data class SecurityIncident(
    val id: String,
    val organizationId: String,
    val ref: String,
    val title: String,
    val titleAm: String,
    val description: String,
    val category: String,
    val severity: String,
    val status: IncidentStatus,
    val source: String,
    val detectionMethod: String,
    val priorityScore: Double,
    val priorityLabel: String, // IMMEDIATE | HIGH | ROUTINE
    val detectedAtEpochMs: Long,
    val notifiedAtEpochMs: Long?,
    val triagedAtEpochMs: Long?,
    val containedAtEpochMs: Long?,
    val eradicatedAtEpochMs: Long?,
    val recoveredAtEpochMs: Long?,
    val closedAtEpochMs: Long?,
    val reportedToEthiocertAtEpochMs: Long?,
    val matchedIndicatorIds: List<String>,
    val events: List<IncidentEvent>,
)

data class ThreatIndicator(
    val id: String,
    val type: String,
    val value: String,
    val titleEn: String,
    val titleAm: String,
    val descriptionEn: String?,
    val descriptionAm: String?,
    val category: String,
    val severity: String,
    val tlp: String,
    val source: String,
    val sourceRef: String?,
    val confidence: Int,
    val sensitivity: Int,
    val sharing: String, // HUB | P2P
    val sharingRationale: String,
    val organizationId: String?,
    val sharedWithOrgIds: List<String>,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    val sightings: Int,
    val isActive: Boolean,
)

data class SyncRun(
    val id: String,
    val direction: String, // INGEST | REPORT
    val status: String,
    val source: String, // FIXTURE | LIVE
    val ingested: Int,
    val updated: Int,
    val reported: Int,
    val payloadHash: String?,
    val receiptId: String?,
    val organizationId: String?,
    val detail: String,
    val createdAtEpochMs: Long,
)

// ── Phase 3: Track B Awareness Platform ─────────────────────────────────────

data class AwarenessSchool(
    val id: String,
    val name: String,
    val nameAm: String,
    val subCity: String,
    val contactPerson: String?,
)

data class AwarenessClass(
    val id: String,
    val schoolId: String,
    val name: String,
    val grade: Int,
    val groupType: String, // TREATMENT | CONTROL
    val joinCode: String,
    val teacherName: String?,
)

data class AwarenessQuestion(
    val id: String,
    val moduleCode: String,
    val sortOrder: Int,
    val promptEn: String,
    val promptAm: String,
    val optionsEn: List<String>,
    val optionsAm: List<String>,
    val answerIdx: Int,
    val explainEn: String,
    val explainAm: String,
)

data class AwarenessModule(
    val id: String,
    val code: String,
    val sortOrder: Int,
    val topic: String,
    val titleEn: String,
    val titleAm: String,
    val summaryEn: String,
    val summaryAm: String,
    val contentEn: String,
    val contentAm: String,
    val durationMin: Int,
    val gradeBand: String,
    val audience: String, // KIDS | TEENS | PRO | AWARE
    val isActive: Boolean,
    val questions: List<AwarenessQuestion>,
)

data class AwarenessLearner(
    val id: String,
    val classId: String,
    val code: String,
    val grade: Int,
    val consent: String, // PENDING | GRANTED | DENIED
    val consentRef: String?,
    val preScore: Double?,
    val preCorrect: Int?,
    val preTotal: Int?,
    val postScore: Double?,
    val postCorrect: Int?,
    val postTotal: Int?,
    val completedModules: Map<String, Double>, // moduleCode -> quizScore
)

data class AwarenessToolPing(
    val toolId: String,
    val lang: String,
    val createdAtEpochMs: Long,
)

// ── Phase 4 & 5: Track C Gated Expansion ────────────────────────────────────

data class GateEvidence(
    val id: String,
    val fileName: String,
    val description: String?,
    val checksumSha256: String,
    val fileSize: Long,
    val uploadedByName: String,
    val createdAtEpochMs: Long,
)

data class GateEvent(
    val id: String,
    val kind: String, // STATUS_CHANGE | EVIDENCE_ATTACHED | CRITERION_SEEDED
    val fromStatus: String?,
    val toStatus: String?,
    val note: String?,
    val actorName: String?,
    val createdAtEpochMs: Long,
)

data class ExpansionGate(
    val id: String,
    val phase: String, // P4_ACFS | P5_CII
    val code: String,
    val titleEn: String,
    val titleAm: String,
    val detailEn: String,
    val detailAm: String,
    val category: String,
    val status: String, // NOT_STARTED | IN_PROGRESS | SATISFIED | BLOCKED
    val blocking: Boolean,
    val notes: String?,
    val evidence: List<GateEvidence>,
    val events: List<GateEvent>,
)

data class AcfsGateItem(
    val id: String,
    val domain: String,
    val code: String,
    val titleEn: String,
    val titleAm: String,
    val description: String,
    val status: String, // NOT_STARTED | IN_PROGRESS | EVIDENCE_SUBMITTED | SATISFIED | BLOCKED
    val owner: String?,
    val notes: String?,
    val evidenceRef: String?,
    val sortOrder: Int,
)

data class AcfsGateDecision(
    val id: String,
    val decision: String, // REMAINED_LOCKED | OPENED | RE_LOCKED
    val rationale: String,
    val decidedBy: String,
    val createdAtEpochMs: Long,
)

data class CiiMou(
    val id: String,
    val version: Int,
    val status: String, // DRAFTING | LEGAL_REVIEW | SIGNED | ACTIVE | EXPIRED | TERMINATED
    val scope: List<String>,
    val dataTerms: String?,
    val signedAtEpochMs: Long?,
    val expiresAtEpochMs: Long?,
)

data class CiiAuthorization(
    val id: String,
    val reference: String,
    val status: String, // PENDING | UNDER_REVIEW | GRANTED | ACTIVE | SUSPENDED | REVOKED | EXPIRED
    val issuedAtEpochMs: Long?,
    val expiresAtEpochMs: Long?,
    val scope: String?,
)

data class CiiSystem(
    val id: String,
    val name: String,
    val nameAm: String?,
    val category: String,
    val criticality: String,
    val readiness: String, // REGISTERED -> INVENTORY_MAPPED -> AGENT_READY -> MONITORING_AUTHORIZED
)

data class CiiOperator(
    val id: String,
    val code: String,
    val name: String,
    val nameAm: String,
    val sector: String,
    val tier: String,
    val orgId: String?,
    val contactEmail: String?,
    val mous: List<CiiMou>,
    val authorizations: List<CiiAuthorization>,
    val systems: List<CiiSystem>,
)
