package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SeedData
import com.example.domain.SlaEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.model.Permission
import com.example.model.ScanResult
import com.example.ui.components.*
import com.example.viewmodel.AdwaViewModel

@Composable
fun FindingsScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val orgById = remember(data.organizations) { data.organizations.associateBy { it.id } }
    val ruleByCode = remember { SeedData.ALL_RULES.associateBy { it.code } }
    val completedScans = vm.visibleScans().filter { it.status == "complete" }

    val allFindings = remember(completedScans) {
        completedScans.flatMap { sc ->
            sc.results.filter { it.isFinding }.map { res -> sc to res }
        }.sortedByDescending { it.second.riskRank }
    }

    val slaPairs = remember(allFindings) {
        allFindings.map { (sc, res) ->
            val rule = ruleByCode[res.ruleCode]
            val sla = SlaEngine.slaInfo(res.status, res.dueAtEpochMs, res.remediatedAtEpochMs, rule?.severity)
            Triple(sc, res, sla)
        }
    }
    val slaSummary = remember(slaPairs) { SlaEngine.summarizeSla(slaPairs.map { it.third }) }

    var statusFilter by remember { mutableStateOf("ALL") }
    var slaFilter by remember { mutableStateOf("ALL") }
    var waiveTarget by remember { mutableStateOf<Pair<String, ScanResult>?>(null) }

    val filtered = remember(slaPairs, statusFilter, slaFilter) {
        slaPairs.filter { (_, res, sla) ->
            (statusFilter == "ALL" || res.status == statusFilter) &&
                (slaFilter == "ALL" ||
                    (slaFilter == "OVERDUE" && sla.state == SlaEngine.SlaState.OVERDUE) ||
                    (slaFilter == "DUE_SOON" && sla.state == SlaEngine.SlaState.DUE_SOON) ||
                    (slaFilter == "ON_TRACK" && sla.state == SlaEngine.SlaState.ON_TRACK))
        }
    }

    val canManage = vm.can(Permission.FINDINGS_MANAGE_ALL) || vm.can(Permission.FINDINGS_MANAGE_ORG)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(title = AppStrings.t(lang, "findTitle"), subtitle = "${AppStrings.t(lang, "findSub")} · ${AppStrings.t(lang, "slaPolicy")}")
        }

        // SLA Summary Strip
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Tracked Open", "${slaSummary.trackedOpen}", null, Icons.Default.Schedule, modifier = Modifier.weight(1f))
                KpiCard("Overdue", "${slaSummary.overdue}", "${slaSummary.dueSoon} due <=7d", accentColor = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                KpiCard("Closed", "${slaSummary.closedOnTime + slaSummary.closedLate}", "${slaSummary.closedOnTime} on time", accentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            }
        }

        // Status & SLA filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        "ALL" to "All (${slaPairs.size})",
                        "open" to AppStrings.t(lang, "stOpen"),
                        "in_progress" to AppStrings.t(lang, "stProgress"),
                        "remediated" to AppStrings.t(lang, "stRemediated"),
                        "waived" to AppStrings.t(lang, "stWaived"),
                    ).forEach { (k, label) ->
                        FilterChip(selected = statusFilter == k, onClick = { statusFilter = k }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("ALL" to "Any SLA", "OVERDUE" to AppStrings.t(lang, "slaOverdue"), "DUE_SOON" to AppStrings.t(lang, "slaDueSoon"), "ON_TRACK" to AppStrings.t(lang, "slaOnTrack")).forEach { (k, label) ->
                        FilterChip(selected = slaFilter == k, onClick = { slaFilter = k }, label = { Text(label) })
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(Icons.Default.CheckCircle, "No findings match filters", "Adjust status or SLA filters to inspect other obligations.")
            }
        } else {
            items(filtered, key = { it.second.id }) { (scan, res, sla) ->
                val rule = ruleByCode[res.ruleCode]
                val orgName = orgById[scan.organizationId]?.name ?: scan.organizationId

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().testTag("finding_card_${res.id}"),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(res.ruleCode, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            if (rule != null) SeverityBadge(rule.severity, lang)
                            Spacer(Modifier.weight(1f))
                            val (slaText, slaBg, slaFg) = when (sla.state) {
                                SlaEngine.SlaState.OVERDUE -> Triple("${sla.daysOverdue}d overdue", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                SlaEngine.SlaState.DUE_SOON -> Triple("${sla.daysRemaining}d left", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                SlaEngine.SlaState.ON_TRACK -> Triple("${sla.daysRemaining}d left", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                SlaEngine.SlaState.CLOSED_ON_TIME -> Triple(AppStrings.t(lang, "slaClosedOnTime"), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                SlaEngine.SlaState.CLOSED_LATE -> Triple("${sla.daysOverdue}d late", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                SlaEngine.SlaState.WAIVED -> Triple(AppStrings.t(lang, "slaWaived"), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                SlaEngine.SlaState.NOT_TRACKED -> Triple("—", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(slaText, slaBg, slaFg)
                        }
                        Text(
                            text = rule?.let { if (lang == Lang.EN) it.titleEn else it.titleAm } ?: res.ruleCode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("$orgName · ${scan.frameworkCode} · Risk ${res.riskRank} · Status: ${res.status.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (rule != null) {
                            Text(if (lang == Lang.EN) rule.remediationEn else rule.remediationAm, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!res.waiverNote.isNullOrBlank()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                                Text("Waiver rationale: ${res.waiverNote}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                            }
                        }
                        if (canManage) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                if (res.status != "in_progress") {
                                    OutlinedButton(onClick = { vm.updateFindingStatus(scan.id, res.id, "in_progress") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(AppStrings.t(lang, "stProgress"))
                                    }
                                }
                                if (res.status != "remediated") {
                                    Button(onClick = { vm.updateFindingStatus(scan.id, res.id, "remediated") }, modifier = Modifier.heightIn(min = 48.dp).testTag("remediate_${res.id}")) {
                                        Text(AppStrings.t(lang, "stRemediated"))
                                    }
                                }
                                if (res.status != "waived") {
                                    OutlinedButton(onClick = { waiveTarget = scan.id to res }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(AppStrings.t(lang, "stWaived"))
                                    }
                                }
                                if (res.status != "open") {
                                    TextButton(onClick = { vm.updateFindingStatus(scan.id, res.id, "open") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("Reopen")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    waiveTarget?.let { (scanId, res) ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { waiveTarget = null },
            title = { Text("${AppStrings.t(lang, "waiveTitle")} (${res.ruleCode})") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Justification / compensating control (>=5 chars)") },
                    modifier = Modifier.fillMaxWidth().testTag("waiver_note_input"),
                )
            },
            confirmButton = {
                Button(
                    enabled = note.trim().length >= 5,
                    onClick = {
                        vm.updateFindingStatus(scanId, res.id, "waived", note)
                        waiveTarget = null
                    },
                    modifier = Modifier.testTag("confirm_waiver_button"),
                ) {
                    Text(AppStrings.t(lang, "save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { waiveTarget = null }) { Text(AppStrings.t(lang, "cancel")) }
            },
        )
    }
}
