package com.example.domain

import com.example.model.Answer
import com.example.model.PillarWeight
import com.example.model.Rule
import com.example.model.ScanResult
import kotlin.math.max
import kotlin.math.round

object ComplianceEngine {

    data class RemediationItem(
        val rank: Int,
        val ruleCode: String,
        val article: String,
        val titleEn: String,
        val titleAm: String,
        val remediationEn: String,
        val remediationAm: String,
        val riskRank: Double,
        val severity: String,
        val penaltyRef: String,
        val predictive: Boolean,
    )

    data class EvaluationOutput(
        val overallScore: Double,
        val pillarScores: Map<String, Double>,
        val results: List<ScanResult>,
        val findingsCount: Int,
        val criticalFindings: Int,
        val remediationPlan: List<RemediationItem>,
    )

    data class AuditTimeComparison(
        val manualLowHours: Int,
        val manualHighHours: Int,
        val engineMinutes: Double,
        val reductionPercent: Double,
        val rq1Target: Int,
    )

    fun penaltyFactor(penaltyRef: String): Double = when {
        penaltyRef.contains("4%") -> 3.0
        penaltyRef.contains("5–10 years") -> 3.0
        penaltyRef.contains("3–5 years") -> 2.0
        penaltyRef.contains("1–3 years") -> 1.5
        else -> 1.0
    }

    fun evaluate(
        scanId: String,
        rules: List<Rule>,
        answers: Map<String, Answer>,
        notes: Map<String, String>,
        pillars: List<PillarWeight>,
        existingStatuses: Map<String, ScanResult> = emptyMap(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): EvaluationOutput {
        val results = rules.map { rule ->
            val ans = answers[rule.code] ?: Answer.NA
            val score = ans.score
            val isFinding = ans == Answer.NO || ans == Answer.PARTIAL
            val pf = penaltyFactor(rule.penaltyRef)
            val answerFactor = when (ans) {
                Answer.NO -> 1.0
                Answer.PARTIAL -> 0.6
                else -> 0.0
            }
            val riskRank = if (isFinding) round(rule.weight * pf * answerFactor * 10.0) / 10.0 else 0.0
            val predictive = ans == Answer.PARTIAL && pf >= 2.0
            val prev = existingStatuses[rule.code]
            val status = when {
                !isFinding -> "remediated"
                prev != null -> prev.status
                else -> "open"
            }
            val dueAt = if (isFinding) prev?.dueAtEpochMs ?: SlaEngine.dueDateFor(nowEpochMs, rule.severity) else prev?.dueAtEpochMs

            ScanResult(
                id = prev?.id ?: "${scanId}_${rule.code}",
                scanId = scanId,
                ruleCode = rule.code,
                answer = ans,
                answeredAtEpochMs = prev?.answeredAtEpochMs ?: nowEpochMs,
                evidenceNote = notes[rule.code] ?: prev?.evidenceNote,
                score = score,
                riskRank = riskRank,
                isFinding = isFinding,
                predictive = predictive,
                findingEn = if (isFinding) {
                    val state = if (ans == Answer.NO) "not implemented" else "partially implemented"
                    "${rule.titleEn} — $state (${rule.article}, ${rule.severity} severity)"
                } else null,
                findingAm = if (isFinding) {
                    val state = if (ans == Answer.NO) "አልተተገበረም" else "በከፊል ተተገብሯል"
                    "${rule.titleAm} — $state (${rule.article})"
                } else null,
                status = status,
                waiverNote = prev?.waiverNote,
                dueAtEpochMs = dueAt,
                remediatedAtEpochMs = prev?.remediatedAtEpochMs,
            )
        }

        val resultByCode = results.associateBy { it.ruleCode }
        val pillarScores = mutableMapOf<String, Double>()

        for (pw in pillars) {
            val paired = rules.filter { it.pillar == pw.name }.map { it to resultByCode[it.code]!! }
            val applicable = paired.filter { (_, res) -> res.answer != Answer.NA }
            if (applicable.isEmpty()) {
                pillarScores[pw.name] = 0.0
                continue
            }
            val totalWeight = applicable.sumOf { (r, _) -> r.weight.toDouble() }
            val earned = applicable.sumOf { (r, res) -> r.weight * (res.score / 100.0) }
            pillarScores[pw.name] = if (totalWeight > 0) round((earned / totalWeight) * 1000.0) / 10.0 else 0.0
        }

        var weightedSum = 0.0
        var weightSum = 0.0
        for (pw in pillars) {
            val hasApplicable = rules.any { it.pillar == pw.name && resultByCode[it.code]?.answer != Answer.NA }
            if (hasApplicable) {
                weightedSum += (pillarScores[pw.name] ?: 0.0) * pw.weight
                weightSum += pw.weight
            }
        }
        val overallScore = if (weightSum > 0) round((weightedSum / weightSum) * 10.0) / 10.0 else 0.0

        val remediationPlan = rules
            .map { it to resultByCode[it.code]!! }
            .filter { (_, res) -> res.isFinding }
            .sortedByDescending { (_, res) -> res.riskRank }
            .mapIndexed { idx, (r, res) ->
                RemediationItem(
                    rank = idx + 1,
                    ruleCode = r.code,
                    article = r.article,
                    titleEn = r.titleEn,
                    titleAm = r.titleAm,
                    remediationEn = r.remediationEn,
                    remediationAm = r.remediationAm,
                    riskRank = res.riskRank,
                    severity = r.severity,
                    penaltyRef = r.penaltyRef,
                    predictive = res.predictive,
                )
            }

        val ruleByCode = rules.associateBy { it.code }
        val criticalFindings = results.count { res ->
            res.isFinding && ruleByCode[res.ruleCode]?.severity == "critical"
        }

        return EvaluationOutput(
            overallScore = overallScore,
            pillarScores = pillarScores,
            results = results,
            findingsCount = results.count { it.isFinding },
            criticalFindings = criticalFindings,
            remediationPlan = remediationPlan,
        )
    }

    fun auditTimeComparison(durationMinutes: Double): AuditTimeComparison {
        val manualLow = 40
        val manualHigh = 80
        val reduction = round((1.0 - durationMinutes / 60.0 / manualLow) * 1000.0) / 10.0
        return AuditTimeComparison(
            manualLowHours = manualLow,
            manualHighHours = manualHigh,
            engineMinutes = round(durationMinutes * 10.0) / 10.0,
            reductionPercent = max(0.0, reduction),
            rq1Target = 70,
        )
    }
}
