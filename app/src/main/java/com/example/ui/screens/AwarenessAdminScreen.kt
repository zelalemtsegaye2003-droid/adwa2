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
import com.example.data.AwarenessModulesData
import com.example.data.AwarenessToolsData
import com.example.domain.AwarenessEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.components.GainHistogramChart
import com.example.ui.components.KpiCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.Screen

@Composable
fun AwarenessAdminScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    var tab by remember { mutableIntStateOf(0) } // 0=RQ2 Stats, 1=Schools & Classes, 2=Consent, 3=Curriculum
    var showAddClass by remember { mutableStateOf(false) }

    val classById = remember(data.awarenessClasses) { data.awarenessClasses.associateBy { it.id } }
    val schoolById = remember(data.awarenessSchools) { data.awarenessSchools.associateBy { it.id } }

    // Compute RQ2 stats from paired PRE+POST learners
    val pairedByGroup = remember(data.awarenessLearners, classById) {
        data.awarenessLearners.mapNotNull { l ->
            val cls = classById[l.classId] ?: return@mapNotNull null
            val pre = l.preScore ?: return@mapNotNull null
            val post = l.postScore ?: return@mapNotNull null
            cls.groupType to AwarenessEngine.ScorePair(pre, post)
        }.groupBy({ it.first }, { it.second })
    }
    val tPairs = pairedByGroup["TREATMENT"].orEmpty()
    val cPairs = pairedByGroup["CONTROL"].orEmpty()
    val allPairs = tPairs + cPairs

    val tStat = remember(tPairs) { AwarenessEngine.groupStat(tPairs) }
    val cStat = remember(cPairs) { AwarenessEngine.groupStat(cPairs) }
    val tTest = remember(tPairs) { AwarenessEngine.pairedT(tPairs) }
    val anova = remember(tPairs, cPairs) {
        AwarenessEngine.anovaOneWay(
            mapOf(
                "TREATMENT" to tPairs.map { it.post - it.pre },
                "CONTROL" to cPairs.map { it.post - it.pre },
            )
        )
    }
    val tHist = remember(tPairs) { AwarenessEngine.gainHistogram(tPairs.map { it.post - it.pre }) }
    val cHist = remember(cPairs) { AwarenessEngine.gainHistogram(cPairs.map { it.post - it.pre }) }

    val toolCounts = remember(data.awarenessToolPings) {
        data.awarenessToolPings.groupingBy { it.toolId }.eachCount()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = AppStrings.t(lang, "awTitle"),
                subtitle = AppStrings.t(lang, "awSub"),
                action = {
                    FilledTonalButton(
                        onClick = { vm.navigate(Screen.LEARN) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("aw_open_learn_button"),
                    ) {
                        Text("/learn")
                    }
                },
            )
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("RQ2 Evaluation") }, modifier = Modifier.testTag("aw_tab_rq2"))
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Schools (${data.awarenessSchools.size})") }, modifier = Modifier.testTag("aw_tab_schools"))
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Consent (${data.awarenessLearners.size})") }, modifier = Modifier.testTag("aw_tab_consent"))
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Curriculum (${AwarenessModulesData.MODULES.size})") }, modifier = Modifier.testTag("aw_tab_curriculum"))
            }
        }

        when (tab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiCard("Paired Learners", "${allPairs.size}", "${tPairs.size} Treatment · ${cPairs.size} Control", Icons.Default.Groups, modifier = Modifier.weight(1f))
                        KpiCard("Treatment Gain", "+${tStat.meanGain} pp", "vs +${cStat.meanGain} pp Control", Icons.Default.TrendingUp, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                            Text("Statistical Significance (Pre -> Post Knowledge Gain)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (tTest != null) {
                                Text(
                                    "Treatment Paired-t: t(${tTest.df}) = ${tTest.t}, p = ${tTest.p}, mean gain = +${tTest.meanGain} pp (SD ${tTest.sdGain}) · ${if (tTest.significant) "SIGNIFICANT (p < 0.05)" else "ns"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (anova != null) {
                                Text(
                                    "One-Way ANOVA (Treatment vs Control): F(${anova.dfBetween}, ${anova.dfWithin}) = ${anova.f}, p = ${anova.p}, eta^2 = ${anova.etaSquared} · ${if (anova.significant) "SIGNIFICANT" else "ns"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
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
                            Text("Pre->Post Score Gain Distribution (pp)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            GainHistogramChart(treatmentBuckets = tHist, controlBuckets = cHist)
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
                            Text("AI Enhancement Lab Telemetry (${data.awarenessToolPings.size} total pings)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            AwarenessToolsData.TOOLS.forEach { tool ->
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (lang == Lang.EN) tool.nameEn else tool.nameAm, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text("${toolCounts[tool.id] ?: 0}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                    Button(
                        onClick = { showAddClass = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("aw_add_school_class_button"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add School & Class Cohort")
                    }
                }
                items(data.awarenessClasses, key = { it.id }) { cls ->
                    val school = schoolById[cls.schoolId]
                    val enrolled = data.awarenessLearners.count { it.classId == cls.id }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(cls.groupType, if (cls.groupType == "TREATMENT") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, if (cls.groupType == "TREATMENT") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                                StatusPill("Join: ${cls.joinCode}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                Text("Grade ${cls.grade}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text("${school?.name ?: ""} — ${cls.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${school?.subCity ?: ""} sub-city · Teacher: ${cls.teacherName ?: "—"} · $enrolled learners", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                onClick = { vm.issueLearnerCodes(cls.id, 3) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("issue_codes_${cls.id}"),
                            ) {
                                Text("Issue +3 Participant Codes")
                            }
                        }
                    }
                }
            }

            2 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data.awarenessLearners.take(60), key = { it.id }) { lrn ->
                    val cls = classById[lrn.classId]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${lrn.code} (${cls?.joinCode ?: ""})", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Consent: ${lrn.consent} · Pre: ${lrn.preScore ?: "—"}% · Post: ${lrn.postScore ?: "—"}%", style = MaterialTheme.typography.bodySmall)
                            }
                            if (lrn.consent != "GRANTED") {
                                Button(onClick = { vm.updateLearnerConsent(lrn.id, "GRANTED") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Grant")
                                }
                            } else {
                                OutlinedButton(onClick = { vm.updateLearnerConsent(lrn.id, "DENIED") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Revoke")
                                }
                            }
                        }
                    }
                }
            }

            3 -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(AwarenessModulesData.MODULES, key = { it.code }) { mod ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(mod.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(mod.audience, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                StatusPill("Gr ${mod.gradeBand} · ${mod.durationMin}m", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (lang == Lang.EN) mod.titleEn else mod.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (lang == Lang.EN) mod.summaryEn else mod.summaryAm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAddClass) {
        var schName by remember { mutableStateOf("") }
        var subCity by remember { mutableStateOf("Bole") }
        var clsName by remember { mutableStateOf("Grade 9A") }
        var teacher by remember { mutableStateOf("") }
        var groupType by remember { mutableStateOf("TREATMENT") }

        AlertDialog(
            onDismissRequest = { showAddClass = false },
            title = { Text("Add School & Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = schName, onValueChange = { schName = it }, label = { Text("School Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = subCity, onValueChange = { subCity = it }, label = { Text("Sub-city") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = clsName, onValueChange = { clsName = it }, label = { Text("Class Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("Teacher Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TREATMENT", "CONTROL").forEach { g ->
                            FilterChip(selected = groupType == g, onClick = { groupType = g }, label = { Text(g) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = schName.isNotBlank() && clsName.isNotBlank(), onClick = {
                    vm.addAwarenessSchoolAndClass(schName, subCity, clsName, 9, groupType, teacher)
                    showAddClass = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddClass = false }) { Text("Cancel") } },
        )
    }
}
