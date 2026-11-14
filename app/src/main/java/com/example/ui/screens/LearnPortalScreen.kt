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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AwarenessModulesData
import com.example.i18n.AppStrings
import com.example.model.AwarenessLearner
import com.example.model.AwarenessModule
import com.example.model.Lang
import com.example.ui.components.StatusPill
import com.example.viewmodel.AdwaViewModel

@Composable
fun LearnPortalScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    BackHandler { vm.closeLearnPortal() }

    var tab by remember { mutableIntStateOf(0) } // 0=Portal/Assessments, 1=Lessons, 2=AI Tools Lab
    var activeAssessmentPhase by remember { mutableStateOf<String?>(null) } // PRE | POST
    var activeModule by remember { mutableStateOf<AwarenessModule?>(null) }

    val activeLearner = ui.activeLearnerId?.let { id -> data.awarenessLearners.find { it.id == id } }
    val activeClass = ui.activeLearnClassId?.let { id -> data.awarenessClasses.find { it.id == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { if (ui.isPublicLearnMode) it.statusBarsPadding().navigationBarsPadding() else it },
    ) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (ui.isPublicLearnMode) {
                        IconButton(onClick = { vm.closeLearnPortal() }, modifier = Modifier.testTag("learn_exit_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(AppStrings.t(lang, "learnTitle"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(AppStrings.t(lang, "learnSub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (ui.isPublicLearnMode) {
                        FilledTonalButton(
                            onClick = { vm.toggleLanguage() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("learn_lang_toggle"),
                        ) {
                            Text(if (lang == Lang.EN) "አማ" else "EN")
                        }
                    }
                }
                if (activeAssessmentPhase == null && activeModule == null) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(AppStrings.pick(lang, "Cohort & Quiz", "ክፍል እና ፈተና")) }, modifier = Modifier.testTag("learn_tab_cohort"))
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(AppStrings.pick(lang, "Lessons (15)", "ትምህርቶች (15)")) }, modifier = Modifier.testTag("learn_tab_lessons"))
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(AppStrings.pick(lang, "AI Lab (6)", "AI መሣሪያዎች")) }, modifier = Modifier.testTag("learn_tab_ailab"))
                    }
                }
            }
        }

        when {
            activeAssessmentPhase != null && activeLearner != null -> {
                Fixed16ItemAssessmentView(
                    lang = lang,
                    phase = activeAssessmentPhase!!,
                    learner = activeLearner,
                    onCancel = { activeAssessmentPhase = null },
                    onSubmit = { correct, total ->
                        vm.submitLearnerAssessment(activeLearner.id, activeAssessmentPhase!!, correct, total)
                        activeAssessmentPhase = null
                    },
                )
            }
            activeModule != null -> {
                ModuleReaderView(
                    lang = lang,
                    module = activeModule!!,
                    learner = activeLearner,
                    onBack = { activeModule = null },
                    onQuizComplete = { score ->
                        if (activeLearner != null) {
                            vm.completeModuleQuiz(activeLearner.id, activeModule!!.code, score)
                        }
                    },
                )
            }
            tab == 0 -> CohortAndAssessmentHome(
                vm = vm,
                lang = lang,
                activeLearner = activeLearner,
                activeClass = activeClass,
                onStartAssessment = { phase -> activeAssessmentPhase = phase },
            )
            tab == 1 -> LessonsListView(
                lang = lang,
                learner = activeLearner,
                onOpenModule = { activeModule = it },
            )
            else -> LearnToolsLab(
                lang = lang,
                onToolUsed = { vm.pingTool(it) },
            )
        }
    }
}

