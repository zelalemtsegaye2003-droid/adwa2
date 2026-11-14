package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ComplianceScan
import com.example.model.ControlMonitor
import com.example.model.RiskRegisterEntry

@Entity(tableName = "risk_register")
data class RiskRegisterEntity(
    @PrimaryKey val id: String,
    val code: String,
    val titleEn: String,
    val titleAm: String,
    val description: String,
    val frameworks: List<String>,
    val linkedRuleCodes: List<String>,
    val likelihood: Int,
    val impact: Int,
    val residualScore: Int,
    val treatment: String,
    val status: String,
    val ownerName: String,
    val dueAtEpochMs: Long?,
    val sourceMonitorId: String?,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "compliance_snapshots")
data class ComplianceSnapshotEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val frameworkCode: String,
    val name: String,
    val status: String,
    val overallScore: Double?,
    val findingsCount: Int,
    val criticalFindings: Int,
    val durationMinutes: Double?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
)

@Entity(tableName = "control_monitors")
data class ControlMonitorEntity(
    @PrimaryKey val id: String,
    val code: String,
    val titleEn: String,
    val titleAm: String,
    val category: String,
    val sourceType: String,
    val targetHint: String,
    val mappedRuleCodes: List<String>,
    val status: String,
    val detail: String,
    val lastCheckedAtEpochMs: Long?,
)

fun RiskRegisterEntity.toDomain(): RiskRegisterEntry = RiskRegisterEntry(
    id = id,
    code = code,
    titleEn = titleEn,
    titleAm = titleAm,
    description = description,
    frameworks = frameworks,
    linkedRuleCodes = linkedRuleCodes,
    likelihood = likelihood,
    impact = impact,
    residualScore = residualScore,
    treatment = treatment,
    status = status,
    ownerName = ownerName,
    dueAtEpochMs = dueAtEpochMs,
    sourceMonitorId = sourceMonitorId,
)

fun RiskRegisterEntry.toEntity(updatedAtEpochMs: Long = System.currentTimeMillis()): RiskRegisterEntity = RiskRegisterEntity(
    id = id,
    code = code,
    titleEn = titleEn,
    titleAm = titleAm,
    description = description,
    frameworks = frameworks,
    linkedRuleCodes = linkedRuleCodes,
    likelihood = likelihood,
    impact = impact,
    residualScore = residualScore,
    treatment = treatment,
    status = status,
    ownerName = ownerName,
    dueAtEpochMs = dueAtEpochMs,
    sourceMonitorId = sourceMonitorId,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun ComplianceScan.toSnapshotEntity(): ComplianceSnapshotEntity = ComplianceSnapshotEntity(
    id = id,
    organizationId = organizationId,
    frameworkCode = frameworkCode,
    name = name,
    status = status,
    overallScore = overallScore,
    findingsCount = findingsCount,
    criticalFindings = criticalFindings,
    durationMinutes = durationMinutes,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = completedAtEpochMs,
)

fun ControlMonitorEntity.toDomain(): ControlMonitor = ControlMonitor(
    id = id,
    code = code,
    titleEn = titleEn,
    titleAm = titleAm,
    category = category,
    sourceType = sourceType,
    targetHint = targetHint,
    mappedRuleCodes = mappedRuleCodes,
    status = status,
    detail = detail,
    lastCheckedAtEpochMs = lastCheckedAtEpochMs,
)

fun ControlMonitor.toEntity(): ControlMonitorEntity = ControlMonitorEntity(
    id = id,
    code = code,
    titleEn = titleEn,
    titleAm = titleAm,
    category = category,
    sourceType = sourceType,
    targetHint = targetHint,
    mappedRuleCodes = mappedRuleCodes,
    status = status,
    detail = detail,
    lastCheckedAtEpochMs = lastCheckedAtEpochMs,
)
