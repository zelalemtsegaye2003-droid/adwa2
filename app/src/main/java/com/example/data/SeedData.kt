package com.example.data

import com.example.domain.ComplianceEngine
import com.example.domain.RulesEngine
import com.example.domain.SocEngine
import com.example.model.*

data class AdwaState(
    val organizations: List<Organization>,
    val users: List<UserAccount>,
    val authEvents: List<AuthEvent>,
    val scans: List<ComplianceScan>,
    val draftAnswers: Map<String, Map<String, Answer>>, // scanId -> (ruleCode -> Answer)
    val draftNotes: Map<String, Map<String, String>>,   // scanId -> (ruleCode -> Note)
    val evidenceDocs: List<EvidenceDocument>,
    val evidenceLinks: List<EvidenceLink>,
    val incidents: List<SecurityIncident>,
    val threatIndicators: List<ThreatIndicator>,
    val syncRuns: List<SyncRun>,
    val awarenessSchools: List<AwarenessSchool>,
    val awarenessClasses: List<AwarenessClass>,
    val awarenessLearners: List<AwarenessLearner>,
    val awarenessToolPings: List<AwarenessToolPing>,
    val expansionGates: List<ExpansionGate>,
    val acfsItems: List<AcfsGateItem>,
    val acfsDecisions: List<AcfsGateDecision>,
    val ciiOperators: List<CiiOperator>,
)

object SeedData {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MIN_MS = 60L * 1000L

    val FRAMEWORKS: List<Framework> = listOf(
        Framework(
            id = "fw_pd1321",
            code = "PD-1321",
            name = "Personal Data Protection Proclamation",
            nameAm = "የግል መረጃ ጥበቃ አዋጅ",
            issuer = "Ethiopian Communications Authority (ECA)",
            instrument = "Proclamation",
            instrumentNo = "1321/2024",
            descriptionEn = "47 enforceable data protection obligations spanning processing registers, consent, sensitive data, minors, cross-border sovereignty, security safeguards, and 72-hour breach reporting.",
            descriptionAm = "47 የግል መረጃ ጥበቃ ግዴታዎች — ምዝገባ፣ ፈቃድ፣ ስሜታዊ መረጃ፣ ህጻናት፣ የውሂብ ሉዓላዊነት እና የ72 ሰዓት የጥሰት ሪፖርት።",
            pillars = listOf(
                PillarWeight("Legal", "ሕጋዊ", 0.35),
                PillarWeight("Technical", "ቴክኒካል", 0.30),
                PillarWeight("Organisational", "ድርጅታዊ", 0.20),
                PillarWeight("Awareness", "ግንዛቤ", 0.15),
            ),
            sectorScope = "All public and private data controllers/processors in Ethiopia",
        ),
        Framework(
            id = "fw_nbe_it",
            code = "NBE-IT",
            name = "NBE IT Management & Critical Cyber Directive",
            nameAm = "የNBE የአይቲ አስተዳደር እና የሳይበር መመሪያ",
            issuer = "National Bank of Ethiopia (NBE) & INSA",
            instrument = "Directive / Proclamation",
            instrumentNo = "SBB/83/2022 & Procl. 1426/2026",
            descriptionEn = "30 banking & MFI technology governance, core-banking automation, IT risk management, INSA critical-infrastructure cyber controls, and NBE breach reporting rules.",
            descriptionAm = "30 የባንክና የማይክሮፋይናንስ የአይቲ አስተዳደር፣ የሥራ ቀጣይነት እና የሳይበር ደህንነት ግዴታዎች።",
            pillars = listOf(
                PillarWeight("Governance", "የአስተዳደር ሥርዓት", 0.20),
                PillarWeight("Automation", "ራስ-ሰርነት", 0.15),
                PillarWeight("Risk Management", "የስጋት አስተዳደር", 0.25),
                PillarWeight("Cyber Security", "የሳይበር ደህንነት", 0.25),
                PillarWeight("Continuity", "ቀጣይነት", 0.15),
            ),
            sectorScope = "Commercial banks, microfinance institutions & payment operators",
        ),
    )

    val ALL_RULES: List<Rule> = Pd1321RulesData.RULES + NbeRulesData.RULES

    fun rulesFor(frameworkCode: String): List<Rule> = ALL_RULES.filter { it.frameworkCode == frameworkCode }

    fun frameworkFor(frameworkCode: String): Framework? = FRAMEWORKS.find { it.code == frameworkCode }

