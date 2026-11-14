package com.example.domain

import com.example.model.EvidenceCategory
import com.example.model.EvidenceDocument
import com.example.model.EvidenceLink
import com.example.model.Rule
import java.security.MessageDigest

object RulesEngine {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    enum class CoverageStatus { SATISFIED, STALE, GAP }

    data class LinkedEvidenceRef(
        val docId: String,
        val title: String,
        val category: EvidenceCategory,
        val version: Int,
        val isFresh: Boolean,
        val ageMonths: Int,
        val linkType: String,
        val source: String,
    )

    data class RuleCoverage(
        val ruleCode: String,
        val status: CoverageStatus,
        val freshCount: Int,
        val staleCount: Int,
        val docs: List<LinkedEvidenceRef>,
    )

    fun freshnessOf(
        category: EvidenceCategory,
        createdAtEpochMs: Long,
        lastVerifiedAtEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Pair<Boolean, Int> {
        val anchor = maxOf(createdAtEpochMs, lastVerifiedAtEpochMs ?: createdAtEpochMs)
        val ageMs = maxOf(0L, nowEpochMs - anchor)
        val ageMonths = ((ageMs / DAY_MS) / 30.4375).toInt()
        val maxMs = (category.freshnessMonths * 30.4375 * DAY_MS).toLong()
        return (ageMs <= maxMs) to ageMonths
    }

    fun evaluateCoverage(
        rules: List<Rule>,
        documents: List<EvidenceDocument>,
        links: List<EvidenceLink>,
        organizationId: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Map<String, RuleCoverage> {
        val docMap = documents
            .filter { it.status == "ACTIVE" && (organizationId == null || it.organizationId == organizationId) }
            .associateBy { it.id }

        val linksByRule = links.groupBy { it.ruleCode }
        val result = mutableMapOf<String, RuleCoverage>()

        for (rule in rules) {
            val ruleLinks = linksByRule[rule.code].orEmpty()
            val refs = ruleLinks.mapNotNull { link ->
                val doc = docMap[link.evidenceId] ?: return@mapNotNull null
                val (fresh, ageMonths) = freshnessOf(doc.category, doc.createdAtEpochMs, doc.lastVerifiedAtEpochMs, nowEpochMs)
                LinkedEvidenceRef(
                    docId = doc.id,
                    title = doc.title,
                    category = doc.category,
                    version = doc.version,
                    isFresh = fresh,
                    ageMonths = ageMonths,
                    linkType = link.linkType,
                    source = link.source,
                )
            }
            val freshCount = refs.count { it.isFresh }
            val staleCount = refs.count { !it.isFresh }
            val status = when {
                freshCount > 0 -> CoverageStatus.SATISFIED
                staleCount > 0 -> CoverageStatus.STALE
                else -> CoverageStatus.GAP
            }
            result[rule.code] = RuleCoverage(rule.code, status, freshCount, staleCount, refs)
        }
        return result
    }

    private val rulePattern = Regex("""\b(ART\d{1,2}-\d{2}|NBE-\d{2})\b""", RegexOption.IGNORE_CASE)
    private val articlePattern = Regex("""\bArt\.?\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE)

    fun parseRuleRefs(tags: String, allRules: List<Rule>): List<String> {
        val validCodes = allRules.map { it.code }.toSet()
        val matched = linkedSetOf<String>()
        rulePattern.findAll(tags).forEach { m ->
            val code = m.groupValues[1].uppercase()
            if (code in validCodes) matched.add(code)
        }
        articlePattern.findAll(tags).forEach { m ->
            val num = m.groupValues[1]
            allRules.filter { it.code.startsWith("ART$num-") }.forEach { matched.add(it.code) }
        }
        return matched.toList()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
