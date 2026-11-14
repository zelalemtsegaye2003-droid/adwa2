package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SeedData
import com.example.domain.EnterpriseEngines
import com.example.i18n.AppStrings
import com.example.model.ControlMonitor
import com.example.model.Lang
import com.example.model.RiskRegisterEntry
import com.example.ui.components.*
import com.example.viewmodel.AdwaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorsScreen(vm: AdwaViewModel) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val roomRisks by vm.roomRiskRegister.collectAsStateWithLifecycle()
    val lang = ui.lang

    var showAddRiskDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SectionHeader(
                title = AppStrings.pick(lang, "Monitors, Mapping & Risk", "ክትትል፣ መስቀለኛ እና ስጋት"),
                subtitle = AppStrings.pick(
                    lang,
                    "Live Android & TLS probes · Cross-framework mapping · Risk register",
                    "ቀጥታ የስልክ እና TLS ፍተሻዎች · የስጋት መዝገብ",
                ),
            )
            TabRow(selectedTabIndex = ui.monitorsTab) {
                Tab(
                    selected = ui.monitorsTab == 0,
                    onClick = { vm.openMonitorsTab(0) },
                    text = { Text(AppStrings.pick(lang, "Live CCM", "ቀጥታ ክትትል"), maxLines = 1) },
                    modifier = Modifier.testTag("monitors_tab_ccm"),
                )
                Tab(
                    selected = ui.monitorsTab == 1,
                    onClick = { vm.openMonitorsTab(1) },
                    text = { Text(AppStrings.pick(lang, "Cross-Map", "መስቀለኛ"), maxLines = 1) },
                    modifier = Modifier.testTag("monitors_tab_crossmap"),
                )
                Tab(
                    selected = ui.monitorsTab == 2,
                    onClick = { vm.openMonitorsTab(2) },
                    text = { Text("${AppStrings.pick(lang, "Risks", "ስጋቶች")} (${roomRisks.size})", maxLines = 1) },
                    modifier = Modifier.testTag("monitors_tab_risks"),
                )
            }
        }

        when (ui.monitorsTab) {
            0 -> LiveCcmTab(
                vm = vm,
                monitors = data.controlMonitors,
                probeUrl = ui.probeUrl,
                isSweeping = ui.isSweepingMonitors,
                lastRunSummary = data.monitorRuns.firstOrNull()?.summary,
                lang = lang,
                onRunSweep = { url -> vm.runPostureSweep(context, url) },
            )
            1 -> CrossFrameworkTab(lang = lang)
            else -> RiskRegisterTab(
                vm = vm,
                risks = roomRisks,
                lang = lang,
                onOpenAdd = { showAddRiskDialog = true },
            )
        }
    }

    if (showAddRiskDialog) {
        AddRiskDialog(
            lang = lang,
            onDismiss = { showAddRiskDialog = false },
            onSave = { title, desc, fws, likelihood, impact, treatment ->
                vm.addCustomRisk(title, desc, fws, emptyList(), likelihood, impact, treatment)
                showAddRiskDialog = false
            },
        )
    }
}

