package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SeedData
import com.example.domain.ComplianceEngine
import com.example.domain.SlaEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.model.Permission
import com.example.ui.components.*
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.Screen
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val orgs = vm.visibleOrganizations()
    val scans = vm.visibleScans()
    val completedScans = remember(scans) { scans.filter { it.status == "complete" }.sortedByDescending { it.completedAtEpochMs ?: 0L } }
    val draftScans = remember(scans) { scans.filter { it.status == "in_progress" } }
    val ruleByCode = remember { SeedData.ALL_RULES.associateBy { it.code } }
    val orgById = remember(orgs) { orgs.associateBy { it.id } }

    val openFindings = remember(completedScans) {
        completedScans.flatMap { sc -> sc.results.filter { it.isFinding && it.status != "remediated" && it.status != "waived" } }
    }
    val slaInfos = remember(completedScans) {
        completedScans.flatMap { sc ->
            sc.results.filter { it.isFinding }.map { res ->
                SlaEngine.slaInfo(res.status, res.dueAtEpochMs, res.remediatedAtEpochMs, ruleByCode[res.ruleCode]?.severity)
            }
        }
    }
    val slaSummary = remember(slaInfos) { SlaEngine.summarizeSla(slaInfos) }

    val avgScore = if (completedScans.isNotEmpty()) {
        (completedScans.mapNotNull { it.overallScore }.average() * 10.0).roundToInt() / 10.0
    } else 0.0
    val avgMinutes = if (completedScans.isNotEmpty()) {
        (completedScans.mapNotNull { it.durationMinutes }.average() * 10.0).roundToInt() / 10.0
    } else 34.0
    val rq1 = remember(avgMinutes) { ComplianceEngine.auditTimeComparison(avgMinutes) }

    val critCount = openFindings.count { ruleByCode[it.ruleCode]?.severity == "critical" }
    val highCount = openFindings.count { ruleByCode[it.ruleCode]?.severity == "high" }
    val medCount = openFindings.count { ruleByCode[it.ruleCode]?.severity == "medium" }

    val latestPd = completedScans.firstOrNull { it.frameworkCode == "PD-1321" }
    val latestNbe = completedScans.firstOrNull { it.frameworkCode == "NBE-IT" }

    val topArticleRisks = remember(openFindings) {
        openFindings.groupBy { ruleByCode[it.ruleCode]?.article ?: "Other" }
            .mapValues { (_, v) -> v.size }
            .entries.sortedByDescending { it.value }
            .take(6)
            .map { it.key to it.value }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "dashTitle"),
                subtitle = AppStrings.t(lang, "dashSub"),
                action = {
                    if (vm.can(Permission.SCANS_RUN_ORG) || vm.can(Permission.SCANS_RUN_ANY_ORG)) {
                        Button(
                            onClick = { vm.navigate(Screen.SCANS) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("dash_new_scan_button"),
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.t(lang, "newScan"))
                        }
                    }
                },
            )
        }

        // SLA Breach Alert
        if (slaSummary.overdue > 0 || slaSummary.dueSoon > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "SLA Alert", tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                AppStrings.pick(lang, "Procl. 1426/2026 SLA Attention Required", "የSLA ጊዜ ገደብ ማስጠንቀቂያ"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "${slaSummary.overdue} overdue · ${slaSummary.dueSoon} due within 7 days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        TextButton(
                            onClick = { vm.navigate(Screen.FINDINGS) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("dash_sla_findings_button"),
                        ) {
                            Text(AppStrings.pick(lang, "Review", "ይመልከቱ"))
                        }
                    }
                }
            }
        }

        // In-progress draft scan banner
        if (draftScans.isNotEmpty()) {
            val draft = draftScans.first()
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(Icons.Default.PendingActions, contentDescription = "Draft Scan", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t(lang, "inProgressScans"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(draft.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { vm.openScan(draft.id) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("dash_resume_draft_button"),
                        ) {
                            Text(AppStrings.t(lang, "resumeScan"))
                        }
                    }
                }
            }
        }

        // 2x2 KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard(
                        title = AppStrings.t(lang, "avgScore"),
                        value = "${avgScore}%",
                        subtitle = "${completedScans.size} completed scans",
                        icon = Icons.Default.Verified,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = AppStrings.t(lang, "orgsMonitored"),
                        value = "${orgs.size}",
                        subtitle = AppStrings.pick(lang, "Pilot institutions", "ተቋማት"),
                        icon = Icons.Default.Apartment,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard(
                        title = AppStrings.t(lang, "rulesEnforced"),
                        value = "${SeedData.ALL_RULES.size}",
                        subtitle = "47 PD-1321 + 30 NBE-IT",
                        icon = Icons.Default.Gavel,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = AppStrings.t(lang, "auditDuration"),
                        value = "${rq1.engineMinutes}m",
                        subtitle = "RQ1: ${rq1.reductionPercent}% faster vs manual",
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Dual Framework Summary Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SeedData.FRAMEWORKS.forEach { fw ->
                    val fwScans = completedScans.filter { it.frameworkCode == fw.code }
                    val fwAvg = if (fwScans.isNotEmpty()) fwScans.mapNotNull { it.overallScore }.average() else 0.0
                    val fwFindings = fwScans.sumOf { it.findingsCount }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(14.dp),
                        ) {
                            ScoreRing(score = fwAvg, size = 68.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                StatusPill(fw.instrumentNo, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(if (lang == Lang.EN) fw.name else fw.nameAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${fwScans.size} scan(s) · $fwFindings open findings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Findings Donut Chart
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                    Text(AppStrings.t(lang, "findingsSeverity"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FindingsDonutChart(critical = critCount, high = highCount, medium = medCount)
                }
            }
        }

        // Pillar Performance Radar
        val radarScan = latestPd ?: latestNbe
        if (radarScan != null && radarScan.pillarScores.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                        Text("${AppStrings.t(lang, "pillarPerf")} (${radarScan.frameworkCode})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        PillarRadarChart(scores = radarScan.pillarScores, lang = lang)
                    }
                }
            }
        }

        // Article Risk Heatmap
        if (topArticleRisks.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                        Text(AppStrings.t(lang, "articleRisk"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalBarList(items = topArticleRisks)
                    }
                }
            }
        }

        // Recent Completed Scans
        item {
            Text(AppStrings.t(lang, "recentScans"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(completedScans.take(6), key = { it.id }) { scan ->
            val orgName = orgById[scan.organizationId]?.name ?: scan.organizationId
            Card(
                onClick = { vm.openExecutiveReport(scan.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("recent_scan_card_${scan.id}"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    ScoreRing(score = scan.overallScore ?: 0.0, size = 56.dp, strokeWidth = 5.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(scan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$orgName · ${scan.frameworkCode} · ${formatDate(scan.completedAtEpochMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${scan.findingsCount} open findings · ${scan.durationMinutes ?: 30.0}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open executive report")
                }
            }
        }
    }
}
