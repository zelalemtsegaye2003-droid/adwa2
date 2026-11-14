package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.domain.RulesEngine
import com.example.i18n.AppStrings
import com.example.model.*
import com.example.ui.components.*
import com.example.viewmodel.AdwaViewModel

@Composable
fun ScanWizardScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val orgs = vm.visibleOrganizations()
    val scans = vm.visibleScans()
    val activeScan = ui.activeScanId?.let { id -> scans.find { it.id == id } }

    if (activeScan == null) {
        ScanListAndSetup(vm = vm, orgs = orgs, scans = scans, lang = lang)
    } else if (activeScan.status == "in_progress") {
        BackHandler { vm.clearActiveScan() }
        ScanAssessmentWizard(
            vm = vm,
            scan = activeScan,
            answers = data.draftAnswers[activeScan.id].orEmpty(),
            notes = data.draftNotes[activeScan.id].orEmpty(),
            docs = data.evidenceDocs,
            links = data.evidenceLinks,
            lang = lang,
        )
    } else {
        BackHandler { vm.clearActiveScan() }
        ScanCompletedResults(vm = vm, scan = activeScan, orgs = orgs, lang = lang)
    }
}

@Composable
private fun ScanListAndSetup(
    vm: AdwaViewModel,
    orgs: List<Organization>,
    scans: List<ComplianceScan>,
    lang: Lang,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var selectedOrgId by remember(orgs, ui.preselectedOrgId) {
        mutableStateOf(ui.preselectedOrgId?.takeIf { id -> orgs.any { it.id == id } } ?: orgs.firstOrNull()?.id ?: "")
    }
    var selectedFw by remember(ui.preselectedFramework) { mutableStateOf(ui.preselectedFramework ?: "PD-1321") }
    var scanName by remember(selectedOrgId, selectedFw) {
        val orgName = orgs.find { it.id == selectedOrgId }?.name ?: "Assessment"
        mutableStateOf("$orgName — $selectedFw Review")
    }
    val orgById = remember(orgs) { orgs.associateBy { it.id } }
    val canRun = vm.can(Permission.SCANS_RUN_ORG) || vm.can(Permission.SCANS_RUN_ANY_ORG)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(title = AppStrings.t(lang, "scanTitle"), subtitle = AppStrings.t(lang, "scanSub"))
        }

        if (canRun && orgs.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                        Text("${AppStrings.t(lang, "step1")}: ${AppStrings.t(lang, "newScan")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Organization", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            orgs.forEach { o ->
                                FilterChip(
                                    selected = selectedOrgId == o.id,
                                    onClick = { selectedOrgId = o.id },
                                    label = { Text(o.name, maxLines = 1) },
                                )
                            }
                        }
                        Text("Regulatory Framework", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SeedData.FRAMEWORKS.forEach { fw ->
                                val count = SeedData.rulesFor(fw.code).size
                                FilterChip(
                                    selected = selectedFw == fw.code,
                                    onClick = { selectedFw = fw.code },
                                    label = { Text("${fw.code} ($count rules)") },
                                    modifier = Modifier.testTag("wizard_fw_${fw.code}"),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = scanName,
                            onValueChange = { scanName = it },
                            label = { Text("Assessment Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("wizard_scan_name_input"),
                        )
                        Button(
                            enabled = selectedOrgId.isNotBlank() && scanName.isNotBlank(),
                            onClick = { vm.startDraftScan(selectedOrgId, selectedFw, scanName) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start_scan_button"),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.t(lang, "startScan"))
                        }
                    }
                }
            }
        }

        item {
            Text(AppStrings.t(lang, "navScans"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(scans, key = { it.id }) { sc ->
            val orgName = orgById[sc.organizationId]?.name ?: sc.organizationId
            Card(
                onClick = { vm.openScan(sc.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("scan_item_${sc.id}"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    if (sc.status == "complete") {
                        ScoreRing(score = sc.overallScore ?: 0.0, size = 54.dp, strokeWidth = 5.dp)
                    } else {
                        Icon(Icons.Default.EditNote, contentDescription = "Draft", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(sc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$orgName · ${sc.frameworkCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusPill(
                            label = if (sc.status == "complete") "${sc.findingsCount} open findings · ${formatDate(sc.completedAtEpochMs)}" else "IN PROGRESS (Draft)",
                            containerColor = if (sc.status == "complete") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (sc.status == "complete") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open scan")
                }
            }
        }
    }
}

@Composable
private fun ScanAssessmentWizard(
    vm: AdwaViewModel,
    scan: ComplianceScan,
    answers: Map<String, Answer>,
    notes: Map<String, String>,
    docs: List<EvidenceDocument>,
    links: List<EvidenceLink>,
    lang: Lang,
) {
    val rules = remember(scan.frameworkCode) { SeedData.rulesFor(scan.frameworkCode) }
    val coverage = remember(rules, docs, links, scan.organizationId) {
        RulesEngine.evaluateCoverage(rules, docs, links, scan.organizationId)
    }
    val pillars = remember(rules) { rules.map { it.pillar }.distinct() }
    var pillarFilter by remember { mutableStateOf("ALL") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filtered = remember(rules, pillarFilter) {
        rules.filter { pillarFilter == "ALL" || it.pillar == pillarFilter }
    }
    val answeredCount = rules.count { answers.containsKey(it.code) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { vm.clearActiveScan() }, modifier = Modifier.testTag("wizard_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to scans")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(scan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${AppStrings.t(lang, "step2")}: $answeredCount / ${rules.size} answered · ${AppStrings.t(lang, "autoSaved")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        onClick = { vm.discardDraftScan(scan.id) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("discard_draft_button"),
                    ) {
                        Text(AppStrings.t(lang, "discardScan"), color = MaterialTheme.colorScheme.error)
                    }
                }
                LinearProgressIndicator(
                    progress = { answeredCount.toFloat() / rules.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = pillarFilter == "ALL", onClick = { pillarFilter = "ALL" }, label = { Text("All (${rules.size})") })
                    pillars.forEach { p ->
                        FilterChip(selected = pillarFilter == p, onClick = { pillarFilter = p }, label = { Text(AppStrings.pillarLabel(lang, p)) })
                    }
                }
                if (validationError != null) {
                    Text(validationError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                OutlinedButton(
                    onClick = {
                        rules.forEach { r ->
                            if (!answers.containsKey(r.code)) {
                                vm.saveDraftAnswer(scan.id, r.code, Answer.YES)
                            }
                        }
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("autofill_remaining_button"),
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.pick(lang, "Mark remaining unanswered as Compliant", "ያልተመለሱትን እንደ ተሟልቷል ምልክት ያድርጉ"))
                }
            }

            items(filtered, key = { it.code }) { rule ->
                val curAns = answers[rule.code]
                val curNote = notes[rule.code] ?: ""
                val cov = coverage[rule.code]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(rule.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            SeverityBadge(rule.severity, lang)
                            Spacer(Modifier.weight(1f))
                            if (cov?.status == RulesEngine.CoverageStatus.SATISFIED) {
                                StatusPill(AppStrings.t(lang, "evSuggestYes"), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Text(if (lang == Lang.EN) rule.questionEn else rule.questionAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(
                                Answer.YES to AppStrings.t(lang, "answerYes"),
                                Answer.PARTIAL to AppStrings.t(lang, "answerPartial"),
                                Answer.NO to AppStrings.t(lang, "answerNo"),
                                Answer.NA to AppStrings.t(lang, "answerNa"),
                            ).forEach { (ans, label) ->
                                FilterChip(
                                    selected = curAns == ans,
                                    onClick = {
                                        validationError = null
                                        vm.saveDraftAnswer(scan.id, rule.code, ans)
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("ans_${rule.code}_${ans.name}"),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = curNote,
                            onValueChange = {
                                validationError = null
                                vm.saveDraftNote(scan.id, rule.code, it)
                            },
                            label = {
                                Text(
                                    if (curAns == Answer.NA) AppStrings.pick(lang, "Justification note (required >=5 chars)", "ማብራሪያ (ቢያንስ 5 ፊደላት ያስፈልጋል)")
                                    else AppStrings.pick(lang, "Auditor evidence note (optional)", "የኦዲተር ማስታወሻ (አማራጭ)")
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(14.dp)) {
                Button(
                    onClick = {
                        val missing = rules.any { !answers.containsKey(it.code) }
                        val badNa = rules.any { answers[it.code] == Answer.NA && (notes[it.code]?.trim()?.length ?: 0) < 5 }
                        when {
                            missing -> validationError = AppStrings.t(lang, "answerAllToSubmit")
                            badNa -> validationError = AppStrings.t(lang, "naNoteRequired")
                            else -> vm.submitDraftScan(scan.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("submit_scan_engine_button"),
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${AppStrings.t(lang, "runEngine")} ($answeredCount/${rules.size})")
                }
            }
        }
    }
}

@Composable
private fun ScanCompletedResults(
    vm: AdwaViewModel,
    scan: ComplianceScan,
    orgs: List<Organization>,
    lang: Lang,
) {
    val orgName = orgs.find { it.id == scan.organizationId }?.name ?: scan.organizationId
    val fw = SeedData.frameworkFor(scan.frameworkCode)
    val rules = SeedData.rulesFor(scan.frameworkCode)
    val ruleByCode = remember(rules) { rules.associateBy { it.code } }
    val evalPlan = remember(scan) {
        scan.results.filter { it.isFinding }.sortedByDescending { it.riskRank }
    }
    val rq1 = ComplianceEngine.auditTimeComparison(scan.durationMinutes ?: 30.0)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { vm.clearActiveScan() }, modifier = Modifier.testTag("results_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to scans")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(scan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$orgName · ${fw?.instrumentNo ?: scan.frameworkCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = { vm.openExecutiveReport(scan.id) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("view_exec_report_button"),
                ) {
                    Text("Report / PDF")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    ScoreRing(score = scan.overallScore ?: 0.0, size = 82.dp, strokeWidth = 8.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(AppStrings.t(lang, "overallScore"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${scan.findingsCount} open findings (${scan.criticalFindings} critical)", style = MaterialTheme.typography.bodySmall)
                        Text("Audit time: ${rq1.engineMinutes} min (${rq1.reductionPercent}% faster vs manual)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Text(AppStrings.t(lang, "pillarPerf"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    PillarRadarChart(scores = scan.pillarScores, lang = lang)
                }
            }
        }

        item {
            Text(AppStrings.t(lang, "remediationPlan"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(evalPlan, key = { it.id }) { res ->
            val r = ruleByCode[res.ruleCode] ?: return@items
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(r.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        SeverityBadge(r.severity, lang)
                        Spacer(Modifier.weight(1f))
                        Text("Risk ${res.riskRank}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Text(if (lang == Lang.EN) r.titleEn else r.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (lang == Lang.EN) r.remediationEn else r.remediationAm, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