@Composable
private fun LiveCcmTab(
    vm: AdwaViewModel,
    monitors: List<ControlMonitor>,
    probeUrl: String,
    isSweeping: Boolean,
    lastRunSummary: String?,
    lang: Lang,
    onRunSweep: (String) -> Unit,
) {
    var urlInput by remember(probeUrl) { mutableStateOf(probeUrl) }
    val passCount = monitors.count { it.status == "PASS" }
    val warnCount = monitors.count { it.status == "WARN" }
    val failCount = monitors.count { it.status == "FAIL" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            AppStrings.pick(lang, "On-Device & Live TLS Sweep", "የስልክ እና ቀጥታ TLS ፍተሻ"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        StatusPill(
                            label = "$passCount Pass · $warnCount Warn · $failCount Fail",
                            containerColor = if (failCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (failCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it; vm.setProbeUrl(it) },
                        label = { Text(AppStrings.pick(lang, "Live HTTPS Target URL", "የቀጥታ HTTPS ኢላማ አድራሻ")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ccm_probe_url_input"),
                    )
                    Button(
                        onClick = { onRunSweep(urlInput) },
                        enabled = !isSweeping,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ccm_run_sweep_button"),
                    ) {
                        if (isSweeping) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.pick(lang, "Probing device & TLS endpoint…", "በመመርመር ላይ…"))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.pick(lang, "Run Live Posture Sweep", "ቀጥታ ፍተሻ አስጀምር"))
                        }
                    }
                    if (!lastRunSummary.isNullOrBlank()) {
                        Text(
                            "Last Sweep: $lastRunSummary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(monitors, key = { it.id }) { m ->
            val (bg, fg) = when (m.status) {
                "PASS" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                "FAIL" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                "WARN" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("monitor_card_${m.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(m.status, bg, fg)
                        StatusPill(m.sourceType, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.weight(1f))
                        Text(m.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        if (lang == Lang.EN) m.titleEn else m.titleAm,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(m.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Mapped: ${m.mappedRuleCodes.joinToString(", ")} · Checked: ${formatDateTime(m.lastCheckedAtEpochMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (m.status == "FAIL" || m.status == "WARN") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.createRiskFromMonitor(m) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mon_log_risk_${m.id}"),
                            ) {
                                Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(AppStrings.pick(lang, "Log Risk", "ስጋት መዝግብ"))
                            }
                            FilledTonalButton(
                                onClick = { vm.openTrustAiTab(1) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("mon_fix_ai_${m.id}"),
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(AppStrings.pick(lang, "Fix w/ AI Policy", "በAI ፖሊሲ አስተካክል"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrossFrameworkTab(lang: Lang) {
    var expandedId by remember { mutableStateOf<String?>(EnterpriseEngines.CANONICAL_GROUPS.first().id) }
    val allRules = remember { SeedData.ALL_RULES }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                    Text(
                        AppStrings.pick(lang, "Map Once, Comply Everywhere", "አንዴ ያጣምሩ፣ በሁሉም ሕግ ያሟሉ"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        AppStrings.pick(
                            lang,
                            "Unified controls satisfy PD-1321, NBE-IT, Procl. 1426/2026 SLAs, and ACFS simultaneously.",
                            "አንድ ቁጥጥር PD-1321፣ NBE-IT፣ Procl. 1426/2026 እና ACFSን በጋራ ያሟላል።",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(EnterpriseEngines.CANONICAL_GROUPS, key = { it.id }) { grp ->
            val isExpanded = expandedId == grp.id
            val resolved = remember(grp.id) { EnterpriseEngines.resolveRulesForGroup(grp, allRules) }
            Card(
                onClick = { expandedId = if (isExpanded) null else grp.id },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("crossmap_group_${grp.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(grp.id, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        StatusPill("SLA ${grp.slaDays}d", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.weight(1f))
                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle control mapping")
                    }
                    Text(
                        if (lang == Lang.EN) grp.domainEn else grp.domainAm,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(grp.descriptionEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "PD-1321 (${grp.pd1321Codes.size}) · NBE-IT (${grp.nbeItCodes.size}) · ${grp.acfsRef}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AnimatedVisibility(visible = isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            HorizontalDivider()
                            resolved.forEach { rule ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(rule.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (lang == Lang.EN) rule.titleEn else rule.titleAm,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SeverityBadge(rule.severity, lang)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskRegisterTab(
    vm: AdwaViewModel,
    risks: List<RiskRegisterEntry>,
    lang: Lang,
    onOpenAdd: () -> Unit,
) {
    val openCount = risks.count { it.status == "OPEN" || it.status == "IN_PROGRESS" }
    val criticalRisks = risks.count { it.residualScore >= 15 && it.status != "MITIGATED" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Open Risks", "$openCount", "$criticalRisks critical/high (>=15)", Icons.Default.Warning, modifier = Modifier.weight(1f))
                KpiCard("Mitigated", "${risks.count { it.status == "MITIGATED" }}", "Verified treatments", Icons.Default.Verified, modifier = Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = onOpenAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("risk_add_button"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(AppStrings.pick(lang, "Add Risk Entry", "አዲስ ስጋት ጨምር"))
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    AppStrings.pick(
                        lang,
                        "Stored locally on-device via Room SQLite (${risks.size} risk records)",
                        "በስልኩ ውስጥ በRoom SQLite የተቀመጠ (${risks.size} መዝገቦች)",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(risks, key = { it.id }) { rk ->
            val (bg, fg) = when {
                rk.status == "MITIGATED" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                rk.residualScore >= 15 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                rk.residualScore >= 10 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("risk_card_${rk.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill("${rk.code} · Score ${rk.residualScore}/25", bg, fg)
                        StatusPill(rk.status, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.weight(1f))
                        Text(rk.treatment, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(if (lang == Lang.EN) rk.titleEn else rk.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(rk.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "L${rk.likelihood}xI${rk.impact} · ${rk.frameworks.joinToString(", ")} · Owner: ${rk.ownerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        if (rk.status != "IN_PROGRESS" && rk.status != "MITIGATED") {
                            OutlinedButton(
                                onClick = { vm.updateRiskStatus(rk.id, "IN_PROGRESS") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Start Mitigation") }
                        }
                        if (rk.status != "MITIGATED") {
                            Button(
                                onClick = { vm.updateRiskStatus(rk.id, "MITIGATED") },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("risk_mitigate_${rk.id}"),
                            ) { Text("Mark Mitigated") }
                        }
                        if (rk.status == "OPEN") {
                            TextButton(
                                onClick = { vm.updateRiskStatus(rk.id, "ACCEPTED", "ACCEPT") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Accept") }
                        }
                        IconButton(
                            onClick = { vm.deleteRiskEntry(rk.id) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("risk_delete_${rk.id}"),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete risk ${rk.code}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRiskDialog(
    lang: Lang,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, Int, Int, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var likelihood by remember { mutableIntStateOf(3) }
    var impact by remember { mutableIntStateOf(4) }
    var treatment by remember { mutableStateOf("MITIGATE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.pick(lang, "Register Security Risk", "የደህንነት ስጋት መዝግብ")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description & impact") }, minLines = 2)
                Text("Likelihood ($likelihood/5) · Impact ($impact/5) -> Score ${likelihood * impact}", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(selected = likelihood == n, onClick = { likelihood = n }, label = { Text("L$n") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(selected = impact == n, onClick = { impact = n }, label = { Text("I$n") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MITIGATE", "ACCEPT", "TRANSFER").forEach { tr ->
                        FilterChip(selected = treatment == tr, onClick = { treatment = tr }, label = { Text(tr) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, desc, listOf("PD-1321", "NBE-IT"), likelihood, impact, treatment) }) {
                Text(AppStrings.t(lang, "save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.t(lang, "cancel")) }
        },
    )
}
