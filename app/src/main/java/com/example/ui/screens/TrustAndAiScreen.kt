package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.i18n.AppStrings
import com.example.model.IncidentStatus
import com.example.model.Lang
import com.example.ui.components.ScoreRing
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.EvidenceInspectState
import com.example.viewmodel.PolicyDraftState

@Composable
fun TrustAndAiScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lang = ui.lang

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            SectionHeader(
                title = AppStrings.pick(lang, "Trust Center & AI Studio", "የእምነት ማዕከል እና AI ስቱዲዮ"),
                subtitle = AppStrings.pick(
                    lang,
                    "External Assurance Portal · Gemini Policy & Vision · Co-Pilot",
                    "የእምነት ፖርታል · የGemini ፖሊሲ እና ምስል ፍተሻ · AI አማካሪ",
                ),
            )
            TabRow(selectedTabIndex = ui.trustAiTab) {
                Tab(
                    selected = ui.trustAiTab == 0,
                    onClick = { vm.openTrustAiTab(0) },
                    text = { Text(AppStrings.pick(lang, "Trust Center", "የእምነት ማዕከል"), maxLines = 1) },
                    modifier = Modifier.testTag("trust_tab_portal"),
                )
                Tab(
                    selected = ui.trustAiTab == 1,
                    onClick = { vm.openTrustAiTab(1) },
                    text = { Text(AppStrings.pick(lang, "Policy & Vision", "ፖሊሲ እና ምስል"), maxLines = 1) },
                    modifier = Modifier.testTag("trust_tab_ai_studio"),
                )
                Tab(
                    selected = ui.trustAiTab == 2,
                    onClick = { vm.openTrustAiTab(2) },
                    text = { Text(AppStrings.pick(lang, "Co-Pilot", "AI አማካሪ"), maxLines = 1) },
                    modifier = Modifier.testTag("trust_tab_copilot"),
                )
            }
        }

        when (ui.trustAiTab) {
            0 -> TrustCenterPortalTab(vm = vm, lang = lang)
            1 -> AiPolicyAndVisionTab(vm = vm, lang = lang)
            else -> GeminiAiScreen(vm = vm)
        }
    }
}

