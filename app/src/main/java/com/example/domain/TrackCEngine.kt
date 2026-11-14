package com.example.domain

import com.example.model.AcfsGateItem
import com.example.model.CiiOperator
import com.example.model.ExpansionGate
import kotlin.math.roundToInt

object TrackCEngine {

    data class PhaseReadiness(
        val phase: String,
        val total: Int,
        val satisfied: Int,
        val inProgress: Int,
        val blocked: Int,
        val readinessPct: Int,
        val open: Boolean,
    )

    fun canTransitionGate(from: String, to: String): Boolean {
        if (from == to) return false
        if (to == "BLOCKED") return true
        return when (from) {
            "BLOCKED" -> to == "IN_PROGRESS" || to == "NOT_STARTED"
            "NOT_STARTED" -> to == "IN_PROGRESS" || to == "SATISFIED"
            "IN_PROGRESS" -> to == "SATISFIED"
            else -> false
        }
    }

    fun phaseReadiness(phase: String, criteria: List<ExpansionGate>): PhaseReadiness {
        val list = criteria.filter { it.phase == phase }
        val blocking = list.filter { it.blocking }
        val satisfied = blocking.count { it.status == "SATISFIED" }
        val open = blocking.isNotEmpty() && satisfied == blocking.size
        val pct = if (blocking.isNotEmpty()) ((satisfied.toDouble() / blocking.size) * 100.0).roundToInt() else 0
        return PhaseReadiness(
            phase = phase,
            total = list.size,
            satisfied = satisfied,
            inProgress = list.count { it.status == "IN_PROGRESS" },
            blocked = list.count { it.status == "BLOCKED" },
            readinessPct = pct,
            open = open,
        )
    }

    fun isOperatorGateOpen(op: CiiOperator): Boolean {
        val mouOk = op.mous.any { it.status == "ACTIVE" || it.status == "SIGNED" }
        val authOk = op.authorizations.any { it.status == "ACTIVE" || it.status == "GRANTED" }
        return mouOk && authOk
    }

    fun operatorGateReasons(op: CiiOperator): List<String> {
        val reasons = mutableListOf<String>()
        if (op.mous.none { it.status == "ACTIVE" || it.status == "SIGNED" }) {
            reasons.add("MOU status: ${op.mous.firstOrNull()?.status ?: "NONE"}")
        }
        if (op.authorizations.none { it.status == "ACTIVE" || it.status == "GRANTED" }) {
            reasons.add("INSA auth: ${op.authorizations.firstOrNull()?.status ?: "NONE"}")
        }
        return reasons
    }

    fun isAcfsGateOpen(items: List<AcfsGateItem>): Boolean =
        items.isNotEmpty() && items.all { it.status == "SATISFIED" }
}
