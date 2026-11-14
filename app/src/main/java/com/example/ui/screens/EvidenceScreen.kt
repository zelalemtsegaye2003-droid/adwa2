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
import com.example.data.SeedData
import com.example.domain.RulesEngine
import com.example.i18n.AppStrings
import com.example.model.*
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDate
import com.example.viewmodel.AdwaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvidenceScreen(vm: AdwaViewModel) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val docs = vm.visibleEvidence()
    val orgs = vm.visibleOrganizations()
    val orgById = remember(orgs) { orgs.associateBy { it.id } }

    var showUploadDialog by remember { mutableStateOf(false) }
    var supersedeTarget by remember { mutableStateOf<EvidenceDocument?>(null) }
    var linkTarget by remember { mutableStateOf<EvidenceDocument?>(null) }
    var statusFilter by remember { mutableStateOf("ACTIVE") }

    val filteredDocs = remember(docs, statusFilter) {
        docs.filter { statusFilter == "ALL" || it.status == statusFilter }
    }
    val canUpload = vm.can(Permission.EVIDENCE_UPLOAD)
    val canManage = vm.can(Permission.EVIDENCE_MANAGE_ALL) || vm.can(Permission.EVIDENCE_MANAGE_ORG)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "evTitle"),
                subtitle = AppStrings.t(lang, "evSub"),
                action = {
                    if (canUpload) {
                        Button(
                            onClick = { supersedeTarget = null; showUploadDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("upload_evidence_button"),
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.t(lang, "evUpload"))
                        }
                    }
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("ACTIVE" to "Active", "ARCHIVED" to "Archived", "SUPERSEDED" to "Superseded", "ALL" to "All (${docs.size})").forEach { (k, label) ->
                    FilterChip(selected = statusFilter == k, onClick = { statusFilter = k }, label = { Text(label) })
                }
            }
        }

        items(filteredDocs, key = { it.id }) { doc ->
            val (fresh, ageMonths) = RulesEngine.freshnessOf(doc.category, doc.createdAtEpochMs, doc.lastVerifiedAtEpochMs)
            val docLinks = data.evidenceLinks.filter { it.evidenceId == doc.id }
            val orgName = orgById[doc.organizationId]?.name ?: doc.organizationId

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("evidence_card_${doc.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill("v${doc.version}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        StatusPill(doc.status, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (doc.status == "ACTIVE") {
                            StatusPill(
                                label = if (fresh) "FRESH (${ageMonths}m)" else "STALE (${ageMonths}m)",
                                containerColor = if (fresh) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (fresh) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Text(doc.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(doc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${doc.fileName} (${doc.fileSize / 1024} KB) · $orgName · ${if (lang == Lang.EN) doc.category.labelEn else doc.category.labelAm}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "SHA-256: ${doc.checksumSha256.take(24)}… · Verified ${formatDate(doc.lastVerifiedAtEpochMs)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (docLinks.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            docLinks.forEach { link ->
                                InputChip(
                                    selected = true,
                                    onClick = { if (canManage) vm.unlinkEvidenceRule(link.id) },
                                    label = { Text("${link.ruleCode} (${link.source})") },
                                    trailingIcon = if (canManage) {
                                        { Icon(Icons.Default.Close, contentDescription = "Unlink ${link.ruleCode}", modifier = Modifier.size(14.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                    if (canUpload || canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            if (doc.status == "ACTIVE" && canUpload) {
                                FilledTonalButton(
                                    onClick = { supersedeTarget = doc; showUploadDialog = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("New version")
                                }
                            }
                            if (canManage) {
                                OutlinedButton(
                                    onClick = { linkTarget = doc },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("Link rule")
                                }
                                OutlinedButton(
                                    onClick = { vm.verifyEvidence(doc.id) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("verify_ev_${doc.id}"),
                                ) {
                                    Text("Verify")
                                }
                                TextButton(
                                    onClick = { vm.toggleArchiveEvidence(doc.id) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(if (doc.status == "ACTIVE") "Archive" else "Restore")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        var title by remember { mutableStateOf(supersedeTarget?.title ?: "") }
        var desc by remember { mutableStateOf(supersedeTarget?.description ?: "") }
        var tags by remember { mutableStateOf(supersedeTarget?.tags ?: "ART2-01, NBE-01") }
        var category by remember { mutableStateOf(supersedeTarget?.category ?: EvidenceCategory.POLICY) }
        var orgId by remember { mutableStateOf(supersedeTarget?.organizationId ?: orgs.firstOrNull()?.id ?: "") }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickedUri = uri
        }

        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text(if (supersedeTarget != null) "Upload v${supersedeTarget!!.version + 1} (Supersedes v${supersedeTarget!!.version})" else AppStrings.t(lang, "evUpload")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Artefact Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ev_title_input"))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Rule tags (auto-linked, e.g. ART17-01, NBE-02)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        EvidenceCategory.entries.forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.labelEn) })
                        }
                    }
                    if (orgs.size > 1 && supersedeTarget == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            orgs.forEach { o ->
                                FilterChip(selected = orgId == o.id, onClick = { orgId = o.id }, label = { Text(o.name) })
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pick_saf_file_button"),
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (pickedUri != null) "Document selected (ready to seal)" else "Pick File (PDF/JSON/CSV) — Optional")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank() && orgId.isNotBlank(),
                    onClick = {
                        vm.uploadEvidenceFromUri(context, pickedUri, title, desc, category, tags, orgId, supersedeTarget?.id)
                        showUploadDialog = false
                    },
                    modifier = Modifier.testTag("confirm_upload_ev_button"),
                ) {
                    Text("Seal & Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) { Text(AppStrings.t(lang, "cancel")) }
            },
        )
    }

    linkTarget?.let { doc ->
        var selectedRuleCode by remember { mutableStateOf(SeedData.ALL_RULES.first().code) }
        var ruleSearch by remember { mutableStateOf("") }
        val candidates = remember(ruleSearch) {
            val q = ruleSearch.trim().lowercase()
            SeedData.ALL_RULES.filter {
                q.isEmpty() || it.code.lowercase().contains(q) || it.titleEn.lowercase().contains(q)
            }.take(8)
        }
        AlertDialog(
            onDismissRequest = { linkTarget = null },
            title = { Text("Link Rule to ${doc.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = ruleSearch, onValueChange = { ruleSearch = it }, label = { Text("Filter rule code or title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    candidates.forEach { r ->
                        FilterChip(
                            selected = selectedRuleCode == r.code,
                            onClick = { selectedRuleCode = r.code },
                            label = { Text("${r.code}: ${r.titleEn}", maxLines = 1) },
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.linkEvidenceRule(doc.id, selectedRuleCode)
                    linkTarget = null
                }) { Text("Link") }
            },
            dismissButton = { TextButton(onClick = { linkTarget = null }) { Text("Cancel") } },
        )
    }
}