@Composable
private fun TrustCenterPortalTab(vm: AdwaViewModel, lang: Lang) {
    val context = LocalContext.current
    val data by vm.dataState.collectAsStateWithLifecycle()
    val cfg = data.trustCenter ?: SeedData.buildInitialState().trustCenter!!
    val completed = vm.visibleScans().filter { it.status == "complete" }
    val avgScore = if (completed.isNotEmpty()) completed.mapNotNull { it.overallScore }.average() else 0.0
    val monitors = data.controlMonitors
    val openRisks = data.riskRegister.count { it.status == "OPEN" || it.status == "IN_PROGRESS" }
    val activeIncidents = vm.visibleIncidents().count { it.status != IncidentStatus.CLOSED }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    ScoreRing(score = avgScore, size = 76.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        StatusPill(
                            label = if (cfg.ndaRequired) "NDA GATED" else "LIVE VERIFIED",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(cfg.publicTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Contact: ${cfg.securityContactEmail} · Last published: ${formatDateTime(cfg.lastPublishedAtEpochMs)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Text(
                        AppStrings.pick(lang, "External Portal Visibility Controls", "የውጭ ፖርታል እይታ መቆጣጠሪያ"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    VisibilitySwitchRow("Require NDA / Auditor Token", cfg.ndaRequired) { v ->
                        vm.updateTrustCenter { it.copy(ndaRequired = v) }
                    }
                    VisibilitySwitchRow("Show Framework Compliance Scores", cfg.showComplianceScores) { v ->
                        vm.updateTrustCenter { it.copy(showComplianceScores = v) }
                    }
                    VisibilitySwitchRow("Show Live Control Monitors (${monitors.count { it.status == "PASS" }}/${monitors.size})", cfg.showControlMonitors) { v ->
                        vm.updateTrustCenter { it.copy(showControlMonitors = v) }
                    }
                    VisibilitySwitchRow("Show Ethio-CERT SOC Readiness", cfg.showEthioCertStatus) { v ->
                        vm.updateTrustCenter { it.copy(showEthioCertStatus = v) }
                    }
                    VisibilitySwitchRow("Show Subprocessor Registry (${cfg.subprocessors.size})", cfg.showSubprocessors) { v ->
                        vm.updateTrustCenter { it.copy(showSubprocessors = v) }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Text("Published Assurance Snapshot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (cfg.showComplianceScores) {
                        Text("• PD-1321 & NBE-IT Verified Score: ${String.format("%.1f", avgScore)}% ($openRisks open risks)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (cfg.showControlMonitors) {
                        Text("• Continuous Control Monitors: ${monitors.count { it.status == "PASS" }}/${monitors.size} passing", style = MaterialTheme.typography.bodySmall)
                    }
                    if (cfg.showEthioCertStatus) {
                        Text("• Ethio-CERT & NIST SP 800-61 SOC: $activeIncidents active incident(s)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (cfg.showSubprocessors) {
                        cfg.subprocessors.forEach { sub ->
                            Text("• Subprocessor: $sub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "Seal SHA-256: ${cfg.publishedSha256?.take(28) ?: "Pending"}…",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.publishTrustCenterSnapshot() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trust_publish_button"),
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.pick(lang, "Publish Seal", "ማህተም አትም"))
                }
                Button(
                    onClick = { vm.exportAndShareTrustCenterPdf(context) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trust_export_pdf_button"),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.pick(lang, "Share PDF", "PDF አጋራ"))
                }
            }
        }
    }
}

@Composable
private fun VisibilitySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AiPolicyAndVisionTab(vm: AdwaViewModel, lang: Lang) {
    val context = LocalContext.current
    val policyState by vm.policyDraftState.collectAsStateWithLifecycle()
    val inspectState by vm.evidenceInspectState.collectAsStateWithLifecycle()
    val orgs = vm.visibleOrganizations()
    val rules = remember { SeedData.ALL_RULES.take(15) }

    var selectedRuleIdx by remember { mutableIntStateOf(0) }
    val selectedRule = rules[selectedRuleIdx]
    val targetOrgId = orgs.firstOrNull()?.id ?: "org_cbe"

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            vm.inspectEvidenceImageWithGemini(context, uri, selectedRule.code)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                    Text(
                        AppStrings.pick(lang, "Target Obligation Rule", "የሚተገበረው ደንብ"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "[${selectedRule.code}] ${if (lang == Lang.EN) selectedRule.titleEn else selectedRule.titleAm}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(selectedRule.statementEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { selectedRuleIdx = (selectedRuleIdx + 1) % rules.size },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai_studio_next_rule"),
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cycle Rule (${selectedRuleIdx + 1}/${rules.size})")
                        }
                    }
                }
            }
        }

        // Section 1: AI Policy Generator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            AppStrings.pick(lang, "Gemini AI Policy Drafter", "Gemini AI የፖሊሲ ረቂቅ"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = { vm.draftPolicyWithGemini(selectedRule.code, targetOrgId) },
                        enabled = policyState !is PolicyDraftState.Loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_draft_policy_button"),
                    ) {
                        if (policyState is PolicyDraftState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Generating Structured Policy…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Draft Policy with Gemini 3.8 Flash")
                        }
                    }

                    when (val ps = policyState) {
                        is PolicyDraftState.Ready -> {
                            HorizontalDivider()
                            Text(ps.draft.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(ps.draft.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(ps.draft.policyText, style = MaterialTheme.typography.bodySmall)
                            ps.draft.checklist.forEach { chk ->
                                Text("• $chk", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { vm.sealDraftedPolicyToEvidence() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_seal_policy_button"),
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Seal to Evidence Vault (SHA-256)")
                            }
                        }
                        is PolicyDraftState.Error -> {
                            StatusPill("Error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            Text(ps.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Section 2: Multimodal Evidence Inspector (Zero-Permission Photo Picker)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ImageSearch, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            AppStrings.pick(lang, "Multimodal Evidence Inspector", "የምስል ማስረጃ መርማሪ (AI)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        AppStrings.pick(
                            lang,
                            "Select a configuration screenshot or signed certificate photo via Android Photo Picker for Gemini Vision verification.",
                            "ለGemini Vision ማረጋገጫ የቅንብር ወይም የሰነድ ፎቶ ይምረጡ።",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = inspectState !is EvidenceInspectState.Loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_pick_evidence_image_button"),
                    ) {
                        if (inspectState is EvidenceInspectState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Inspecting image evidence…")
                        } else {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Evidence Screenshot & Inspect")
                        }
                    }

                    when (val ins = inspectState) {
                        is EvidenceInspectState.Ready -> {
                            val r = ins.result
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    label = "${r.verdict} (${r.confidencePct}%)",
                                    containerColor = if (r.verdict == "VERIFIED") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = if (r.verdict == "VERIFIED") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(r.ruleCode, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                            }
                            if (r.observedControls.isNotEmpty()) {
                                Text("Observed Controls:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                r.observedControls.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (r.missingGaps.isNotEmpty()) {
                                Text("Identified Gaps:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                r.missingGaps.forEach { Text("! $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        is EvidenceInspectState.Error -> {
                            StatusPill("Inspection Error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            Text(ins.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
