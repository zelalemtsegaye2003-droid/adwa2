package com.example.domain

import com.example.model.IncidentStatus
import com.example.model.SecurityIncident
import com.example.model.ThreatIndicator
import kotlin.math.round

object SocEngine {
    const val RQ3_TARGET_MINUTES = 15.0

    private val severityWeight = mapOf(
        "critical" to 40,
        "high" to 28,
        "medium" to 16,
        "low" to 8,
    )

    private val sectorCategoryBoost: Map<String, Map<String, Int>> = mapOf(
        "Banking" to mapOf("bec" to 14, "ransomware" to 12, "phishing" to 10, "credential_stuffing" to 10, "malware" to 8, "data_breach" to 8),
        "Microfinance" to mapOf("phishing" to 12, "bec" to 10, "credential_stuffing" to 8, "malware" to 6),
        "Telecom" to mapOf("ddos" to 14, "ransomware" to 8, "apt" to 8),
        "SME" to mapOf("phishing" to 12, "scam" to 10, "malware" to 6, "credential_stuffing" to 6),
    )

    private val sectorBaseline: Map<String, Map<String, Int>> = mapOf(
        "Banking" to mapOf("bec" to 26, "phishing" to 20, "ransomware" to 16, "credential_stuffing" to 12, "malware" to 10, "data_breach" to 8, "ddos" to 4, "apt" to 4),
        "Microfinance" to mapOf("phishing" to 28, "bec" to 18, "credential_stuffing" to 16, "malware" to 12, "ransomware" to 10, "scam" to 8, "data_breach" to 8),
        "Telecom" to mapOf("ddos" to 30, "ransomware" to 16, "apt" to 14, "malware" to 12, "phishing" to 12, "credential_stuffing" to 8, "bec" to 8),
        "SME" to mapOf("phishing" to 30, "scam" to 18, "malware" to 14, "credential_stuffing" to 12, "bec" to 10, "ransomware" to 8, "ddos" to 8),
    )

    data class Prioritisation(
        val score: Int,
        val label: String,
        val rationale: String,
    )

    data class LatencyStats(
        val count: Int,
        val medianMin: Double?,
        val p90Min: Double?,
        val withinTarget: Int,
        val targetMet: Boolean?,
    )

    data class LifecycleTimes(
        val mttaMin: Double?,
        val mttcMin: Double?,
        val mttrMin: Double?,
    )

    data class ThreatForecast(
        val category: String,
        val riskIndex: Int,
        val activeIndicators: Int,
        val recentIncidents: Int,
        val drivers: List<String>,
    )

    data class NationalFeedEntry(
        val type: String,
        val value: String,
        val titleEn: String,
        val titleAm: String,
        val category: String,
        val severity: String,
        val tlp: String,
        val confidence: Int,
        val sensitivity: Int,
        val sharing: String,
        val bulletin: String,
    )

    val ETHIOCERT_FEED: List<NationalFeedEntry> = listOf(
        NationalFeedEntry("domain", "cbe-secure-verify-portal.com", "Bank impersonation phishing domain", "የባንክ ማንነት የሚስበ የማጭበርበር ገጽ", "phishing", "critical", "TLP:AMBER", 92, 2, "HUB", "EC-BUL-2026-041"),
        NationalFeedEntry("ip", "203.0.113.66", "Active credential-stuffing source", "ንቁ የይለፍ ቃል መሞከሪያ ምንጭ", "credential_stuffing", "high", "TLP:AMBER", 85, 3, "HUB", "EC-BUL-2026-042"),
        NationalFeedEntry("hash_sha256", "9f2c4a71e8d3b5067c1a9e4f8b2d6a3c5e7f1b9d8a6c4e2f0d3b5a7c9e1f4b6d", "Mobile banking trojan dropper (Fleckpe-family)", "የሞባይል ባንክ ትሮጃን መስቫሽ", "malware", "critical", "TLP:AMBER", 88, 2, "HUB", "EC-BUL-2026-043"),
        NationalFeedEntry("domain", "ethiotelecom-bill-pay.online", "Utility-bill phishing domain", "የኤሌክትሪክ ክፍያ ማጭበርበሪያ ገጽ", "phishing", "high", "TLP:GREEN", 78, 1, "HUB", "EC-BUL-2026-044"),
        NationalFeedEntry("email", "procurement-ethiopian-airports.net@mail.cf", "BEC sender impersonating procurement staff", "የግዥ ሠራተኛ ሆኖ የሚቀርጥ የBEC ላኪ", "bec", "high", "TLP:AMBER", 81, 3, "HUB", "EC-BUL-2026-045"),
        NationalFeedEntry("url", "hxxp://198.51.100.23/nta-update.exe", "Ransomware staging URL (lockbit-style)", "የራንሶምዌር ዝግጅት አድራሻ", "ransomware", "critical", "TLP:RED", 90, 4, "P2P", "EC-BUL-2026-046"),
        NationalFeedEntry("ip", "192.0.2.140", "DDoS bootstresser C2", "የDDoS ጥቃት መቆጣጠሪያ", "ddos", "medium", "TLP:GREEN", 72, 2, "HUB", "EC-BUL-2026-047"),
        NationalFeedEntry("filename", "NBE_circular_2026_DBS-41.docm", "Macro-enabled lure document impersonating NBE circular", "የኤንቢኢ ዘመናዊ ሆኖ የቀረጸ ማታለያ ሰነድ", "malware", "high", "TLP:AMBER", 83, 3, "HUB", "EC-BUL-2026-048"),
    )