    fun buildInitialState(nowMs: Long = System.currentTimeMillis()): AdwaState {
        val orgs = listOf(
            Organization("org_cbe", "Commercial Bank of Ethiopia", "የኢትዮጵያ ንግድ ባንክ", "cbe", "Banking", "Enterprise", "ciso@cbe.com.et", "Active", "Tier-1 state commercial bank — PD-1321 + NBE-IT pilot"),
            Organization("org_dashen", "Dashen Bank S.C.", "ዳሸን ባንክ አ.ማ.", "dashen", "Banking", "Enterprise", "compliance@dashenbank.et", "Active", "Private commercial bank — NBE SBB/83/2022 + Procl. 1426 pilot"),
            Organization("org_wasasa", "Wasasa Microfinance Institution", "ዋሳሳ ማይክሮፋይናንስ", "wasasa", "Microfinance", "Growth", "it@wasasa.et", "Active", "Oromia MFI — resource-constrained SME compliance pilot"),
            Organization("org_oromia", "Oromia Credit & Saving S.C.", "ኦሮሚያ ብድርና ቁጠባ", "oromia-cs", "Microfinance", "Growth", "security@ocssCo.et", "Active", "Regional MFI expanding core banking automation"),
            Organization("org_amhara", "Amhara Credit & Saving Institution", "አማራ ብድርና ቁጠባ", "acsi", "Microfinance", "Enterprise", "risk@acsi.org.et", "Active", "Large MFI undergoing NBE core automation review"),
            Organization("org_ethio", "Ethio Telecom", "ኢትዮ ቴሌኮም", "ethiotelecom", "Telecom", "Critical Infrastructure", "sec@ethiotelecom.et", "Active", "National telecom & telebirr mobile money operator"),
        )

        val users = listOf(
            UserAccount("usr_super", "admin@adwacyber.et", "Adwa#Admin2026!", "Selamawit Bekele", AppRole.SUPER_ADMIN, null, "ACTIVE", false, null, emptyList(), nowMs - 2 * DAY_MS),
            UserAccount("usr_cbe_admin", "admin@cbe.com.et", "Adwa#Demo2026!", "Dawit Tadesse", AppRole.ORG_ADMIN, "org_cbe", "ACTIVE", false, null, emptyList(), nowMs - 1 * DAY_MS),
            UserAccount("usr_cbe_officer", "officer@cbe.com.et", "Adwa#Demo2026!", "Rahel Girma", AppRole.COMPLIANCE_OFFICER, "org_cbe", "ACTIVE", true, "UPRWOJXX2JVEYGDRDDXSWXDGCDIJLNLD", listOf("7H4K-9M2P", "3X8R-5V9Q", "9W2N-4B6T", "6L8J-2D5F", "4C7Y-8H3K", "5M9P-3Q7W", "2Z6X-8N4R", "8B3V-6T9L"), nowMs - 12 * 3600_000L),
            UserAccount("usr_dashen_aud", "auditor@dashen.com.et", "Adwa#Demo2026!", "Yohannes Alemu", AppRole.AUDITOR, "org_dashen", "ACTIVE", false, null, emptyList(), nowMs - 3 * DAY_MS),
            UserAccount("usr_ethio_view", "viewer@ethiotelecom.et", "Adwa#Demo2026!", "Tigist Haile", AppRole.VIEWER, "org_ethio", "ACTIVE", false, null, emptyList(), nowMs - 4 * DAY_MS),
            UserAccount("usr_wasasa_admin", "admin@wasasa.et", "Adwa#Demo2026!", "Bikila Negeri", AppRole.ORG_ADMIN, "org_wasasa", "ACTIVE", false, null, emptyList(), nowMs - 5 * DAY_MS),
        )

        val authEvents = listOf(
            AuthEvent("ae_1", "admin@adwacyber.et", null, "LOGIN_SUCCESS", "Authenticated platform super admin session", nowMs - 2 * DAY_MS),
            AuthEvent("ae_2", "officer@cbe.com.et", "org_cbe", "MFA_VERIFIED", "TOTP MFA challenge passed", nowMs - 12 * 3600_000L),
            AuthEvent("ae_3", "auditor@dashen.com.et", "org_dashen", "LOGIN_SUCCESS", "Read-only audit review session", nowMs - 3 * DAY_MS),
            AuthEvent("ae_4", "admin@cbe.com.et", "org_cbe", "SCAN_SUBMITTED", "Submitted CBE Q1 PD-1321 assessment", nowMs - 18 * DAY_MS),
        )

        val pdRules = rulesFor("PD-1321")
        val nbeRules = rulesFor("NBE-IT")
        val pdFw = frameworkFor("PD-1321")!!
        val nbeFw = frameworkFor("NBE-IT")!!

        // Scan 1: CBE PD-1321 (~79% mature bank)
        val cbePdAnswers = pdRules.associate { r ->
            val ans = when {
                r.code in setOf("ART11-01", "ART11-02", "ART22-03") -> Answer.PARTIAL
                r.code in setOf("ART9-03", "ART17-05", "ART24-04", "ART52-03") -> Answer.PARTIAL
                r.code in setOf("ART15-03", "ART43-03") -> Answer.NO
                else -> Answer.YES
            }
            r.code to ans
        }
        val cbePdEval = ComplianceEngine.evaluate("scan_cbe_pd", pdRules, cbePdAnswers, emptyMap(), pdFw.pillars, nowEpochMs = nowMs - 38 * DAY_MS)
        // Adjust a couple findings for realistic SLA demonstration (one closed on time, one overdue)
        val cbePdResults = cbePdEval.results.map { res ->
            when (res.ruleCode) {
                "ART43-03" -> res.copy(dueAtEpochMs = nowMs - 5 * DAY_MS) // overdue!
                "ART11-01" -> res.copy(status = "in_progress", dueAtEpochMs = nowMs + 4 * DAY_MS) // due soon!
                "ART9-03" -> res.copy(status = "remediated", remediatedAtEpochMs = nowMs - 12 * DAY_MS)
                else -> res
            }
        }
        val scan1 = ComplianceScan(
            id = "scan_cbe_pd",
            organizationId = "org_cbe",
            frameworkCode = "PD-1321",
            name = "CBE — Q1 Proclamation 1321/2024 Audit",
            status = "complete",
            auditorName = "Rahel Girma",
            overallScore = cbePdEval.overallScore,
            pillarScores = cbePdEval.pillarScores,
            findingsCount = cbePdResults.count { it.isFinding && it.status != "remediated" && it.status != "waived" },
            criticalFindings = cbePdEval.criticalFindings,
            startedAtEpochMs = nowMs - 38 * DAY_MS - 35 * MIN_MS,
            completedAtEpochMs = nowMs - 38 * DAY_MS,
            durationMinutes = 35.0,
            results = cbePdResults,
        )

        // Scan 2: Wasasa MFI PD-1321 (~52% growing MFI)
        val wasasaPdAnswers = pdRules.mapIndexed { idx, r ->
            val ans = when {
                idx % 5 == 0 -> Answer.NO
                idx % 3 == 0 -> Answer.PARTIAL
                idx == 24 -> Answer.NA
                else -> Answer.YES
            }
            r.code to ans
        }.toMap()
        val wasasaNotes = mapOf(pdRules[24].code to "MFI processes no international data transfers outside Ethiopia.")
        val wasasaEval = ComplianceEngine.evaluate("scan_wasasa_pd", pdRules, wasasaPdAnswers, wasasaNotes, pdFw.pillars, nowEpochMs = nowMs - 14 * DAY_MS)
        val scan2 = ComplianceScan(
            id = "scan_wasasa_pd",
            organizationId = "org_wasasa",
            frameworkCode = "PD-1321",
            name = "Wasasa MFI — Baseline PD-1321 Assessment",
            status = "complete",
            auditorName = "Bikila Negeri",
            overallScore = wasasaEval.overallScore,
            pillarScores = wasasaEval.pillarScores,
            findingsCount = wasasaEval.findingsCount,
            criticalFindings = wasasaEval.criticalFindings,
            startedAtEpochMs = nowMs - 14 * DAY_MS - 42 * MIN_MS,
            completedAtEpochMs = nowMs - 14 * DAY_MS,
            durationMinutes = 42.0,
            results = wasasaEval.results,
        )

        // Scan 3: CBE NBE-IT (~84%)
        val cbeNbeAnswers = nbeRules.associate { r ->
            val ans = when {
                r.code in setOf("NBE-09", "NBE-17", "NBE-24", "NBE-28") -> Answer.PARTIAL
                r.code == "NBE-22" -> Answer.NO
                else -> Answer.YES
            }
            r.code to ans
        }
        val cbeNbeEval = ComplianceEngine.evaluate("scan_cbe_nbe", nbeRules, cbeNbeAnswers, emptyMap(), nbeFw.pillars, nowEpochMs = nowMs - 9 * DAY_MS)
        val scan3 = ComplianceScan(
            id = "scan_cbe_nbe",
            organizationId = "org_cbe",
            frameworkCode = "NBE-IT",
            name = "CBE — SBB/83/2022 & Procl. 1426 Inspection",
            status = "complete",
            auditorName = "Dawit Tadesse",
            overallScore = cbeNbeEval.overallScore,
            pillarScores = cbeNbeEval.pillarScores,
            findingsCount = cbeNbeEval.findingsCount,
            criticalFindings = cbeNbeEval.criticalFindings,
            startedAtEpochMs = nowMs - 9 * DAY_MS - 28 * MIN_MS,
            completedAtEpochMs = nowMs - 9 * DAY_MS,
            durationMinutes = 28.0,
            results = cbeNbeEval.results,
        )

        // Scan 4: Dashen Bank NBE-IT (~74%)
        val dashenNbeAnswers = nbeRules.mapIndexed { idx, r ->
            val ans = when {
                idx % 6 == 0 -> Answer.NO
                idx % 4 == 0 -> Answer.PARTIAL
                else -> Answer.YES
            }
            r.code to ans
        }.toMap()
        val dashenEval = ComplianceEngine.evaluate("scan_dashen_nbe", nbeRules, dashenNbeAnswers, emptyMap(), nbeFw.pillars, nowEpochMs = nowMs - 6 * DAY_MS)
        val scan4 = ComplianceScan(
            id = "scan_dashen_nbe",
            organizationId = "org_dashen",
            frameworkCode = "NBE-IT",
            name = "Dashen Bank — Annual NBE IT Governance Scan",
            status = "complete",
            auditorName = "Yohannes Alemu",
            overallScore = dashenEval.overallScore,
            pillarScores = dashenEval.pillarScores,
            findingsCount = dashenEval.findingsCount,
            criticalFindings = dashenEval.criticalFindings,
            startedAtEpochMs = nowMs - 6 * DAY_MS - 31 * MIN_MS,
            completedAtEpochMs = nowMs - 6 * DAY_MS,
            durationMinutes = 31.0,
            results = dashenEval.results,
        )

        // Draft scan in progress for Wasasa NBE-IT so user sees Resume Draft banner
        val draftScan = ComplianceScan(
            id = "scan_wasasa_nbe_draft",
            organizationId = "org_wasasa",
            frameworkCode = "NBE-IT",
            name = "Wasasa MFI — NBE-IT Draft Review",
            status = "in_progress",
            auditorName = "Bikila Negeri",
            overallScore = null,
            pillarScores = emptyMap(),
            findingsCount = 0,
            criticalFindings = 0,
            startedAtEpochMs = nowMs - 2 * 3600_000L,
            completedAtEpochMs = null,
            durationMinutes = null,
            results = emptyList(),
        )
        val initialDraftAnswers = mapOf(
            "scan_wasasa_nbe_draft" to mapOf(
                "NBE-01" to Answer.YES,
                "NBE-02" to Answer.PARTIAL,
                "NBE-03" to Answer.YES,
                "NBE-04" to Answer.NO,
            )
        )

        // Evidence Library
        fun evSha(seed: String) = RulesEngine.sha256Hex(seed.toByteArray())
        val evidenceDocs = listOf(
            EvidenceDocument(
                id = "ev_1",
                title = "CBE Data Protection & Processing Register v1.0",
                description = "Initial master ROPA register covering retail & mobile banking.",
                category = EvidenceCategory.POLICY,
                tags = "ART2-01, ART2-02, ART6-01",
                fileName = "cbe_ropa_v1.pdf",
                mimeType = "application/pdf",
                fileSize = 248_910,
                checksumSha256 = evSha("cbe_ropa_v1"),
                status = "SUPERSEDED",
                version = 1,
                rootId = "ev_1",
                downloads = 4,
                lastVerifiedAtEpochMs = nowMs - 240 * DAY_MS,
                organizationId = "org_cbe",
                uploadedByName = "Rahel Girma",
                createdAtEpochMs = nowMs - 240 * DAY_MS,
                contentPreview = "CBE ROPA v1.0 — superseded by v2.0",
            ),
            EvidenceDocument(
                id = "ev_2",
                title = "CBE Data Protection & Processing Register v2.0",
                description = "Updated ROPA including telebirr & Fayda integration datasets, signed by DPO.",
                category = EvidenceCategory.POLICY,
                tags = "ART2-01, ART2-02, ART6-01, ART52-01",
                fileName = "cbe_ropa_v2_signed.pdf",
                mimeType = "application/pdf",
                fileSize = 412_300,
                checksumSha256 = evSha("cbe_ropa_v2_signed"),
                status = "ACTIVE",
                version = 2,
                rootId = "ev_1",
                downloads = 7,
                lastVerifiedAtEpochMs = nowMs - 25 * DAY_MS,
                organizationId = "org_cbe",
                uploadedByName = "Rahel Girma",
                createdAtEpochMs = nowMs - 25 * DAY_MS,
                contentPreview = "Approved Register of Processing Activities v2.0 covering 42 core systems.",
            ),
            EvidenceDocument(
                id = "ev_3",
                title = "TLS 1.3 & AES-256 KMS Encryption Configuration Export",
                description = "Production gateway & core banking encryption posture snapshot (quarterly).",
                category = EvidenceCategory.CONFIG_EXPORT,
                tags = "ART17-01, ART17-02, NBE-20",
                fileName = "cbe_kms_tls_export.json",
                mimeType = "application/json",
                fileSize = 84_120,
                checksumSha256 = evSha("cbe_kms_tls_export"),
                status = "ACTIVE",
                version = 1,
                rootId = "ev_3",
                downloads = 3,
                lastVerifiedAtEpochMs = nowMs - 140 * DAY_MS, // > 3 months -> STALE for CONFIG_EXPORT!
                organizationId = "org_cbe",
                uploadedByName = "Dawit Tadesse",
                createdAtEpochMs = nowMs - 140 * DAY_MS,
                contentPreview = "{\"tlsMin\":\"1.3\",\"kms\":\"HSM-FIPS-140-3\",\"aesBits\":256}",
            ),
            EvidenceDocument(
                id = "ev_4",
                title = "Board-Approved IT Strategy & SBB/83 Charter",
                description = "Signed board resolution adopting SBB/83/2022 IT governance & INSA cybersecurity plan.",
                category = EvidenceCategory.CERTIFICATE,
                tags = "NBE-01, NBE-02, NBE-03",
                fileName = "cbe_board_it_charter_2026.pdf",
                mimeType = "application/pdf",
                fileSize = 530_200,
                checksumSha256 = evSha("cbe_board_it_charter_2026"),
                status = "ACTIVE",
                version = 1,
                rootId = "ev_4",
                downloads = 9,
                lastVerifiedAtEpochMs = nowMs - 40 * DAY_MS,
                organizationId = "org_cbe",
                uploadedByName = "Dawit Tadesse",
                createdAtEpochMs = nowMs - 40 * DAY_MS,
                contentPreview = "Board Resolution #2026-014: Approved IT Strategy & Governance RACI.",
            ),
            EvidenceDocument(
                id = "ev_5",
                title = "72-Hour Breach Notification Drill & Ethio-CERT Runbook",
                description = "Tested incident escalation drill confirming Art. 43 & Procl. 1426 notification readiness.",
                category = EvidenceCategory.AUDIT_REPORT,
                tags = "ART43-01, ART43-02, NBE-25",
                fileName = "cbe_breach_drill_q1.pdf",
                mimeType = "application/pdf",
                fileSize = 195_800,
                checksumSha256 = evSha("cbe_breach_drill_q1"),
                status = "ACTIVE",
                version = 1,
                rootId = "ev_5",
                downloads = 2,
                lastVerifiedAtEpochMs = nowMs - 18 * DAY_MS,
                organizationId = "org_cbe",
                uploadedByName = "Rahel Girma",
                createdAtEpochMs = nowMs - 18 * DAY_MS,
                contentPreview = "Tabletop drill completed in 11 min median detection-to-notification latency.",
            ),
            EvidenceDocument(
                id = "ev_6",
                title = "Wasasa Staff Data Privacy Training Attendance",
                description = "Signed training roster covering PD-1321 basics and phishing hygiene.",
                category = EvidenceCategory.TRAINING_RECORD,
                tags = "ART52-02, ART6-03",
                fileName = "wasasa_training_2026.csv",
                mimeType = "text/csv",
                fileSize = 18_420,
                checksumSha256 = evSha("wasasa_training_2026"),
                status = "ACTIVE",
                version = 1,
                rootId = "ev_6",
                downloads = 1,
                lastVerifiedAtEpochMs = nowMs - 30 * DAY_MS,
                organizationId = "org_wasasa",
                uploadedByName = "Bikila Negeri",
                createdAtEpochMs = nowMs - 30 * DAY_MS,
                contentPreview = "58 of 64 branch staff completed PD-1321 Module AW-13.",
            ),
        )
        val evidenceLinks = listOf(
            EvidenceLink("el_1", "ART2-01", "ev_2", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_2", "ART2-02", "ev_2", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_3", "ART6-01", "ev_2", "SUPPORTS", "AUTO_TAG"),
            EvidenceLink("el_4", "ART52-01", "ev_2", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_5", "ART17-01", "ev_3", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_6", "ART17-02", "ev_3", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_7", "NBE-20", "ev_3", "SUPPORTS", "AUTO_TAG"),
            EvidenceLink("el_8", "NBE-01", "ev_4", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_9", "NBE-02", "ev_4", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_10", "NBE-03", "ev_4", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_11", "ART43-01", "ev_5", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_12", "ART43-02", "ev_5", "SUPPORTS", "AUTO_TAG"),
            EvidenceLink("el_13", "NBE-25", "ev_5", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_14", "ART52-02", "ev_6", "SATISFIES", "AUTO_TAG"),
            EvidenceLink("el_15", "ART6-03", "ev_6", "SUPPORTS", "AUTO_TAG"),
        )

        // Phase 2: SOC / CERT-as-a-Service
        val feedIndicators = SocEngine.ETHIOCERT_FEED.mapIndexed { idx, f ->
            ThreatIndicator(
                id = "ind_ec_${idx + 1}",
                type = f.type,
                value = f.value,
                titleEn = f.titleEn,
                titleAm = f.titleAm,
                descriptionEn = "National CERT bulletin ${f.bulletin}",
                descriptionAm = "ብሔራዊ የCERT መግለጫ ${f.bulletin}",
                category = f.category,
                severity = f.severity,
                tlp = f.tlp,
                source = "ETHIO_CERT",
                sourceRef = f.bulletin,
                confidence = f.confidence,
                sensitivity = f.sensitivity,
                sharing = f.sharing,
                sharingRationale = if (f.sharing == "P2P") "${f.tlp} — restricted to named recipients" else "${f.tlp} — anonymised hub broadcast",
                organizationId = null,
                sharedWithOrgIds = if (f.sharing == "P2P") listOf("org_cbe", "org_dashen", "org_ethio") else emptyList(),
                firstSeenAtEpochMs = nowMs - (12 - idx) * DAY_MS,
                lastSeenAtEpochMs = nowMs - (idx + 1) * 3600_000L,
                sightings = 3 + idx * 2,
                isActive = true,
            )
        }
        val memberIndicators = listOf(
            ThreatIndicator(
                id = "ind_mem_1",
                type = "ip",
                value = "185.220.101.47",
                titleEn = "Automated mobile-banking credential-stuffing exit node",
                titleAm = "የሞባይል ባንክ መግቢያ መሞከሪያ ምንጭ",
                descriptionEn = "Observed hitting CBE & Dashen API gateways at 1,400 req/min.",
                descriptionAm = "በCBE እና ዳሸን API ላይ የተገኘ ጥቃት።",
                category = "credential_stuffing",
                severity = "high",
                tlp = "TLP:AMBER",
                source = "MEMBER_HUB",
                sourceRef = "CBE-SOC-2026-09",
                confidence = 87,
                sensitivity = 3,
                sharing = "HUB",
                sharingRationale = "TLP:AMBER — anonymised hub broadcast is safe",
                organizationId = "org_cbe",
                sharedWithOrgIds = emptyList(),
                firstSeenAtEpochMs = nowMs - 4 * DAY_MS,
                lastSeenAtEpochMs = nowMs - 3 * 3600_000L,
                sightings = 14,
                isActive = true,
            ),
            ThreatIndicator(
                id = "ind_mem_2",
                type = "email",
                value = "swift-recon-ops@mail-et.org",
                titleEn = "Targeted insider/BEC lure spoofing treasury settlement",
                titleAm = "የትሬዠሪ ክፍያን የሚያስመስል የBEC አድራሻ",
                descriptionEn = "Restricted P2P indicator shared only with peer commercial banks.",
                descriptionAm = "በባንኮች መካከል በP2P የተጋራ ስጋት።",
                category = "bec",
                severity = "critical",
                tlp = "TLP:AMBER+STRICT",
                source = "MEMBER_P2P",
                sourceRef = "DASHEN-CERT-14",
                confidence = 91,
                sensitivity = 4,
                sharing = "P2P",
                sharingRationale = "TLP:AMBER+STRICT — restricted to named recipients",
                organizationId = "org_dashen",
                sharedWithOrgIds = listOf("org_cbe", "org_dashen"),
                firstSeenAtEpochMs = nowMs - 2 * DAY_MS,
                lastSeenAtEpochMs = nowMs - 5 * 3600_000L,
                sightings = 5,
                isActive = true,
            ),
        )
        val indicators = feedIndicators + memberIndicators

        val incidents = listOf(
            SecurityIncident(
                id = "inc_1", organizationId = "org_cbe", ref = "INC-2026-0041",
                title = "Phishing domain impersonating CBE Internet Banking",
                titleAm = "የኢትዮጵያ ንግድ ባንክን የሚያስመስል ሐሰተኛ ድረ-ገጽ",
                description = "Active phishing kit detected matching Ethio-CERT bulletin EC-BUL-2026-041.",
                category = "phishing", severity = "critical", status = IncidentStatus.CONTAINED,
                source = "ETHIO_CERT_HUB", detectionMethod = "IOC_MATCH",
                priorityScore = 88.0, priorityLabel = "IMMEDIATE",
                detectedAtEpochMs = nowMs - 26 * 3600_000L,
                notifiedAtEpochMs = nowMs - 26 * 3600_000L + 6 * MIN_MS,
                triagedAtEpochMs = nowMs - 26 * 3600_000L + 11 * MIN_MS,
                containedAtEpochMs = nowMs - 25 * 3600_000L,
                eradicatedAtEpochMs = null, recoveredAtEpochMs = null, closedAtEpochMs = null,
                reportedToEthiocertAtEpochMs = nowMs - 25 * 3600_000L,
                matchedIndicatorIds = listOf("ind_ec_1"),
                events = listOf(
                    IncidentEvent("ie_1", "DETECTED", "Matched cbe-secure-verify-portal.com", "Adwa SOC Engine", nowMs - 26 * 3600_000L),
                    IncidentEvent("ie_2", "NOTIFIED", "SMS & portal alert delivered to CBE CISO (6 min)", "Rahel Girma", nowMs - 26 * 3600_000L + 6 * MIN_MS),
                    IncidentEvent("ie_3", "CONTAINED", "DNS sinkhole + registrar takedown filed", "Dawit Tadesse", nowMs - 25 * 3600_000L),
                ),
            ),
            SecurityIncident(
                id = "inc_2", organizationId = "org_dashen", ref = "INC-2026-0042",
                title = "Credential stuffing burst against mobile API gateway",
                titleAm = "በሞባይል API ላይ የይለፍ ቃል መሞከር ጥቃት",
                description = "1,400/min failed login attempts from Tor/proxy exit IPs.",
                category = "credential_stuffing", severity = "high", status = IncidentStatus.CLOSED,
                source = "SIEM_ALERT", detectionMethod = "RATE_ANOMALY",
                priorityScore = 76.0, priorityLabel = "IMMEDIATE",
                detectedAtEpochMs = nowMs - 5 * DAY_MS,
                notifiedAtEpochMs = nowMs - 5 * DAY_MS + 9 * MIN_MS,
                triagedAtEpochMs = nowMs - 5 * DAY_MS + 14 * MIN_MS,
                containedAtEpochMs = nowMs - 5 * DAY_MS + 29 * MIN_MS,
                eradicatedAtEpochMs = nowMs - 5 * DAY_MS + 75 * MIN_MS,
                recoveredAtEpochMs = nowMs - 5 * DAY_MS + 95 * MIN_MS,
                closedAtEpochMs = nowMs - 5 * DAY_MS + 120 * MIN_MS,
                reportedToEthiocertAtEpochMs = nowMs - 5 * DAY_MS + 40 * MIN_MS,
                matchedIndicatorIds = listOf("ind_ec_2", "ind_mem_1"),
                events = listOf(
                    IncidentEvent("ie_4", "DETECTED", "WAF rate limit exceeded", "Adwa SOC Engine", nowMs - 5 * DAY_MS),
                    IncidentEvent("ie_5", "CLOSE", "IP range blocked and accounts reset", "Yohannes Alemu", nowMs - 5 * DAY_MS + 120 * MIN_MS),
                ),
            ),
            SecurityIncident(
                id = "inc_3", organizationId = "org_wasasa", ref = "INC-2026-0043",
                title = "Macro lure email targeting Wasasa finance clerks",
                titleAm = "የፋይናንስ ሠራተኞችን ያነጣጠረ አደገኛ የኢሜይል ሰነድ",
                description = "Fake NBE circular attachment NBE_circular_2026_DBS-41.docm quarantined.",
                category = "malware", severity = "high", status = IncidentStatus.ERADICATED,
                source = "EMAIL_GATEWAY", detectionMethod = "HASH_MATCH",
                priorityScore = 69.0, priorityLabel = "HIGH",
                detectedAtEpochMs = nowMs - 3 * DAY_MS,
                notifiedAtEpochMs = nowMs - 3 * DAY_MS + 11 * MIN_MS,
                triagedAtEpochMs = nowMs - 3 * DAY_MS + 18 * MIN_MS,
                containedAtEpochMs = nowMs - 3 * DAY_MS + 42 * MIN_MS,
                eradicatedAtEpochMs = nowMs - 3 * DAY_MS + 90 * MIN_MS,
                recoveredAtEpochMs = null, closedAtEpochMs = null,
                reportedToEthiocertAtEpochMs = null,
                matchedIndicatorIds = listOf("ind_ec_8"),
                events = listOf(
                    IncidentEvent("ie_6", "DETECTED", "Quarantined 4 emails in Oromia branch", "Adwa SOC Engine", nowMs - 3 * DAY_MS),
                ),
            ),
            SecurityIncident(
                id = "inc_4", organizationId = "org_ethio", ref = "INC-2026-0044",
                title = "DDoS probe against public billing portal",
                titleAm = "በክፍያ ፖርታል ላይ የDDoS ሙከራ",
                description = "Low-volume UDP amplification probe from known bootstresser IP.",
                category = "ddos", severity = "medium", status = IncidentStatus.RECOVERED,
                source = "NETFLOW", detectionMethod = "IOC_MATCH",
                priorityScore = 54.0, priorityLabel = "HIGH",
                detectedAtEpochMs = nowMs - 7 * DAY_MS,
                notifiedAtEpochMs = nowMs - 7 * DAY_MS + 13 * MIN_MS,
                triagedAtEpochMs = nowMs - 7 * DAY_MS + 20 * MIN_MS,
                containedAtEpochMs = nowMs - 7 * DAY_MS + 35 * MIN_MS,
                eradicatedAtEpochMs = nowMs - 7 * DAY_MS + 65 * MIN_MS,
                recoveredAtEpochMs = nowMs - 7 * DAY_MS + 80 * MIN_MS,
                closedAtEpochMs = null,
                reportedToEthiocertAtEpochMs = nowMs - 7 * DAY_MS + 45 * MIN_MS,
                matchedIndicatorIds = listOf("ind_ec_7"),
                events = listOf(
                    IncidentEvent("ie_7", "RECOVER", "Traffic scrubbing confirmed nominal", "Tigist Haile", nowMs - 7 * DAY_MS + 80 * MIN_MS),
                ),
            ),
            SecurityIncident(
                id = "inc_5", organizationId = "org_cbe", ref = "INC-2026-0045",
                title = "BEC treasury settlement impersonation attempt",
                titleAm = "የትሬዠሪ ክፍያ የBEC ማስመሰል ሙከራ",
                description = "SPF-failing email from swift-recon-ops@mail-et.org blocked at gateway.",
                category = "bec", severity = "critical", status = IncidentStatus.TRIAGED,
                source = "EMAIL_GATEWAY", detectionMethod = "DMARC_FAIL",
                priorityScore = 84.0, priorityLabel = "IMMEDIATE",
                detectedAtEpochMs = nowMs - 8 * 3600_000L,
                notifiedAtEpochMs = nowMs - 8 * 3600_000L + 5 * MIN_MS,
                triagedAtEpochMs = nowMs - 8 * 3600_000L + 12 * MIN_MS,
                containedAtEpochMs = null, eradicatedAtEpochMs = null, recoveredAtEpochMs = null, closedAtEpochMs = null,
                reportedToEthiocertAtEpochMs = null,
                matchedIndicatorIds = listOf("ind_mem_2"),
                events = listOf(
                    IncidentEvent("ie_8", "TRIAGE", "Confirmed P2P match with Dashen CERT", "Rahel Girma", nowMs - 8 * 3600_000L + 12 * MIN_MS),
                ),
            ),
            SecurityIncident(
                id = "inc_6", organizationId = "org_oromia", ref = "INC-2026-0046",
                title = "Suspicious ransomware staging binary beacon",
                titleAm = "አጠራጣሪ የራንሶምዌር ማገናኛ ሙከራ",
                description = "Branch workstation attempted outbound fetch to 198.51.100.23.",
                category = "ransomware", severity = "critical", status = IncidentStatus.DETECTED,
                source = "EDR", detectionMethod = "IOC_MATCH",
                priorityScore = 89.0, priorityLabel = "IMMEDIATE",
                detectedAtEpochMs = nowMs - 4 * MIN_MS,
                notifiedAtEpochMs = null,
                triagedAtEpochMs = null, containedAtEpochMs = null, eradicatedAtEpochMs = null, recoveredAtEpochMs = null, closedAtEpochMs = null,
                reportedToEthiocertAtEpochMs = null,
                matchedIndicatorIds = listOf("ind_ec_6"),
                events = listOf(
                    IncidentEvent("ie_9", "DETECTED", "Awaiting operator notification", "Adwa SOC Engine", nowMs - 4 * MIN_MS),
                ),
            ),
        )

        val syncRuns = listOf(
            SyncRun(
                id = "sync_1",
                direction = "INGEST",
                status = "SUCCESS",
                source = "LIVE",
                ingested = 8,
                updated = 0,
                reported = 0,
                payloadHash = RulesEngine.sha256Hex("EC-BUL-2026-041..048".toByteArray()),
                receiptId = "EC-INGEST-202605-01",
                organizationId = null,
                detail = "Pulled 8 national bulletins EC-BUL-2026-041..048 from Ethio-CERT hub",
                createdAtEpochMs = nowMs - 6 * 3600_000L,
            )
        )

        // Phase 3: Track B Awareness Platform (5 Addis Ababa schools, 10 classes)
        val schools = listOf(
            AwarenessSchool("sch_1", "Menelik II Preparatory School", "ዳግማዊ ምኒልክ ሁለተኛ ደረጃ ት/ቤት", "Arada", "Ato Girma Teshome"),
            AwarenessSchool("sch_2", "Bole High School", "ቦሌ ከፍተኛ ሁለተኛ ደረጃ ት/ቤት", "Bole", "W/ro Tigist Mamo"),
            AwarenessSchool("sch_3", "Yekatit 12 Secondary School", "የካቲት 12 ሁለተኛ ደረጃ ት/ቤት", "Gullele", "Ato Samson Bekele"),
            AwarenessSchool("sch_4", "Kokebe Tsibah Secondary School", "ኮከበ ጽባሕ ሁለተኛ ደረጃ ት/ቤት", "Yeka", "W/ro Meron Kebede"),
            AwarenessSchool("sch_5", "Lideta Catholic Cathedral School", "ልደታ ካቴድራል ት/ቤት", "Lideta", "Sr. Aster Solomon"),
        )

        val classes = listOf(
            AwarenessClass("cls_1", "sch_1", "Grade 9A — ICT Club", 9, "TREATMENT", "ADWA-M2S-9A", "Mesfin Haile"),
            AwarenessClass("cls_2", "sch_1", "Grade 9B — Control", 9, "CONTROL", "ADWA-M2S-9B", "Abebe Worku"),
            AwarenessClass("cls_3", "sch_2", "Grade 10A — Cyber Safety", 10, "TREATMENT", "ADWA-BOL-10A", "Hanna Desta"),
            AwarenessClass("cls_4", "sch_2", "Grade 10B — Control", 10, "CONTROL", "ADWA-BOL-10B", "Dawit Lemma"),
            AwarenessClass("cls_5", "sch_3", "Grade 8A — Digital Citizenship", 8, "TREATMENT", "ADWA-Y12-8A", "Kidan Tilahun"),
            AwarenessClass("cls_6", "sch_3", "Grade 8B — Control", 8, "CONTROL", "ADWA-Y12-8B", "Tewodros Ayele"),
            AwarenessClass("cls_7", "sch_4", "Grade 9A — Amharic Lab", 9, "TREATMENT", "ADWA-KOK-9A", "Bethlehem Assefa"),
            AwarenessClass("cls_8", "sch_4", "Grade 9B — Control", 9, "CONTROL", "ADWA-KOK-9B", "Yared Solomon"),
            AwarenessClass("cls_9", "sch_5", "Grade 10A — Security Pilot", 10, "TREATMENT", "ADWA-LID-10A", "Mekdes Fikre"),
            AwarenessClass("cls_10", "sch_5", "Grade 10B — Control", 10, "CONTROL", "ADWA-LID-10B", "Samuel Negash"),
        )

        val learners = mutableListOf<AwarenessLearner>()
        // Demo codes inside ADWA-M2S-9A (cls_1)
        learners += AwarenessLearner(
            id = "lrn_demo_1", classId = "cls_1", code = "AW-DEMO01", grade = 9,
            consent = "GRANTED", consentRef = "PARENT-REG-2026-001",
            preScore = 43.75, preCorrect = 7, preTotal = 16,
            postScore = null, postCorrect = null, postTotal = null,
            completedModules = mapOf("AW-01" to 100.0, "AW-02" to 100.0),
        )
        learners += AwarenessLearner(
            id = "lrn_demo_2", classId = "cls_1", code = "AW-DEMO02", grade = 9,
            consent = "PENDING", consentRef = null,
            preScore = null, preCorrect = null, preTotal = null,
            postScore = null, postCorrect = null, postTotal = null,
            completedModules = emptyMap(),
        )

        val allKidsCodes = listOf("AW-01", "AW-02", "AW-03", "AW-04", "AW-05", "AW-06", "AW-07", "AW-08")
        classes.forEachIndexed { cIdx, cls ->
            for (i in 1..15) {
                val id = "lrn_${cls.id}_$i"
                val code = "AW-${(cIdx + 1).toString().padStart(2, '0')}${i.toString().padStart(3, '0')}"
                val consent = when {
                    i == 15 && cIdx % 2 == 1 -> "DENIED"
                    i == 14 && cIdx > 1 -> "PENDING"
                    else -> "GRANTED"
                }
                if (consent != "GRANTED") {
                    learners += AwarenessLearner(id, cls.id, code, cls.grade, consent, null, null, null, null, null, null, null, emptyMap())
                } else {
                    val preC = 5 + ((cIdx * 3 + i) % 5) // 5..9 / 16 (31.25%..56.25%)
                    val gainC = if (cls.groupType == "TREATMENT") 5 + ((cIdx + i) % 3) else ((i % 3) - 0) // +5..+7 vs 0..+2 items
                    val postC = (preC + gainC).coerceAtMost(16)
                    val preS = kotlin.math.round((preC / 16.0) * 10000.0) / 100.0
                    val postS = kotlin.math.round((postC / 16.0) * 10000.0) / 100.0
                    val completed = if (cls.groupType == "TREATMENT") {
                        allKidsCodes.associateWith { 85.0 + ((i * 5) % 15) }
                    } else {
                        emptyMap()
                    }
                    learners += AwarenessLearner(
                        id = id, classId = cls.id, code = code, grade = cls.grade,
                        consent = "GRANTED", consentRef = "CREG-$code",
                        preScore = preS, preCorrect = preC, preTotal = 16,
                        postScore = postS, postCorrect = postC, postTotal = 16,
                        completedModules = completed,
                    )
                }
            }
        }

        val toolIds = listOf("password", "phishing", "sextortion", "footprint", "flagging", "report-guide")
        val toolPings = (1..120).map { i ->
            AwarenessToolPing(
                toolId = toolIds[i % toolIds.size],
                lang = if (i % 5 == 0) "en" else "am",
                createdAtEpochMs = nowMs - (i * 45 * MIN_MS),
            )
        }

        // Phase 4 & 5: Track C Gated Expansion
        val gates = listOf(
            ExpansionGate(
                id = "gate_p4_1", phase = "P4_ACFS", code = "P4-ACFS-01",
                titleEn = "Formal MTI / AfCFTA Secretariat Mandate",
                titleAm = "የንግድ ሚኒስቴር / AfCFTA ሥልጣን ደብዳቤ",
                detailEn = "Signed cooperation charter authorising continental compliance cross-walking.",
                detailAm = "የAfCFTA አህጉራዊ ተስማሚነት ማዛመጃ ስምምነት።",
                category = "Legal", status = "SATISFIED", blocking = true,
                notes = "Charter filed and verified.",
                evidence = listOf(GateEvidence("ge_1", "mti_afcfta_mandate_2026.pdf", "Signed MTI cooperation MOU", evSha("mti_afcfta_mandate"), 312_000, "Selamawit Bekele", nowMs - 20 * DAY_MS)),
                events = listOf(
                    GateEvent("gev_1", "CRITERION_SEEDED", null, "NOT_STARTED", "Gate criterion initialised", "System", nowMs - 45 * DAY_MS),
                    GateEvent("gev_2", "EVIDENCE_ATTACHED", null, null, "Attached mti_afcfta_mandate_2026.pdf", "Selamawit Bekele", nowMs - 20 * DAY_MS),
                    GateEvent("gev_3", "STATUS_CHANGE", "IN_PROGRESS", "SATISFIED", "MTI mandate confirmed", "Selamawit Bekele", nowMs - 19 * DAY_MS),
                ),
            ),
            ExpansionGate(
                id = "gate_p4_2", phase = "P4_ACFS", code = "P4-ACFS-02",
                titleEn = "Malabo Convention Ratification & Cross-Border Adequacy",
                titleAm = "የማላቦ ስምምነት እና የድንበር ተሻጋሪ የውሂብ ደህንነት",
                detailEn = "AU Malabo Convention alignment schedule & Procl. 1321 Art. 22 transfer whitelist.",
                detailAm = "ከአፍሪካ ህብረት የማላቦ ስምምነት ጋር ማጣጣም።",
                category = "Legal", status = "IN_PROGRESS", blocking = true,
                notes = "Awaiting Ministry of Justice adequacy opinion.",
                evidence = emptyList(),
                events = listOf(GateEvent("gev_4", "STATUS_CHANGE", "NOT_STARTED", "IN_PROGRESS", "Legal review underway", "Selamawit Bekele", nowMs - 10 * DAY_MS)),
            ),
            ExpansionGate(
                id = "gate_p4_3", phase = "P4_ACFS", code = "P4-ACFS-03",
                titleEn = "Federated Rule Catalogue Schema (v2.0)",
                titleAm = "የፌዴሬትድ ደንብ መዝገብ መዋቅር (v2.0)",
                detailEn = "Multi-jurisdiction control mapping supporting AU, ECOWAS, COMESA & PD-1321.",
                detailAm = "የበርካታ ሀገራት የቅጥ ቁጥጥር ማዛመጃ።",
                category = "Technical", status = "IN_PROGRESS", blocking = true,
                notes = "Schema drafted; awaiting pilot bank testing.",
                evidence = emptyList(),
                events = emptyList(),
            ),
            ExpansionGate(
                id = "gate_p4_4", phase = "P4_ACFS", code = "P4-ACFS-04",
                titleEn = "Zero-Egress Sovereign Telemetry Isolation",
                titleAm = "የብሔራዊ ውሂብ ሉዓላዊነት መለያየት",
                detailEn = "Cryptographic guarantee that national personal data never crosses borders during benchmarking.",
                detailAm = "የግል ውሂብ ከኢትዮጵያ ውጭ እንዳይወጣ የሚያረጋግጥ መቆጣጠሪያ።",
                category = "Technical", status = "NOT_STARTED", blocking = true,
                notes = null, evidence = emptyList(), events = emptyList(),
            ),
            ExpansionGate(
                id = "gate_p5_1", phase = "P5_CII", code = "P5-CII-01",
                titleEn = "INSA Critical Infrastructure Authorisation Protocol",
                titleAm = "የኢንሳ (INSA) ወሳኝ መሠረተ-ልማት ፈቃድ ፕሮቶኮል",
                detailEn = "Formal Procl. 1426/2026 supervisory authorisation for passive telemetry monitoring.",
                detailAm = "በአዋጅ 1426/2026 መሠረት የኢንሳ ፈቃድ።",
                category = "Governance", status = "IN_PROGRESS", blocking = true,
                notes = "CBE & Ethio Telecom dossiers in review.",
                evidence = listOf(GateEvidence("ge_2", "insa_procl1426_protocol.pdf", "INSA joint oversight protocol draft", evSha("insa_protocol"), 445_000, "Selamawit Bekele", nowMs - 14 * DAY_MS)),
                events = emptyList(),
            ),
            ExpansionGate(
                id = "gate_p5_2", phase = "P5_CII", code = "P5-CII-02",
                titleEn = "Bilateral Operator MOU Template (Legal Review)",
                titleAm = "የሁለትዮሽ የኦፕሬተር ስምምነት (MOU) ቅጽ",
                detailEn = "Strict liability, incident SLA, and data-residency MOU cleared by operator counsel.",
                detailAm = "በሕግ ባለሙያዎች የተጸደቀ የCII MOU ቅጽ።",
                category = "Legal", status = "SATISFIED", blocking = true,
                notes = "Signed off by legal counsel.",
                evidence = listOf(GateEvidence("ge_3", "cii_bilateral_mou_v3.pdf", "Approved CII operator MOU template", evSha("cii_mou_v3"), 280_400, "Selamawit Bekele", nowMs - 22 * DAY_MS)),
                events = emptyList(),
            ),
            ExpansionGate(
                id = "gate_p5_3", phase = "P5_CII", code = "P5-CII-03",
                titleEn = "Air-Gapped OT/ICS Passive Tap Validation",
                titleAm = "የOT/ICS ተገላጭነት-አልባ ዳሳሽ ማረጋገጫ",
                detailEn = "Hardware diode verification for power grid & aviation SCADA sensors.",
                detailAm = "ለኃይልና አቪዬሽን ሥርዓቶች ዳሳሽ ፍተሻ።",
                category = "Technical", status = "BLOCKED", blocking = true,
                notes = "Blocked pending physical lab test with EEP substations.",
                evidence = emptyList(), events = emptyList(),
            ),
            ExpansionGate(
                id = "gate_p5_4", phase = "P5_CII", code = "P5-CII-04",
                titleEn = "24/7 Ethio-CERT Escalation Runbook",
                titleAm = "24/7 የኢትዮ-ሴርት ማሳወቂያ መመሪያ",
                detailEn = "Joint war-room procedure for Tier-1 national infrastructure incidents.",
                detailAm = "ለብሔራዊ መሠረተ-ልማት አደጋዎች የጋራ የምላሽ መመሪያ።",
                category = "Operational", status = "NOT_STARTED", blocking = true,
                notes = null, evidence = emptyList(), events = emptyList(),
            ),
        )

        val acfsItems = listOf(
            // Domain 1: Legal & Regulatory Alignment
            AcfsGateItem("acfs_1", "Legal", "ACFS-L1", "AU Malabo Convention alignment check", "የማላቦ ስምምነት ማጣጣም", "Map PD-1321 articles against AU Convention on Cyber Security & Data Protection.", "SATISFIED", "Legal Counsel", "Completed matrix", "malabo_crosswalk_v1.pdf", 1),
            AcfsGateItem("acfs_2", "Legal", "ACFS-L2", "AfCFTA Digital Trade Protocol Annex verification", "የAfCFTA ዲጂታል ንግድ ፕሮቶኮል", "Verify cross-border data flow exceptions under AfCFTA Digital Trade Protocol.", "EVIDENCE_SUBMITTED", "Ministry of Trade", "Annex review submitted", "afcfta_dtp_annex.pdf", 2),
            AcfsGateItem("acfs_3", "Legal", "ACFS-L3", "Data sovereignty exception registry", "የውሂብ ሉዓላዊነት ልዩ ሁኔታ መዝገብ", "Maintain Art. 22 transfer approvals before federation.", "IN_PROGRESS", "ECA Liaison", null, null, 3),
            // Domain 2: Technical & Interoperability
            AcfsGateItem("acfs_4", "Technical", "ACFS-T1", "Multi-framework JSON rule schema v2", "ባለብዙ ማዕቀፍ የደንብ መዋቅር v2", "Support jurisdiction tags and federated obligation crosswalks.", "SATISFIED", "Platform Arch", "Schema unit-tested", "schema_v2.json", 4),
            AcfsGateItem("acfs_5", "Technical", "ACFS-T2", "Zero-egress aggregate benchmarking API", "ድንበር-አልባ የውጤት ማነጻጸሪያ", "Export only differential-privacy sector averages; zero PII egress.", "IN_PROGRESS", "Security Eng", null, null, 5),
            AcfsGateItem("acfs_6", "Technical", "ACFS-T3", "STIX 2.1 cross-border TAXII feed adapter", "STIX 2.1 የስጋት ልውውጥ", "Enable AfricaCERT peer threat exchange.", "NOT_STARTED", "SOC Eng", null, null, 6),
            AcfsGateItem("acfs_7", "Technical", "ACFS-T4", "Amharic / Afaan Oromoo / French / English i18n keys", "ባለብዙ ቋንቋ ድጋፍ", "Provide bilingual + continental translation packs.", "IN_PROGRESS", "Product Team", null, null, 7),
            // Domain 3: Commercial & Pilot Readiness
            AcfsGateItem("acfs_8", "Commercial", "ACFS-C1", "Domestic Ethiopian bank & MFI Phase 1–3 stability (>90% SLA)", "የሀገር ውስጥ ሙከራ መረጋጋት", "Demonstrate sustained operation across 6 domestic pilot institutions.", "SATISFIED", "Delivery Lead", "CBE/Dashen/Wasasa active", "pilot_report_q1.pdf", 8),
            AcfsGateItem("acfs_9", "Commercial", "ACFS-C2", "REC / COMESA fintech anchor partner LOI", "የCOMESA ፊንቴክ አጋርነት", "Signed Letter of Intent from at least two regional fintechs.", "NOT_STARTED", "Partnerships", null, null, 9),
            AcfsGateItem("acfs_10", "Commercial", "ACFS-C3", "Cross-border pricing & SLA schedule", "የድንበር ተሻጋሪ ዋጋና SLA", "Publish transparent institutional licensing model.", "NOT_STARTED", "Finance", null, null, 10),
            // Domain 4: Governance & Sustainability
            AcfsGateItem("acfs_11", "Governance", "ACFS-G1", "Joint Adwa–INSA–ECA Steering Committee Charter", "የጋራ አስተባባሪ ኮሚቴ", "Quarterly board sign-off before continental onboarding.", "SATISFIED", "Board", "Charter active", "steering_charter.pdf", 11),
            AcfsGateItem("acfs_12", "Governance", "ACFS-G2", "Annual external security penetration test", "ዓመታዊ የደህንነት ፍተሻ", "Independent CREST/OSCP test of multi-tenant isolation.", "EVIDENCE_SUBMITTED", "External Auditor", "Report uploaded", "pentest_summary_2026.pdf", 12),
            AcfsGateItem("acfs_13", "Governance", "ACFS-G3", "Data Protection Officer sign-off for federation", "የDPO ማረጋገጫ", "Formal DPIA cleared for cross-border telemetry.", "BLOCKED", "DPO", "Awaiting Art. 22 ruling", null, 13),
        )

        val acfsDecisions = listOf(
            AcfsGateDecision("dec_1", "REMAINED_LOCKED", "Initial Q1 board review: Domestic Phase 1–3 stability confirmed, but Legal ACFS-L2/L3 and DPIA ACFS-G3 remain open.", "Selamawit Bekele", nowMs - 30 * DAY_MS),
            AcfsGateDecision("dec_2", "REMAINED_LOCKED", "Mid-quarter check: Pentest uploaded (ACFS-G2); federation stays LOCKED until all 13 preconditions are SATISFIED.", "Adwa Steering Board", nowMs - 7 * DAY_MS),
        )

        val ciiOperators = listOf(
            CiiOperator(
                id = "cii_eep", code = "EEP",
                name = "Ethiopian Electric Power", nameAm = "የኢትዮጵያ ኤሌክትሪክ ኃይል",
                sector = "Energy / Grid", tier = "Tier-1 CII", orgId = null, contactEmail = "scada-sec@eep.com.et",
                mous = listOf(CiiMou("mou_eep", 1, "SIGNED", listOf("SCADA passive telemetry", "Incident notification"), "Strict on-premise processing", nowMs - 25 * DAY_MS, nowMs + 340 * DAY_MS)),
                authorizations = listOf(CiiAuthorization("auth_eep", "INSA-1426-EEP-01", "UNDER_REVIEW", null, null, "Substations passive tap inspection")),
                systems = listOf(
                    CiiSystem("sys_eep_1", "National Dispatch SCADA", "ብሔራዊ የኃይል ማከፋፈያ SCADA", "OT/ICS", "CRITICAL", "INVENTORY_MAPPED"),
                    CiiSystem("sys_eep_2", "GERD Generation Telemetry Gateway", "የህዳሴ ግድብ ቴሌሜትሪ", "OT/ICS", "CRITICAL", "REGISTERED"),
                ),
            ),
            CiiOperator(
                id = "cii_ethio", code = "ETHIO-TELECOM",
                name = "Ethio Telecom", nameAm = "ኢትዮ ቴሌኮም",
                sector = "Telecom & Payments", tier = "Tier-1 CII", orgId = "org_ethio", contactEmail = "sec@ethiotelecom.et",
                mous = listOf(CiiMou("mou_ethio", 2, "ACTIVE", listOf("Core network alerts", "telebirr fraud IOCs", "DDoS NetFlow"), "National data residency enforced", nowMs - 60 * DAY_MS, nowMs + 305 * DAY_MS)),
                authorizations = listOf(CiiAuthorization("auth_ethio", "INSA-1426-ET-04", "ACTIVE", nowMs - 45 * DAY_MS, nowMs + 320 * DAY_MS, "24/7 CERT-as-a-Service telemetry")),
                systems = listOf(
                    CiiSystem("sys_et_1", "telebirr Core Settlement Switch", "የቴሌብር ዋና ክፍያ ሥርዓት", "FinTech CII", "CRITICAL", "MONITORING_AUTHORIZED"),
                    CiiSystem("sys_et_2", "Addis Ababa Core BGP Backbone", "የአዲስ አበባ ዋና ኔትወርክ", "Telecom Core", "CRITICAL", "AGENT_READY"),
                ),
            ),
            CiiOperator(
                id = "cii_cbe", code = "CBE",
                name = "Commercial Bank of Ethiopia", nameAm = "የኢትዮጵያ ንግድ ባንክ",
                sector = "Financial CII", tier = "Tier-1 CII", orgId = "org_cbe", contactEmail = "ciso@cbe.com.et",
                mous = listOf(CiiMou("mou_cbe", 1, "ACTIVE", listOf("Core banking SIEM feed", "NBE SBB/83 breach telemetry"), "NBE & PD-1321 dual compliance", nowMs - 40 * DAY_MS, nowMs + 325 * DAY_MS)),
                authorizations = listOf(CiiAuthorization("auth_cbe", "INSA-1426-CBE-02", "GRANTED", nowMs - 20 * DAY_MS, nowMs + 345 * DAY_MS, "Tier-1 banking SOC telemetry")),
                systems = listOf(
                    CiiSystem("sys_cbe_1", "T24 Core Banking Cluster", "ዋና የባንክ T24 ሥርዓት", "Core Banking", "CRITICAL", "MONITORING_AUTHORIZED"),
                    CiiSystem("sys_cbe_2", "CBE Birr Mobile Switch", "ሲቢኢ ብር ሞባይል ሥርዓት", "Digital Payments", "HIGH", "AGENT_READY"),
                ),
            ),
            CiiOperator(
                id = "cii_et_air", code = "ETHIOPIAN-AIRLINES",
                name = "Ethiopian Airlines Group", nameAm = "የኢትዮጵያ አየር መንገድ",
                sector = "Aviation / Transport", tier = "Tier-1 CII", orgId = null, contactEmail = "cyber@ethiopianairlines.com",
                mous = listOf(CiiMou("mou_air", 1, "LEGAL_REVIEW", listOf("Passenger PSS alerts", "ATC ground link"), "Pending aviation counsel sign-off", null, null)),
                authorizations = listOf(CiiAuthorization("auth_air", "INSA-1426-EAL-09", "PENDING", null, null, "Aviation infrastructure scoping")),
                systems = listOf(
                    CiiSystem("sys_air_1", "Bole Hub Flight Operations Network", "የቦሌ የበረራ ሥራ ኔትወርክ", "Aviation OT", "CRITICAL", "REGISTERED"),
                ),
            ),
        )

        return AdwaState(
            organizations = orgs,
            users = users,
            authEvents = authEvents,
            scans = listOf(scan1, scan2, scan3, scan4, draftScan),
            draftAnswers = initialDraftAnswers,
            draftNotes = emptyMap(),
            evidenceDocs = evidenceDocs,
            evidenceLinks = evidenceLinks,
            incidents = incidents,
            threatIndicators = indicators,
            syncRuns = syncRuns,
            awarenessSchools = schools,
            awarenessClasses = classes,
            awarenessLearners = learners,
            awarenessToolPings = toolPings,
            expansionGates = gates,
            acfsItems = acfsItems,
            acfsDecisions = acfsDecisions,
            ciiOperators = ciiOperators,
        )
    }
}
