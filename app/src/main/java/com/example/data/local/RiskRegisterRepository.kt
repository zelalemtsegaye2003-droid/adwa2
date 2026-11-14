package com.example.data.local

import com.example.model.ComplianceScan
import com.example.model.ControlMonitor
import com.example.model.RiskRegisterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RiskRegisterRepository(
    private val riskRegisterDao: RiskRegisterDao,
    private val complianceDataDao: ComplianceDataDao,
) {
    val allRisks: Flow<List<RiskRegisterEntry>> =
        riskRegisterDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    val openHighRisks: Flow<List<RiskRegisterEntry>> =
        riskRegisterDao.observeOpenHighRisks(12).map { rows -> rows.map { it.toDomain() } }

    val complianceSnapshots: Flow<List<ComplianceSnapshotEntity>> =
        complianceDataDao.observeSnapshots()

    val controlMonitors: Flow<List<ControlMonitor>> =
        complianceDataDao.observeMonitors().map { rows -> rows.map { it.toDomain() } }

    suspend fun seedIfEmpty(
        risks: List<RiskRegisterEntry>,
        scans: List<ComplianceScan>,
        monitors: List<ControlMonitor>,
    ) {
        if (riskRegisterDao.count() == 0 && risks.isNotEmpty()) {
            riskRegisterDao.upsertAll(risks.map { it.toEntity() })
        }
        if (complianceDataDao.snapshotCount() == 0 && scans.isNotEmpty()) {
            complianceDataDao.upsertSnapshots(scans.map { it.toSnapshotEntity() })
        }
        if (complianceDataDao.monitorCount() == 0 && monitors.isNotEmpty()) {
            complianceDataDao.upsertMonitors(monitors.map { it.toEntity() })
        }
    }

    suspend fun replaceAll(
        risks: List<RiskRegisterEntry>,
        scans: List<ComplianceScan>,
        monitors: List<ControlMonitor>,
    ) {
        riskRegisterDao.clearAll()
        if (risks.isNotEmpty()) {
            riskRegisterDao.upsertAll(risks.map { it.toEntity() })
        }
        complianceDataDao.clearSnapshots()
        if (scans.isNotEmpty()) {
            complianceDataDao.upsertSnapshots(scans.map { it.toSnapshotEntity() })
        }
        complianceDataDao.clearMonitors()
        if (monitors.isNotEmpty()) {
            complianceDataDao.upsertMonitors(monitors.map { it.toEntity() })
        }
    }

    suspend fun upsertRisk(entry: RiskRegisterEntry) {
        riskRegisterDao.upsert(entry.toEntity())
    }

    suspend fun updateRiskStatus(id: String, newStatus: String, treatment: String? = null) {
        val existing = riskRegisterDao.getById(id) ?: return
        val nextTreatment = treatment ?: existing.treatment
        val nextScore = if (newStatus == "MITIGATED") maxOf(1, existing.residualScore / 2) else existing.residualScore
        riskRegisterDao.updateStatusAndScore(
            id = id,
            status = newStatus,
            treatment = nextTreatment,
            residualScore = nextScore,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun deleteRiskById(id: String) {
        riskRegisterDao.deleteById(id)
    }

    suspend fun saveMonitors(monitors: List<ControlMonitor>) {
        complianceDataDao.upsertMonitors(monitors.map { it.toEntity() })
    }

    suspend fun saveSnapshot(scan: ComplianceScan) {
        complianceDataDao.upsertSnapshots(listOf(scan.toSnapshotEntity()))
    }

    suspend fun deleteSnapshot(scanId: String) {
        complianceDataDao.deleteSnapshotById(scanId)
    }
}