    fun prioritiseIncident(
        severity: String,
        category: String,
        orgSector: String?,
        confidence: Int = 60,
        ageMinutes: Int = 0,
        assetCriticality: Int = 3,
    ): Prioritisation {
        val sev = severityWeight[severity] ?: 12
        val boost = orgSector?.let { sectorCategoryBoost[it]?.get(category) } ?: 0
        val conf = confidence.coerceIn(0, 100)
        val confTerm = round(((conf - 50) / 100.0) * 12.0).toInt()
        val asset = assetCriticality.coerceIn(1, 5)
        val assetTerm = (asset - 3) * 3
        val age = maxOf(0, ageMinutes)
        val freshTerm = when {
            age <= 15 -> 6
            age <= 60 -> 3
            age <= 240 -> 0
            else -> -4
        }
        val score = (sev + boost + confTerm + assetTerm + freshTerm).coerceIn(0, 100)
        val label = when {
            score >= 75 -> "IMMEDIATE"
            score >= 50 -> "HIGH"
            else -> "ROUTINE"
        }
        val rationale = "severity $severity ($sev)" +
            (if (boost > 0) " + ${orgSector}x$category (+$boost)" else "") +
            " + conf $conf (${if (confTerm >= 0) "+" else ""}$confTerm)" +
            " + freshness ${age}m"
        return Prioritisation(score, label, rationale)
    }

    fun classifySensitivity(category: String, tlp: String, confidence: Int): Int {
        val base = mapOf(
            "insider" to 4, "data_breach" to 4, "apt" to 4, "bec" to 3, "ransomware" to 3,
            "malware" to 3, "credential_stuffing" to 3, "phishing" to 2, "ddos" to 2, "scam" to 2, "other" to 3,
        )
        var s = base[category] ?: 3
        if (tlp == "TLP:RED" || tlp == "TLP:AMBER+STRICT") s += 1
        if (tlp == "TLP:CLEAR" || tlp == "TLP:GREEN") s -= 1
        if (confidence >= 80) s += 1
        return s.coerceIn(1, 5)
    }

    fun hybridSharingDecision(tlp: String, sensitivity: Int, category: String): Pair<String, String> {
        val identitySensitive = category == "insider" || category == "data_breach"
        if (tlp == "TLP:RED" || tlp == "TLP:AMBER+STRICT") {
            return "P2P" to "$tlp — restricted to named recipients"
        }
        if (sensitivity >= 4) {
            return "P2P" to "sensitivity $sensitivity/5 — identity-protection applies"
        }
        if (identitySensitive && sensitivity >= 3) {
            return "P2P" to "$category indicators can identify the reporter — peer-to-peer only"
        }
        return "HUB" to "$tlp — anonymised hub broadcast is safe"
    }

    fun notificationLatency(incidents: List<SecurityIncident>): LatencyStats {
        val mins = incidents.mapNotNull { inc ->
            inc.notifiedAtEpochMs?.let { maxOf(0.0, (it - inc.detectedAtEpochMs) / 60_000.0) }
        }.sorted()
        if (mins.isEmpty()) return LatencyStats(0, null, null, 0, null)
        val mid = mins.size / 2
        val median = if (mins.size % 2 == 1) mins[mid] else (mins[mid - 1] + mins[mid]) / 2.0
        val p90Idx = (kotlin.math.ceil(0.9 * mins.size).toInt() - 1).coerceIn(0, mins.lastIndex)
        val p90 = mins[p90Idx]
        return LatencyStats(
            count = mins.size,
            medianMin = round(median * 10.0) / 10.0,
            p90Min = round(p90 * 10.0) / 10.0,
            withinTarget = mins.count { it <= RQ3_TARGET_MINUTES },
            targetMet = median <= RQ3_TARGET_MINUTES,
        )
    }

    fun lifecycleTimes(incidents: List<SecurityIncident>): LifecycleTimes {
        fun avgDiff(extract: (SecurityIncident) -> Long?): Double? {
            val diffs = incidents.mapNotNull { inc ->
                extract(inc)?.let { maxOf(0.0, (it - inc.detectedAtEpochMs) / 60_000.0) }
            }
            return if (diffs.isEmpty()) null else round((diffs.average()) * 10.0) / 10.0
        }
        return LifecycleTimes(
            mttaMin = avgDiff { it.triagedAtEpochMs },
            mttcMin = avgDiff { it.containedAtEpochMs },
            mttrMin = avgDiff { it.closedAtEpochMs },
        )
    }

    fun predictThreats(
        sector: String,
        activeIndicators: List<ThreatIndicator>,
        recentIncidents: List<SecurityIncident>,
    ): List<ThreatForecast> {
        val baseline = sectorBaseline[sector] ?: sectorBaseline["SME"]!!
        val categories = (baseline.keys + activeIndicators.map { it.category } + recentIncidents.map { it.category }).toSet()
        return categories.map { cat ->
            val base = baseline[cat] ?: 4
            val inds = activeIndicators.filter { it.category == cat }
            val incs = recentIncidents.filter { it.category == cat }
            val sevBoost = inds.sumOf { if (it.severity == "critical") 6 else if (it.severity == "high") 4 else 2 }
            val riskIndex = minOf(100, base + sevBoost + incs.size * 8)
            val drivers = mutableListOf("$sector baseline $base")
            if (inds.isNotEmpty()) drivers.add("${inds.size} hub indicator(s)")
            if (incs.isNotEmpty()) drivers.add("${incs.size} recent incident(s)")
            ThreatForecast(cat, riskIndex, inds.size, incs.size, drivers)
        }.sortedByDescending { it.riskIndex }
    }

    fun canAdvanceTo(current: IncidentStatus, next: IncidentStatus): Boolean =
        next.ordinal == current.ordinal + 1
}
