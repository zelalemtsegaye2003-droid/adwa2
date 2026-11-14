package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.TrackCEngine
import com.example.i18n.AppStrings
import com.example.model.ExpansionGate
import com.example.model.Lang
import com.example.model.Permission
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDate
import com.example.viewmodel.AdwaViewModel

@Composable
fun TrackCScreen(vm: AdwaViewModel) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    var tab by remember { mutableIntStateOf(0) } // 0=Gate Board, 1=ACFS Register, 2=CII Monitoring
    var attachGateTarget by remember { mutableStateOf<ExpansionGate?>(null) }
    var showDecisionDialog by remember { mutableStateOf(false) }

    val canManageGov = vm.can(Permission.GOVERNANCE_MANAGE)
    val canManageAcfs = vm.can(Permission.ACFS_MANAGE)
    val canManageCii = vm.can(Permission.CII_MANAGE)
    val currentUser = vm.currentUser()

    val p4Readiness = remember(data.expansionGates) { TrackCEngine.phaseReadiness("P4_ACFS", data.expansionGates) }
    val p5Readiness = remember(data.expansionGates) { TrackCEngine.phaseReadiness("P5_CII", data.expansionGates) }
    val acfsOpen = remember(data.acfsItems) { TrackCEngine.isAcfsGateOpen(data.acfsItems) }

    val visibleCiiOperators = remember(data.ciiOperators, currentUser, canManageCii) {
        if (canManageCii) data.ciiOperators
        else data.ciiOperators.filter { it.orgId == currentUser?.organizationId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = AppStrings.t(lang, "gateTitle"),
                subtitle = AppStrings.t(lang, "gateSub"),
                action = {
                    if (vm.can(Permission.GOVERNANCE_VIEW)) {
                        FilledTonalButton(
                            onClick = { vm.exportAndShareGateDossierPdf(context) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("export_gate_dossier_pdf"),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dossier")
                        }
                    }
                },
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Gate Board") }, modifier = Modifier.testTag("trackc_tab_board"))
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("ACFS (P4)") }, modifier = Modifier.testTag("trackc_tab_acfs"))
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("CII (P5)") }, modifier = Modifier.testTag("trackc_tab_cii"))
            }
        }

        when (tab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(p4Readiness to "Phase 4 · ACFS", p5Readiness to "Phase 5 · CII").forEach { (r, title) ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (r.open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
                                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    StatusPill(
                                        label = if (r.open) AppStrings.t(lang, "gateOpenChip") else AppStrings.t(lang, "gateLockedChip"),
                                        containerColor = if (r.open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer,
                                        contentColor = if (r.open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text("${r.satisfied}/${r.total} satisfied (${r.readinessPct}%)", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                items(data.expansionGates, key = { it.id }) { gate ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().testTag("gate_card_${gate.id}"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(gate.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(gate.category, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                val (bg, fg) = when (gate.status) {
                                    "SATISFIED" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                    "BLOCKED" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                    "IN_PROGRESS" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                StatusPill(gate.status, bg, fg)
                            }
                            Text(if (lang == Lang.EN) gate.titleEn else gate.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (lang == Lang.EN) gate.detailEn else gate.detailAm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (gate.evidence.isNotEmpty()) {
                                Text("Sealed artefacts: ${gate.evidence.joinToString { "${it.fileName} (${it.checksumSha256.take(8)}…)" }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("No sealed verification artefact attached yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (canManageGov) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    OutlinedButton(onClick = { attachGateTarget = gate }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("Attach Artefact")
                                    }
                                    listOf("IN_PROGRESS", "SATISFIED", "BLOCKED", "NOT_STARTED").forEach { next ->
                                        if (TrackCEngine.canTransitionGate(gate.status, next)) {
                                            FilledTonalButton(
                                                onClick = { vm.transitionGate(gate.id, next, null) },
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("gate_${gate.id}_to_$next"),
                                            ) {
                                                Text("-> $next")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (acfsOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(14.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Phase 4 ACFS Continental Federation: ${if (acfsOpen) "OPEN" else "LOCKED"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${data.acfsItems.count { it.status == "SATISFIED" }}/${data.acfsItems.size} preconditions satisfied across 4 domains",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (canManageAcfs) {
                                Button(onClick = { showDecisionDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Log Decision")
                                }
                            }
                        }
                    }
                }
                items(data.acfsItems, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(item.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(item.domain, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.weight(1f))
                                StatusPill(item.status, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (lang == Lang.EN) item.titleEn else item.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (canManageAcfs) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    listOf("IN_PROGRESS", "EVIDENCE_SUBMITTED", "SATISFIED", "BLOCKED").forEach { st ->
                                        if (item.status != st) {
                                            OutlinedButton(onClick = { vm.updateAcfsItemStatus(item.id, st) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                                Text(st)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Periodic Board Decisions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(data.acfsDecisions, key = { it.id }) { dec ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${dec.decision} · ${formatDate(dec.createdAtEpochMs)} · ${dec.decidedBy}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(dec.rationale, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            2 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visibleCiiOperators, key = { it.id }) { op ->
                    val gateOpen = TrackCEngine.isOperatorGateOpen(op)
                    val reasons = TrackCEngine.operatorGateReasons(op)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().testTag("cii_operator_${op.code}"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(op.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(op.sector, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                StatusPill(
                                    label = if (gateOpen) "DOUBLE-LOCK OPEN" else "DOUBLE-LOCK CLOSED",
                                    containerColor = if (gateOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (gateOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Text(if (lang == Lang.EN) op.name else op.nameAm, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (reasons.isNotEmpty()) {
                                Text("Gate block: ${reasons.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            op.mous.forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("MOU v${m.version}: ${m.status}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    if (canManageCii && m.status != "ACTIVE") {
                                        TextButton(onClick = { vm.advanceCiiMou(op.id, m.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Advance MOU") }
                                    }
                                }
                            }
                            op.authorizations.forEach { a ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("INSA Auth ${a.reference}: ${a.status}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    if (canManageCii && a.status != "ACTIVE") {
                                        TextButton(onClick = { vm.advanceCiiAuth(op.id, a.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Advance INSA") }
                                    }
                                }
                            }
                            HorizontalDivider()
                            op.systems.forEach { sys ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (lang == Lang.EN || sys.nameAm.isNullOrBlank()) sys.name else sys.nameAm, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("${sys.category} · ${sys.readiness}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (canManageCii && sys.readiness != "MONITORING_AUTHORIZED") {
                                        FilledTonalButton(onClick = { vm.advanceCiiSystem(op.id, sys.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text("Advance")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    attachGateTarget?.let { gate ->
        var desc by remember { mutableStateOf("") }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri = it }
        AlertDialog(
            onDismissRequest = { attachGateTarget = null },
            title = { Text("Seal Gate Artefact (${gate.code})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Artefact note / reference") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (pickedUri != null) "File selected" else "Pick File (Optional)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.attachGateEvidenceFromUri(context, gate.id, pickedUri, desc)
                    attachGateTarget = null
                }) { Text("Seal & Attach") }
            },
            dismissButton = { TextButton(onClick = { attachGateTarget = null }) { Text("Cancel") } },
        )
    }

    if (showDecisionDialog) {
        var decision by remember { mutableStateOf("REMAINED_LOCKED") }
        var rationale by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDecisionDialog = false },
            title = { Text("Record ACFS Board Decision") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("REMAINED_LOCKED", "OPENED", "RE_LOCKED").forEach { d ->
                            FilterChip(selected = decision == d, onClick = { decision = d }, label = { Text(d) })
                        }
                    }
                    OutlinedTextField(value = rationale, onValueChange = { rationale = it }, label = { Text("Board Rationale") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = rationale.isNotBlank(), onClick = {
                    vm.recordAcfsDecision(decision, rationale)
                    showDecisionDialog = false
                }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { showDecisionDialog = false }) { Text("Cancel") } },
        )
    }
}
