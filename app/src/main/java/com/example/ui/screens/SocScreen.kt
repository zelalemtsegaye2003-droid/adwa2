package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.SocEngine
import com.example.i18n.AppStrings
import com.example.model.IncidentStatus
import com.example.model.Lang
import com.example.model.Permission
import com.example.ui.components.*
import com.example.viewmodel.AdwaViewModel

@Composable
fun SocScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    var tab by remember { mutableIntStateOf(0) } // 0=Overview, 1=Incidents, 2=Threats
    var showCreateIncident by remember { mutableStateOf(false) }
    var showContributeIndicator by remember { mutableStateOf(false) }
    var noteIncidentId by remember { mutableStateOf<String?>(null) }

    val incidents = vm.visibleIncidents()
    val indicators = vm.visibleIndicators()
    val orgs = vm.visibleOrganizations()
    val orgById = remember(data.organizations) { data.organizations.associateBy { it.id } }

    val latency = remember(incidents) { SocEngine.notificationLatency(incidents) }
    val lifecycle = remember(incidents) { SocEngine.lifecycleTimes(incidents) }
    val primarySector = orgs.firstOrNull()?.sector ?: "Banking"
    val forecasts = remember(primarySector, indicators, incidents) {
        SocEngine.predictThreats(primarySector, indicators.filter { it.isActive }, incidents).take(6)
    }

    val canManageSoc = vm.can(Permission.SOC_MANAGE_ALL) || vm.can(Permission.SOC_MANAGE_ORG)
    val canSync = vm.can(Permission.THREATS_SYNC)
    val canContribute = vm.can(Permission.THREATS_CONTRIBUTE)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = AppStrings.t(lang, "socTitle"), subtitle = AppStrings.t(lang, "socSub"))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(AppStrings.t(lang, "socOverview")) }, modifier = Modifier.testTag("soc_tab_overview"))
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("${AppStrings.t(lang, "socIncidents")} (${incidents.size})") }, modifier = Modifier.testTag("soc_tab_incidents"))
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("${AppStrings.t(lang, "socThreats")} (${indicators.size})") }, modifier = Modifier.testTag("soc_tab_threats"))
            }
        }

        when (tab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // RQ3 Latency Banner + Lifecycle KPIs
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (latency.targetMet == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                            Text(
                                "RQ3 Detection -> Notification Latency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Median: ${latency.medianMin ?: "—"} min · P90: ${latency.p90Min ?: "—"} min · ${latency.withinTarget}/${latency.count} within <=15m target",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiCard("MTTA", "${lifecycle.mttaMin ?: "—"}m", "Mean triage", Icons.Default.Timer, modifier = Modifier.weight(1f))
                        KpiCard("MTTC", "${lifecycle.mttcMin ?: "—"}m", "Mean contain", Icons.Default.Shield, modifier = Modifier.weight(1f))
                        KpiCard("MTTR", "${lifecycle.mttrMin ?: "—"}m", "Mean recover", Icons.Default.Verified, modifier = Modifier.weight(1f))
                    }
                }

                // Ethio-CERT Sync Controls
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                            Text("Ethio-CERT National Hub Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (canSync) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { vm.pullEthioCertFeed() },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("soc_pull_feed_button"),
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(AppStrings.t(lang, "socSyncIngest"), maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = { vm.reportUpwardEthioCert() },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("soc_report_upward_button"),
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(AppStrings.pick(lang, "Report upward", "ሪፖርት ላክ"), maxLines = 1)
                                    }
                                }
                            }
                            data.syncRuns.take(3).forEach { run ->
                                Text(
                                    "• [${run.direction}] ${run.detail} (${formatDateTime(run.createdAtEpochMs)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Predictive Forecast
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                            Text("Predictive Threat Forecast ($primarySector)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalBarList(
                                items = forecasts.map { it.category.uppercase() to it.riskIndex },
                                maxVal = 100,
                                barColor = MaterialTheme.colorScheme.tertiary,
                                suffix = "",
                            )
                        }
                    }
                }
            }

            1 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (canManageSoc) {
                    item {
                        Button(
                            onClick = { showCreateIncident = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("soc_create_incident_button"),
                        ) {
                            Icon(Icons.Default.AddAlert, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.pick(lang, "Log New Security Incident", "አዲስ የደህንነት ክስተት ይመዝግቡ"))
                        }
                    }
                }
                items(incidents, key = { it.id }) { inc ->
                    val orgName = orgById[inc.organizationId]?.name ?: inc.organizationId
                    val nextStage = inc.status.next()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().testTag("incident_card_${inc.id}"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(inc.ref, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                SeverityBadge(inc.severity, lang)
                                StatusPill(
                                    "${inc.priorityLabel} ${inc.priorityScore.toInt()}",
                                    if (inc.priorityLabel == "IMMEDIATE") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    if (inc.priorityLabel == "IMMEDIATE") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.weight(1f))
                                StatusPill(if (lang == Lang.EN) inc.status.labelEn else inc.status.labelAm, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (lang == Lang.EN) inc.title else inc.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(inc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$orgName · ${inc.category} · Notified: ${
                                    inc.notifiedAtEpochMs?.let { "${maxOf(1, ((it - inc.detectedAtEpochMs) / 60_000L).toInt())} min" } ?: "PENDING"
                                }",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (canManageSoc) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    if (inc.notifiedAtEpochMs == null) {
                                        Button(
                                            onClick = { vm.notifyIncidentOrg(inc.id) },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("notify_inc_${inc.id}"),
                                        ) {
                                            Text(AppStrings.t(lang, "incNotify"))
                                        }
                                    }
                                    if (nextStage != null && inc.status != IncidentStatus.CLOSED) {
                                        FilledTonalButton(
                                            onClick = { vm.advanceIncident(inc.id) },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("advance_inc_${inc.id}"),
                                        ) {
                                            Text("-> ${nextStage.labelEn}")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { noteIncidentId = inc.id },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text("Add Note")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (canContribute) {
                    item {
                        Button(
                            onClick = { showContributeIndicator = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("contribute_ioc_button"),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.pick(lang, "Contribute Threat Indicator (HUB / P2P)", "የስጋት አመልካች ያጋሩ"))
                        }
                    }
                }
                items(indicators, key = { it.id }) { ind ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().testTag("indicator_card_${ind.id}"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(ind.sharing, if (ind.sharing == "P2P") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, if (ind.sharing == "P2P") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(ind.tlp, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                SeverityBadge(ind.severity, lang)
                                Spacer(Modifier.weight(1f))
                                if (!ind.isActive) StatusPill("RETIRED", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (lang == Lang.EN) ind.titleEn else ind.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${ind.type.uppercase()}: ${ind.value}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text("Confidence ${ind.confidence}% · Sensitivity ${ind.sensitivity}/5 · Sightings: ${ind.sightings} · ${ind.sharingRationale}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (canManageSoc) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.recordIndicatorSighting(ind.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("+1 Sighting")
                                    }
                                    TextButton(onClick = { vm.toggleIndicatorActive(ind.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(if (ind.isActive) "Retire" else "Reactivate")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateIncident) {
        var orgId by remember { mutableStateOf(orgs.firstOrNull()?.id ?: "") }
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("phishing") }
        var severity by remember { mutableStateOf("high") }

        AlertDialog(
            onDismissRequest = { showCreateIncident = false },
            title = { Text("Log Security Incident") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (orgs.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            orgs.forEach { o -> FilterChip(selected = orgId == o.id, onClick = { orgId = o.id }, label = { Text(o.name) }) }
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Incident Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("phishing", "bec", "ransomware", "malware", "ddos").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("critical", "high", "medium", "low").forEach { s -> FilterChip(selected = severity == s, onClick = { severity = s }, label = { Text(s) }) }
                    }
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank() && orgId.isNotBlank(), onClick = {
                    vm.createIncident(orgId, title, desc, category, severity)
                    showCreateIncident = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateIncident = false }) { Text("Cancel") } },
        )
    }

    noteIncidentId?.let { incId ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteIncidentId = null },
            title = { Text("Add Incident Timeline Note") },
            text = { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Analyst note") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(enabled = note.isNotBlank(), onClick = {
                    vm.addIncidentNote(incId, note)
                    noteIncidentId = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { noteIncidentId = null }) { Text("Cancel") } },
        )
    }

    if (showContributeIndicator) {
        var type by remember { mutableStateOf("domain") }
        var value by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("phishing") }
        var severity by remember { mutableStateOf("high") }
        var tlp by remember { mutableStateOf("TLP:AMBER") }

        val previewSens = SocEngine.classifySensitivity(category, tlp, 85)
        val (previewSharing, previewRationale) = SocEngine.hybridSharingDecision(tlp, previewSens, category)

        AlertDialog(
            onDismissRequest = { showContributeIndicator = false },
            title = { Text("Contribute IOC") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("domain", "ip", "url", "hash_sha256", "email").forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
                    }
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Observable (IP/domain/hash)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title / Summary") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("phishing", "bec", "ransomware", "data_breach", "insider").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("TLP:GREEN", "TLP:AMBER", "TLP:AMBER+STRICT", "TLP:RED").forEach { t -> FilterChip(selected = tlp == t, onClick = { tlp = t }, label = { Text(t) }) }
                    }
                    Text("Hybrid Routing Preview: $previewSharing ($previewRationale)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(enabled = value.isNotBlank() && title.isNotBlank(), onClick = {
                    vm.contributeIndicator(type, value, title, category, severity, tlp, 85, orgs.firstOrNull()?.id, orgs.take(2).map { it.id })
                    showContributeIndicator = false
                }) { Text("Publish") }
            },
            dismissButton = { TextButton(onClick = { showContributeIndicator = false }) { Text("Cancel") } },
        )
    }
}
