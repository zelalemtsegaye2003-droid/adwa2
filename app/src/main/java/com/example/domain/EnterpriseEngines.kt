package com.example.domain

import com.example.model.AwarenessCampaign
import com.example.model.AwarenessLearner
import com.example.model.IncidentStatus
import com.example.model.Rule
import com.example.model.SecurityIncident
import kotlin.math.roundToInt

object EnterpriseEngines {

    // ── Cross-Framework Canonical Control Mapping (Vanta/Drata/OneTrust style) ─

    data class CanonicalControlGroup(
        val id: String,
        val domainEn: String,
        val domainAm: String,
        val descriptionEn: String,
        val pd1321Codes: List<String>,
        val nbeItCodes: List<String>,
        val acfsRef: String,
        val slaDays: Int,
    )

    val CANONICAL_GROUPS = listOf(
        CanonicalControlGroup(
            id = "CCG-IAM",
            domainEn = "Identity, MFA & Privileged Access",
            domainAm = "ማንነት፣ MFA እና ልዩ መዳረሻ",
            descriptionEn = "Enforce strong authentication, role-based least privilege, and periodic admin reviews.",
            pd1321Codes = listOf("PD-1321-A19-01", "PD-1321-A19-02", "PD-1321-A20-01"),
            nbeItCodes = listOf("NBE-IT-01", "NBE-IT-02", "NBE-IT-05"),
            acfsRef = "ACFS-IAM-01 · Privileged Access",
            slaDays = 30,
        ),
        CanonicalControlGroup(
            id = "CCG-CRYPTO",
            domainEn = "Encryption, TLS & Hardware Keys",
            domainAm = "ምስጠራ፣ TLS እና ሃርድዌር ቁልፎች",
            descriptionEn = "Protect sensitive financial & personal data in transit (TLS 1.2+/HSTS) and at rest.",
            pd1321Codes = listOf("PD-1321-A19-03", "PD-1321-A19-04"),
            nbeItCodes = listOf("NBE-IT-09", "NBE-IT-10"),
            acfsRef = "ACFS-CRY-02 · Cryptographic Controls",
            slaDays = 30,
        ),
        CanonicalControlGroup(
            id = "CCG-SOV",
            domainEn = "Data Sovereignty & Localization (Art. 22)",
            domainAm = "የውሂብ ሉዓላዊነት (አንቀጽ 22)",
            descriptionEn = "Govern cross-border transfers, adequacy approvals, and domestic data residency.",
            pd1321Codes = listOf("PD-1321-A22-01", "PD-1321-A22-02", "PD-1321-A22-03"),
            nbeItCodes = listOf("NBE-IT-18"),
            acfsRef = "ACFS-GOV-03 · Sovereign Residency",
            slaDays = 30,
        ),
        CanonicalControlGroup(
            id = "CCG-INC",
            domainEn = "SOC Monitoring & 72h Breach Notification",
            domainAm = "የSOC ክትትል እና የ72 ሰዓት ማሳወቂያ",
            descriptionEn = "Detect threats rapidly (<=15m RQ3) and notify DPA / Ethio-CERT / NBE within legal timelines.",
            pd1321Codes = listOf("PD-1321-A21-01", "PD-1321-A21-02"),
            nbeItCodes = listOf("NBE-IT-14", "NBE-IT-15"),
            acfsRef = "ACFS-SOC-01 · Ethio-CERT Reporting",
            slaDays = 30,
        ),
        CanonicalControlGroup(
            id = "CCG-HUMAN",
            domainEn = "Workforce & Public Cyber Awareness",
            domainAm = "የሰራተኞች እና የህዝብ የሳይበር ግንዛቤ",
            descriptionEn = "Run ongoing multilingual phishing simulations, pre/post knowledge checks, and consent logging.",
            pd1321Codes = listOf("PD-1321-A15-01", "PD-1321-A17-01"),
            nbeItCodes = listOf("NBE-IT-24"),
            acfsRef = "Track B · Bilingual Curriculum",
            slaDays = 60,
        ),
    )

    fun resolveRulesForGroup(group: CanonicalControlGroup, allRules: List<Rule>): List<Rule> {
        val codes = (group.pd1321Codes + group.nbeItCodes).toSet()
        return allRules.filter { it.code in codes }
    }

    // ── CrowdStrike-style SOC Playbooks & MITRE ATT&CK Tagging ────────────────

    data class PlaybookStep(
        val id: String,
        val phase: IncidentStatus,
        val mitreTactic: String,
        val titleEn: String,
        val titleAm: String,
    )

