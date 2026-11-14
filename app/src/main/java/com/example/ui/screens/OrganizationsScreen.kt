package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.model.Permission
import com.example.ui.components.ScoreRing
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.viewmodel.AdwaViewModel

@Composable
fun OrganizationsScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val orgs = vm.visibleOrganizations()
    val scans = vm.visibleScans()
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "orgTitle"),
                subtitle = AppStrings.t(lang, "orgSub"),
                action = {
                    if (vm.can(Permission.ORGS_MANAGE)) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("add_org_button"),
                        ) {
                            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.t(lang, "addOrg"))
                        }
                    }
                },
            )
        }

        items(orgs, key = { it.id }) { org ->
            val orgScans = scans.filter { it.organizationId == org.id && it.status == "complete" }
                .sortedByDescending { it.completedAtEpochMs ?: 0L }
            val latest = orgScans.firstOrNull()
            val openFindings = orgScans.sumOf { it.findingsCount }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("org_card_${org.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (latest != null) {
                            ScoreRing(score = latest.overallScore ?: 0.0, size = 58.dp, strokeWidth = 5.dp)
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.size(58.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Apartment, contentDescription = org.name, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (lang == Lang.EN) org.name else org.nameAm, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(org.sector, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(org.tier, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Text(
                                "${orgScans.size} completed scan(s) · $openFindings open findings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!org.notes.isNullOrBlank()) {
                        Text(org.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(org.contactEmail ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (vm.can(Permission.SCANS_RUN_ORG) || vm.can(Permission.SCANS_RUN_ANY_ORG)) {
                            FilledTonalButton(
                                onClick = { vm.launchNewScanForOrg(org.id) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("org_new_scan_${org.id}"),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(AppStrings.t(lang, "newScan"))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var nameAm by remember { mutableStateOf("") }
        var sector by remember { mutableStateOf("Banking") }
        var tier by remember { mutableStateOf("Growth") }
        var email by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(AppStrings.t(lang, "addOrg")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Organization Name (EN)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new_org_name_input"))
                    OutlinedTextField(value = nameAm, onValueChange = { nameAm = it }, label = { Text("Name (Amharic, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Banking", "Microfinance", "Telecom", "SME").forEach { s ->
                            FilterChip(selected = sector == s, onClick = { sector = s }, label = { Text(s) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Enterprise", "Growth", "Critical Infrastructure").forEach { t ->
                            FilterChip(selected = tier == t, onClick = { tier = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Contact email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.addOrganization(name, nameAm, sector, tier, email, notes)
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_org_button"),
                ) {
                    Text(AppStrings.t(lang, "save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(AppStrings.t(lang, "cancel")) }
            },
        )
    }
}
