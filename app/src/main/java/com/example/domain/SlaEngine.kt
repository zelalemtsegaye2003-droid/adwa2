package com.example.domain

import kotlin.math.ceil

object SlaEngine {
    const val DAY_MS = 24L * 60L * 60L * 1000L
    const val DUE_SOON_DAYS = 7

    val SLA_DAYS: Map<String, Int> = mapOf(
        "critical" to 30,
        "high" to 60,
        "medium" to 90,
    )

    enum class SlaState {
        ON_TRACK,
        DUE_SOON,
        OVERDUE,
        CLOSED_ON_TIME,
        CLOSED_LATE,
        WAIVED,
        NOT_TRACKED,
    }

    data class SlaInfo(
        val state: SlaState,
        val daysRemaining: Int?,
        val daysOverdue: Int?,
        val dueAtEpochMs: Long?,
        val targetDays: Int?,
    )

    data class SlaSummary(
        val trackedOpen: Int,
        val overdue: Int,
        val dueSoon: Int,
        val onTrack: Int,
        val closedOnTime: Int,
        val closedLate: Int,
    )

    fun dueDateFor(completedAtEpochMs: Long, severity: String): Long {
        val days = SLA_DAYS[severity] ?: 90
        return completedAtEpochMs + days * DAY_MS
    }

    fun slaInfo(
        status: String,
        dueAtEpochMs: Long?,
        remediatedAtEpochMs: Long?,
        severity: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SlaInfo {
        val targetDays = severity?.let { SLA_DAYS[it] }
        if (dueAtEpochMs == null) {
            return SlaInfo(SlaState.NOT_TRACKED, null, null, null, targetDays)
        }
        if (status == "waived") {
            return SlaInfo(SlaState.WAIVED, null, null, dueAtEpochMs, targetDays)
        }
        if (status == "remediated") {
            val closedAt = remediatedAtEpochMs ?: nowEpochMs
            if (closedAt <= dueAtEpochMs) {
                return SlaInfo(SlaState.CLOSED_ON_TIME, null, null, dueAtEpochMs, targetDays)
            }
            val late = maxOf(1, ceil((closedAt - dueAtEpochMs).toDouble() / DAY_MS).toInt())
            return SlaInfo(SlaState.CLOSED_LATE, null, late, dueAtEpochMs, targetDays)
        }
        val diff = dueAtEpochMs - nowEpochMs
        if (diff < 0) {
            val over = maxOf(1, ceil((-diff).toDouble() / DAY_MS).toInt())
            return SlaInfo(SlaState.OVERDUE, null, over, dueAtEpochMs, targetDays)
        }
        val left = ceil(diff.toDouble() / DAY_MS).toInt()
        val state = if (left <= DUE_SOON_DAYS) SlaState.DUE_SOON else SlaState.ON_TRACK
        return SlaInfo(state, left, null, dueAtEpochMs, targetDays)
    }

    fun summarizeSla(items: List<SlaInfo>): SlaSummary {
        var trackedOpen = 0
        var overdue = 0
        var dueSoon = 0
        var onTrack = 0
        var closedOnTime = 0
        var closedLate = 0
        for (info in items) {
            when (info.state) {
                SlaState.OVERDUE -> { trackedOpen++; overdue++ }
                SlaState.DUE_SOON -> { trackedOpen++; dueSoon++ }
                SlaState.ON_TRACK -> { trackedOpen++; onTrack++ }
                SlaState.CLOSED_ON_TIME -> closedOnTime++
                SlaState.CLOSED_LATE -> closedLate++
                else -> {}
            }
        }
        return SlaSummary(trackedOpen, overdue, dueSoon, onTrack, closedOnTime, closedLate)
    }
}
