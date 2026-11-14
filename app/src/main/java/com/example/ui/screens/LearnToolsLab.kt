package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.AwarenessToolsData
import com.example.domain.AwarenessEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.components.HorizontalBarList
import com.example.ui.components.StatusPill

@Composable
fun LearnToolsLab(
    lang: Lang,
    onToolUsed: (String) -> Unit,
) {
    var activeToolId by remember { mutableStateOf<String?>(null) }

    if (activeToolId == null) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    AppStrings.pick(lang, "AI Enhancement Lab — 6 On-Device Tools", "የAI ማጠናከሪያ ቤተ-ሙከራ — 6 መሣሪያዎች"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    AppStrings.pick(lang, "100% on-device analysis — passwords, private answers and screenshots never leave your phone.", "ሁሉም ትንተና በመሣሪያዎ ላይ ብቻ ይሠራል — መረጃ አይላክም።"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(AwarenessToolsData.TOOLS.size) { idx ->
                val tool = AwarenessToolsData.TOOLS[idx]
                Card(
                    onClick = {
                        onToolUsed(tool.id)
                        activeToolId = tool.id
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().testTag("ai_tool_card_${tool.id}"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = tool.nameEn, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(tool.moduleCode, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                StatusPill(tool.audiences.joinToString("/"), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (lang == Lang.EN) tool.nameEn else tool.nameAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (lang == Lang.EN) tool.descEn else tool.descAm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open tool")
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            val meta = AwarenessToolsData.TOOLS.find { it.id == activeToolId }
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    IconButton(onClick = { activeToolId = null }, modifier = Modifier.testTag("ai_tool_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                    }
                    Text(
                        meta?.let { if (lang == Lang.EN) it.nameEn else it.nameAm } ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            when (activeToolId) {
                "password" -> PasswordStrengthTool(lang)
                "phishing" -> PhishingRadarTool(lang)
                "sextortion" -> SextortionTrainerTool(lang)
                "footprint" -> DigitalFootprintTool(lang)
                "flagging" -> ContentFlaggingTool(lang)
                "report-guide" -> CybercrimeReportGuideTool(lang)
            }
        }
    }
}

@Composable
private fun PasswordStrengthTool(lang: Lang) {
    var pw by remember { mutableStateOf("") }
    val rep = remember(pw) { AwarenessEngine.scorePassword(pw) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                label = { Text(AppStrings.pick(lang, "Type a test password or passphrase", "ለመሞከር የይለፍ ቃል ይጻፉ")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("pw_tool_input"),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Abebe2012", "12345678", "Blue-Donkey-Coffee-7!", "ሰላም-ቡና-2026!").forEach { sample ->
                    SuggestionChip(onClick = { pw = sample }, label = { Text(sample) })
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${rep.score}/100 · ${if (lang == Lang.EN) rep.bandEn else rep.bandAm}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${rep.entropyBits} bits", style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { rep.score / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(AppStrings.pick(lang, "Estimated crack time: ${rep.crackTimeEn}", "የመሰበር ግምት ጊዜ፦ ${rep.crackTimeAm}"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(rep.findings.size) { i ->
            val f = rep.findings[i]
            val container = when (f.severity) {
                "critical" -> MaterialTheme.colorScheme.errorContainer
                "warn" -> MaterialTheme.colorScheme.tertiaryContainer
                "good" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(color = container, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(if (lang == Lang.EN) f.textEn else f.textAm, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(AppStrings.pick(lang, "Advice:", "ምክር፦"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                (if (lang == Lang.EN) rep.tipsEn else rep.tipsAm).forEach { tip ->
                    Text("• $tip", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PhishingRadarTool(lang: Lang) {
    val verdicts = remember { mutableStateMapOf<Int, String>() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(AwarenessToolsData.PHISH_SPECIMENS.size) { i ->
            val sp = AwarenessToolsData.PHISH_SPECIMENS[i]
            val userChoice = verdicts[sp.id]
            val mlScore = AwarenessEngine.phishiness(sp.hits)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(sp.channel, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(sp.sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sp.subject != sp.channel) Text(sp.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(sp.body, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = userChoice == "PHISHING", onClick = { verdicts[sp.id] = "PHISHING" }, label = { Text(AppStrings.pick(lang, "Phishing", "ፊሺንግ")) })
                        FilterChip(selected = userChoice == "LEGIT", onClick = { verdicts[sp.id] = "LEGIT" }, label = { Text(AppStrings.pick(lang, "Legitimate", "እውነተኛ")) })
                    }
                    if (userChoice != null) {
                        val correct = userChoice == sp.verdict
                        Surface(
                            color = if (correct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Classifier: ${sp.verdict} (risk $mlScore%) · ${if (correct) "Correct!" else "Look closer"}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(if (lang == Lang.EN) sp.whyEn else sp.whyAm, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SextortionTrainerTool(lang: Lang) {
    val chosen = remember { mutableStateMapOf<String, Int>() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(if (lang == Lang.EN) AwarenessToolsData.SEXTORTION_INTRO_EN else AwarenessToolsData.SEXTORTION_INTRO_AM, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
        items(AwarenessToolsData.SEXTORTION_STEPS.size) { idx ->
            val step = AwarenessToolsData.SEXTORTION_STEPS[idx]
            val sel = chosen[step.id]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Text("Moment ${idx + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(if (lang == Lang.EN) step.narrativeEn else step.narrativeAm, style = MaterialTheme.typography.bodyMedium)
                    step.choices.forEachIndexed { cIdx, ch ->
                        OutlinedCard(onClick = { chosen[step.id] = cIdx }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (lang == Lang.EN) ch.textEn else ch.textAm, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                        }
                    }
                    if (sel != null) {
                        val c = step.choices[sel]
                        val bg = when (c.outcome) {
                            "safe" -> MaterialTheme.colorScheme.primaryContainer
                            "risky" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                        Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Text("${c.outcome.uppercase()}: ${if (lang == Lang.EN) c.feedbackEn else c.feedbackAm}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                    Text("INTERPOL Emergency Protocol", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    (if (lang == Lang.EN) AwarenessToolsData.SEXTORTION_PROTOCOL_EN else AwarenessToolsData.SEXTORTION_PROTOCOL_AM).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalFootprintTool(lang: Lang) {
    val answers = remember { mutableStateMapOf<String, Int>() }
    val result = remember(answers.toMap()) { AwarenessEngine.scoreFootprint(answers, AwarenessToolsData.FOOTPRINT_QUESTIONS) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Text("Exposure Risk: ${result.risk}% — ${if (lang == Lang.EN) result.bandEn else result.bandAm}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalBarList(
                        items = result.categoryScores.entries.map { it.key.uppercase() to it.value },
                        maxVal = 100,
                        barColor = MaterialTheme.colorScheme.tertiary,
                        suffix = "%",
                    )
                    (if (lang == Lang.EN) result.tipsEn else result.tipsAm).forEach { tip ->
                        Text("• $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        items(AwarenessToolsData.FOOTPRINT_QUESTIONS.size) { i ->
            val q = AwarenessToolsData.FOOTPRINT_QUESTIONS[i]
            val sel = answers[q.id]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Text("${i + 1}. ${if (lang == Lang.EN) q.qEn else q.qAm}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    q.options.forEachIndexed { oIdx, opt ->
                        FilterChip(
                            selected = sel == oIdx,
                            onClick = { answers[q.id] = oIdx },
                            label = { Text(if (lang == Lang.EN) opt.textEn else opt.textAm) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentFlaggingTool(lang: Lang) {
    val choices = remember { mutableStateMapOf<Int, String>() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(AwarenessToolsData.FLAG_POSTS.size) { i ->
            val p = AwarenessToolsData.FLAG_POSTS[i]
            val c = choices[p.id]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Text(p.author, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(p.text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = c == "KEEP", onClick = { choices[p.id] = "KEEP" }, label = { Text("Keep (Protected)") })
                        FilterChip(selected = c == "REPORT", onClick = { choices[p.id] = "REPORT" }, label = { Text("Report (Harmful)") })
                    }
                    if (c != null) {
                        val ok = c == p.correct
                        Surface(
                            color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${if (lang == Lang.EN) p.basisEn else p.basisAm}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(if (lang == Lang.EN) p.whyEn else p.whyAm, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CybercrimeReportGuideTool(lang: Lang) {
    var selectedId by remember { mutableStateOf(AwarenessToolsData.INCIDENT_TYPES.first().id) }
    val selected = AwarenessToolsData.INCIDENT_TYPES.find { it.id == selectedId } ?: AwarenessToolsData.INCIDENT_TYPES.first()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(AppStrings.pick(lang, "Select what happened:", "ምን እንደተከሰተ ይምረጡ፦"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        items(AwarenessToolsData.INCIDENT_TYPES.size) { i ->
            val item = AwarenessToolsData.INCIDENT_TYPES[i]
            FilterChip(
                selected = selectedId == item.id,
                onClick = { selectedId = item.id },
                label = { Text(if (lang == Lang.EN) item.labelEn else item.labelAm) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    StatusPill(if (lang == Lang.EN) selected.urgencyEn else selected.urgencyAm, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
                    (if (lang == Lang.EN) selected.specificEn else selected.specificAm).forEachIndexed { idx, step ->
                        Text("${idx + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    Text("Ethiopian Helplines: Police 991 · telebirr 994 · Ethio-CERT National CSIRT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