    fun playbookForIncident(inc: SecurityIncident): List<PlaybookStep> {
        val tactic = when (inc.category.lowercase()) {
            "phishing", "bec" -> "TA0001 Initial Access (T1566)"
            "credential_stuffing" -> "TA0006 Credential Access (T1110)"
            "ransomware" -> "TA0040 Impact (T1486)"
            "malware" -> "TA0002 Execution (T1204)"
            "ddos" -> "TA0040 Impact (T1498)"
            else -> "TA0009 Collection / Suspicious Activity"
        }
        return listOf(
            PlaybookStep(
                id = "pb_triage",
                phase = IncidentStatus.TRIAGED,
                mitreTactic = tactic,
                titleEn = "Validate scope, asset criticality & match Ethio-CERT IoCs",
                titleAm = "ስፋቱን፣ የንብረቱን ወሳኝነት እና የኢትዮ-CERT IoC ማዛመድን ያረጋግጡ",
            ),
            PlaybookStep(
                id = "pb_notify",
                phase = IncidentStatus.TRIAGED,
                mitreTactic = "Procl. 1321 Art. 21 · RQ3",
                titleEn = "Dispatch organization & DPO alert within <=15m SLA window",
                titleAm = "በ<=15 ደቂቃ ውስጥ ለተቋሙ እና ለDPO ማስጠንቀቂያ ይላኩ",
            ),
            PlaybookStep(
                id = "pb_contain",
                phase = IncidentStatus.CONTAINED,
                mitreTactic = tactic,
                titleEn = "Isolate affected host/account, revoke active sessions & block IoCs",
                titleAm = "ተጋላጭ መለያ/ሲስተም ያግዱ እና አስጊ IoCዎችን ይዝጉ",
            ),
            PlaybookStep(
                id = "pb_eradicate",
                phase = IncidentStatus.ERADICATED,
                mitreTactic = "NIST SP 800-61 §3.3",
                titleEn = "Remove malicious artifacts, rotate secrets & verify patch state",
                titleAm = "ጎጂ ፋይሎችን ያስወግዱ፣ ቁልፎችን ይቀይሩ እና ጥገናውን ያረጋግጡ",
            ),
            PlaybookStep(
                id = "pb_recover",
                phase = IncidentStatus.RECOVERED,
                mitreTactic = "NIST SP 800-61 §3.4",
                titleEn = "Restore clean production service & monitor 24h recurrence",
                titleAm = "ንጹህ አገልግሎት ይመልሱ እና ለ24 ሰዓት ይከታተሉ",
            ),
            PlaybookStep(
                id = "pb_cert",
                phase = IncidentStatus.CLOSED,
                mitreTactic = "Ethio-CERT Upward Report",
                titleEn = "File anonymised post-incident bulletin to National Hub & close",
                titleAm = "ስም-የለሽ ሪፖርት ለብሔራዊ ማዕከል ይላኩ እና ይዝጉ",
            ),
        )
    }

    // ── KnowBe4-style Human Risk Scoring (HRS) ────────────────────────────────

    data class HumanRiskSummary(
        val overallRiskScore: Int, // 0 (safest) .. 100 (highest human risk)
        val bandEn: String,
        val bandAm: String,
        val avgAssessmentPct: Int,
        val moduleCompletionPct: Int,
        val phishClickRatePct: Int,
        val reportRatePct: Int,
        val highRiskLearners: Int,
    )

    fun computeHumanRisk(
        learners: List<AwarenessLearner>,
        campaigns: List<AwarenessCampaign>,
        totalModules: Int,
    ): HumanRiskSummary {
        val assessed = learners.mapNotNull { it.postScore ?: it.preScore }
        val avgScore = if (assessed.isNotEmpty()) assessed.average().roundToInt().coerceIn(0, 100) else 50
        val avgCompleted = if (learners.isNotEmpty() && totalModules > 0) {
            ((learners.sumOf { it.completedModules.size }.toDouble() / (learners.size * totalModules)) * 100.0)
                .roundToInt().coerceIn(0, 100)
        } else 0

        val totalSent = campaigns.sumOf { it.sentCount }.coerceAtLeast(0)
        val totalClicked = campaigns.sumOf { it.clickedCount }.coerceAtLeast(0)
        val totalReported = campaigns.sumOf { it.reportedCount }.coerceAtLeast(0)

        val clickRate = if (totalSent > 0) ((totalClicked * 100.0) / totalSent).roundToInt().coerceIn(0, 100) else 12
        val reportRate = if (totalSent > 0) ((totalReported * 100.0) / totalSent).roundToInt().coerceIn(0, 100) else 35

        val knowledgeRisk = 100 - avgScore
        val trainingGapRisk = 100 - avgCompleted
        val overall = (knowledgeRisk * 0.45 + trainingGapRisk * 0.25 + clickRate * 0.45 - reportRate * 0.15)
            .roundToInt().coerceIn(5, 98)

        val highRiskCount = learners.count { l ->
            val score = l.postScore ?: l.preScore ?: 40.0
            score < 60.0 || l.completedModules.isEmpty()
        }

        val (bandEn, bandAm) = when {
            overall < 30 -> "Low Human Risk" to "ዝቅተኛ የሰው ኃይል ስጋት"
            overall < 55 -> "Moderate Risk" to "መካከለኛ ስጋት"
            overall < 75 -> "Elevated Risk" to "ከፍተኛ ስጋት"
            else -> "Critical Human Risk" to "ወሳኝ የሰው ኃይል ስጋት"
        }

        return HumanRiskSummary(
            overallRiskScore = overall,
            bandEn = bandEn,
            bandAm = bandAm,
            avgAssessmentPct = avgScore,
            moduleCompletionPct = avgCompleted,
            phishClickRatePct = clickRate,
            reportRatePct = reportRate,
            highRiskLearners = highRiskCount,
        )
    }
}