@Composable
private fun CohortAndAssessmentHome(
    vm: AdwaViewModel,
    lang: Lang,
    activeLearner: AwarenessLearner?,
    activeClass: com.example.model.AwarenessClass?,
    onStartAssessment: (String) -> Unit,
) {
    var joinInput by remember { mutableStateOf("ADWA-M2S-9A") }
    var codeInput by remember { mutableStateOf("AW-DEMO01") }
    var errMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Text(AppStrings.pick(lang, "Learner Cohort Sign-In", "የተማሪ ክፍል መግቢያ"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        AppStrings.pick(lang, "Enter your class join code (e.g. ADWA-M2S-9A) or participant code (AW-DEMO01 / AW-DEMO02):", "የክፍል ኮድ (ADWA-M2S-9A) ወይም የተማሪ ኮድ (AW-DEMO01 / AW-DEMO02) ያስገቡ፦"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (errMsg != null) Text(errMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = joinInput, onValueChange = { joinInput = it }, label = { Text("Class Join Code") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { errMsg = vm.joinLearnClass(joinInput) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Join") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = codeInput, onValueChange = { codeInput = it }, label = { Text("Participant Code") }, singleLine = true, modifier = Modifier.weight(1f).testTag("learner_code_input"))
                        Button(onClick = { errMsg = vm.signInLearnerCode(codeInput) }, modifier = Modifier.heightIn(min = 48.dp).testTag("learner_signin_button")) { Text("Enter") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick = { codeInput = "AW-DEMO01"; errMsg = vm.signInLearnerCode("AW-DEMO01") }, label = { Text("AW-DEMO01 (Granted)") })
                        SuggestionChip(onClick = { codeInput = "AW-DEMO02"; errMsg = vm.signInLearnerCode("AW-DEMO02") }, label = { Text("AW-DEMO02 (Pending)") })
                    }
                }
            }
        }

        if (activeClass != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("${activeClass.name} (${activeClass.joinCode})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Group: ${activeClass.groupType} · Teacher: ${activeClass.teacherName ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (activeLearner != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Participant ${activeLearner.code}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusPill(
                                label = "Consent: ${activeLearner.consent}",
                                containerColor = if (activeLearner.consent == "GRANTED") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (activeLearner.consent == "GRANTED") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (activeLearner.consent != "GRANTED") {
                            Text(
                                AppStrings.pick(lang, "Parent/guardian consent is required before recording Pre/Post evaluation scores. Free lessons & AI tools remain open!", "የቅድመ/ድህረ-ፈተና ውጤት ለመመዝገብ የወላጅ ፈቃድ ያስፈልጋል። ነፃ ትምህርቶች ክፍት ናቸው!"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text("Pre-assessment: ${activeLearner.preScore?.let { "$it% (${activeLearner.preCorrect}/${activeLearner.preTotal})" } ?: "Not taken yet"}", style = MaterialTheme.typography.bodySmall)
                            Text("Post-assessment: ${activeLearner.postScore?.let { "$it% (${activeLearner.postCorrect}/${activeLearner.postTotal})" } ?: "Not taken yet"}", style = MaterialTheme.typography.bodySmall)
                            Text("Completed modules: ${activeLearner.completedModules.size} / 8", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (activeLearner.preScore == null) {
                                    Button(onClick = { onStartAssessment("PRE") }, modifier = Modifier.heightIn(min = 48.dp).testTag("start_pre_assessment")) {
                                        Text(AppStrings.pick(lang, "Take 16-Item Pre-Test", "ቅድመ-ፈተና ውሰድ"))
                                    }
                                }
                                Button(onClick = { onStartAssessment("POST") }, modifier = Modifier.heightIn(min = 48.dp).testTag("start_post_assessment")) {
                                    Text(AppStrings.pick(lang, "Take 16-Item Post-Test", "ድህረ-ፈተና ውሰድ"))
                                }
                            }
                        }
                        TextButton(onClick = { vm.clearLearnerSession() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Switch Participant")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fixed16ItemAssessmentView(
    lang: Lang,
    phase: String,
    learner: AwarenessLearner,
    onCancel: () -> Unit,
    onSubmit: (Int, Int) -> Unit,
) {
    val items16 = remember {
        AwarenessModulesData.MODULES
            .filter { it.audience == "KIDS" }
            .sortedBy { it.sortOrder }
            .flatMap { mod -> mod.questions.filter { it.sortOrder == 1 || it.sortOrder == 3 }.sortedBy { it.sortOrder } }
    }
    val answers = remember { mutableStateMapOf<String, Int>() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("$phase Assessment (${items16.size} Items) — ${learner.code}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        items(items16.size) { idx ->
            val q = items16[idx]
            val opts = if (lang == Lang.EN) q.optionsEn else q.optionsAm
            val sel = answers[q.id]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                    Text("${idx + 1}. ${if (lang == Lang.EN) q.promptEn else q.promptAm}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    opts.forEachIndexed { oIdx, opt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel == oIdx, onClick = { answers[q.id] = oIdx })
                            Text(opt, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Button(
                enabled = answers.size == items16.size,
                onClick = {
                    val correct = items16.count { q -> answers[q.id] == q.answerIdx }
                    onSubmit(correct, items16.size)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("submit_16item_assessment"),
            ) {
                Text("Submit (${answers.size}/${items16.size})")
            }
        }
    }
}

@Composable
private fun LessonsListView(
    lang: Lang,
    learner: AwarenessLearner?,
    onOpenModule: (AwarenessModule) -> Unit,
) {
    var audFilter by remember { mutableStateOf("ALL") }
    val modules = remember(audFilter) {
        AwarenessModulesData.MODULES.filter { audFilter == "ALL" || it.audience == audFilter }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("ALL" to "All (15)", "KIDS" to "Kids (8)", "TEENS" to "Teens (4)", "PRO" to "Pro (2)", "AWARE" to "Teachers (1)").forEach { (k, label) ->
                    FilterChip(selected = audFilter == k, onClick = { audFilter = k }, label = { Text(label) })
                }
            }
        }
        items(modules, key = { it.code }) { mod ->
            val score = learner?.completedModules?.get(mod.code)
            Card(
                onClick = { onOpenModule(mod) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("learn_mod_${mod.code}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(mod.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        StatusPill(mod.audience, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.weight(1f))
                        if (score != null) {
                            StatusPill("${score.toInt()}%", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(if (lang == Lang.EN) mod.titleEn else mod.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (lang == Lang.EN) mod.summaryEn else mod.summaryAm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ModuleReaderView(
    lang: Lang,
    module: AwarenessModule,
    learner: AwarenessLearner?,
    onBack: () -> Unit,
    onQuizComplete: (Double) -> Unit,
) {
    val answers = remember { mutableStateMapOf<String, Int>() }
    var submitted by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to lessons") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("${module.code} · ${if (lang == Lang.EN) module.titleEn else module.titleAm}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Grade ${module.gradeBand} · ${module.durationMin} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (if (lang == Lang.EN) module.contentEn else module.contentAm).replace("## ", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Text(AppStrings.pick(lang, "Module Check Quiz", "የትምህርት መፈተኛ ጥያቄዎች"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(module.questions.size) { idx ->
            val q = module.questions[idx]
            val opts = if (lang == Lang.EN) q.optionsEn else q.optionsAm
            val sel = answers[q.id]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
                    Text("${idx + 1}. ${if (lang == Lang.EN) q.promptEn else q.promptAm}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    opts.forEachIndexed { oIdx, opt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel == oIdx, onClick = { answers[q.id] = oIdx })
                            Text(opt, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (submitted) {
                        val ok = sel == q.answerIdx
                        Text(
                            "${if (ok) "Correct" else "Review"}: ${if (lang == Lang.EN) q.explainEn else q.explainAm}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item {
            Button(
                enabled = answers.size == module.questions.size,
                onClick = {
                    submitted = true
                    val score = (module.questions.count { answers[it.id] == it.answerIdx }.toDouble() / module.questions.size.coerceAtLeast(1)) * 100.0
                    onQuizComplete(score)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("submit_module_quiz_${module.code}"),
            ) {
                Text(if (learner != null) "Submit Quiz (${answers.size}/${module.questions.size})" else "Check Answers (${answers.size}/${module.questions.size})")
            }
        }
    }
}
