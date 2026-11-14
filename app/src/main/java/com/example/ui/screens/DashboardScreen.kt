package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AwarenessModulesData
import com.example.data.SeedData
import com.example.domain.ComplianceEngine
import com.example.domain.EnterpriseEngines
import com.example.domain.SlaEngine
import com.example.i18n.AppStrings
import com.example.model.IncidentStatus
import com.example.model.Lang
import com.example.model.Permission
import com.example.ui.components.*
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMonoFamily
import com.example.ui.theme.CyberNeonEmerald
import com.example.ui.theme.EmeraldDark
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.Screen
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: AdwaViewModel) {
    val context = LocalContext.current
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

    val passingMonitors = data.controlMonitors.count { it.status == "PASS" }
    val failingMonitors = data.controlMonitors.count { it.status == "FAIL" || it.status == "WARN" }
    val totalMonitors = data.controlMonitors.size
    val activeIncidents = vm.visibleIncidents().count { it.status != IncidentStatus.CLOSED }
    val openRisks = data.riskRegister.count { it.status == "OPEN" || it.status == "IN_PROGRESS" }
    val satisfiedGates = data.expansionGates.count { it.status == "SATISFIED" }
    val totalGates = data.expansionGates.size
    val humanRisk = remember(data.awarenessLearners, data.awarenessCampaigns) {
        EnterpriseEngines.computeHumanRisk(
            data.awarenessLearners,
            data.awarenessCampaigns,
            AwarenessModulesData.MODULES.size,
        )
    }

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

        // Executive Command Center Hero Card (Cyber Mesh + Obsidian-Emerald Scrim)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(CyberNeonEmerald.copy(alpha = 0.65f), CyberCyan.copy(alpha = 0.45f)),
                            ),
                        ),
                        RoundedCornerShape(20.dp),
                    )
                    .testTag("dash_command_hero_card"),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cyber_hero_banner),
                    contentDescription = "Adwa Cyber Command Mesh",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF040911).copy(alpha = 0.86f),
                                    EmeraldDark.copy(alpha = 0.82f),
                                ),
                            )
                        ),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(18.dp),
                ) {
                    Text(
                        "SOC TELEMETRY LIVE · TEE/TLS · SHA-256 VAULT",
                        fontFamily = CyberMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            StatusPill(
                                label = AppStrings.pick(lang, "EXECUTIVE COMMAND CENTER", "የአመራር የቁጥጥር ማዕከል"),
                                containerColor = CyberNeonEmerald.copy(alpha = 0.18f),
                                contentColor = CyberNeonEmerald,
                            )
                            Text(
                                "${avgScore.roundToInt()}% ${AppStrings.pick(lang, "Enterprise Readiness", "የተቋም ዝግጁነት")}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                AppStrings.pick(
                                    lang,
                                    "$passingMonitors/$totalMonitors CCM checks passing · $openRisks open risks · ${orgs.size} institutions",
                                    "$passingMonitors/$totalMonitors ቀጥታ ፍተሻዎች · $openRisks ክፍት ስጋቶች",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, CyberNeonEmerald.copy(alpha = 0.55f)),
                        ) {
                            Box(modifier = Modifier.padding(8.dp)) {
                                ScoreRing(score = avgScore, size = 66.dp, strokeWidth = 6.dp)
                            }
                        }
                    }

                    // Instant Drill-Down Status Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        CommandDrillPill(
                            label = if (failingMonitors > 0) "CCM Alert: $failingMonitors" else "CCM: $passingMonitors/$totalMonitors OK",
                            tag = "hero_drill_ccm",
                            highlightAlert = failingMonitors > 0,
                            onClick = { vm.openMonitorsTab(0) },
                        )
                        CommandDrillPill(
                            label = "SLA Overdue: ${slaSummary.overdue}",
                            tag = "hero_drill_sla",
                            highlightAlert = slaSummary.overdue > 0,
                            onClick = { vm.navigate(Screen.FINDINGS) },
                        )
                        CommandDrillPill(
                            label = "Active SOC: $activeIncidents",
                            tag = "hero_drill_soc",
                            highlightAlert = activeIncidents > 0,
                            onClick = { vm.navigate(Screen.SOC) },
                        )
                        if (vm.can(Permission.AWARENESS_MANAGE)) {
                            CommandDrillPill(
                                label = "Human Risk: ${humanRisk.overallRiskScore}/100",
                                tag = "hero_drill_hrs",
                                highlightAlert = humanRisk.overallRiskScore >= 55,
                                onClick = { vm.navigate(Screen.AWARENESS) },
                            )
                        }
                        if (vm.can(Permission.GOVERNANCE_VIEW) || vm.can(Permission.CII_VIEW_OWN)) {
                            CommandDrillPill(
                                label = "Track C Gates: $satisfiedGates/$totalGates",
                                tag = "hero_drill_gates",
                                highlightAlert = false,
                                onClick = { vm.navigate(Screen.TRACK_C) },
                            )
                        }
                    }
                }
            }
        }

        // Quick Command Strip
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = {
                        vm.openMonitorsTab(0)
                        vm.runPostureSweep(context)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dash_quick_sweep"),
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.pick(lang, "Sweep", "ፍተሻ"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = { vm.openTrustAiTab(1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dash_quick_ai_studio"),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.pick(lang, "AI Studio", "AI ስቱዲዮ"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = { vm.openTrustAiTab(0) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dash_quick_trust"),
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.pick(lang, "Trust Hub", "እምነት"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Card(
                onClick = { vm.openTrustAiTab(2) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("dash_open_gemini_card"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Gemini AI Co-Pilot",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            AppStrings.pick(lang, "Gemini AI Compliance Co-Pilot", "Gemini AI የቅጥ አማካሪ"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            AppStrings.pick(
                                lang,
                                "Analyze PD-1321 gaps, draft policies & inspect visual evidence with Gemini 3.8 Flash",
                                "የPD-1321 ክፍተቶችን፣ ፖሊሲዎችን እና ምስሎችን በGemini 3.8 Flash ይተንትኑ",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open Gemini AI Co-Pilot")
                }
            }
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

@Composable
private fun CommandDrillPill(
    label: String,
    tag: String,
    highlightAlert: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (highlightAlert) Color(0xFFFEF3C7) else Color.White.copy(alpha = 0.22f),
        contentColor = if (highlightAlert) Color(0xFF78350F) else Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

