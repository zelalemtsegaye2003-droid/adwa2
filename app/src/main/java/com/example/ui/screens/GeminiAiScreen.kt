package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppStrings
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.GeminiChatMessage
import com.example.viewmodel.GeminiUiState

private data class QuickPromptItem(
    val id: String,
    val labelEn: String,
    val labelAm: String,
    val prompt: String,
)

private val QUICK_PROMPTS = listOf(
    QuickPromptItem(
        id = "gap_brief",
        labelEn = "PD-1321 & NBE-IT Gap Brief",
        labelAm = "የPD-1321 እና NBE-IT ክፍተት ማጠቃለያ",
        prompt = "Summarize the top compliance gaps across PD-1321 and NBE-IT based on the live posture snapshot, and list the top 3 executive priorities.",
    ),
    QuickPromptItem(
        id = "sla_triage",
        labelEn = "Overdue SLA Triage (Procl. 1426)",
        labelAm = "ያለፈባቸው SLA ግኝቶች እቅድ",
        prompt = "Create a prioritized 30-day remediation triage plan for our overdue and due-soon findings aligned with Proclamation 1426/2026 SLAs.",
    ),
    QuickPromptItem(
        id = "soc_playbook",
        labelEn = "SOC Incident Playbook",
        labelAm = "የSOC ክስተት ምላሽ መመሪያ",
        prompt = "Recommend immediate containment and Ethio-CERT upward reporting steps for our currently active SOC incidents under NIST SP 800-61.",
    ),
    QuickPromptItem(
        id = "art22_am",
        labelEn = "Art. 22 Sovereignty (አማርኛ)",
        labelAm = "አንቀጽ 22 የውሂብ ሉዓላዊነት",
        prompt = "Explain Proclamation 1321/2024 Article 22 cross-border data transfer and localization obligations in clear Amharic with English legal terms in parentheses.",
    ),
)

@Composable
fun GeminiAiScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val aiState by vm.geminiState.collectAsStateWithLifecycle()
    val messages by vm.geminiMessages.collectAsStateWithLifecycle()
    val lang = ui.lang

    var promptText by remember { mutableStateOf("") }
    var includeLiveContext by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val isGenerating = aiState is GeminiUiState.Loading || aiState is GeminiUiState.Streaming

    LaunchedEffect(messages.size, aiState) {
        val total = messages.size + if (aiState is GeminiUiState.Streaming || aiState is GeminiUiState.Loading) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total)
        }
    }

    fun sendCurrentPrompt() {
        val q = promptText.trim()
        if (q.isNotBlank() && !isGenerating) {
            promptText = ""
            vm.askGemini(q, includeLiveContext = includeLiveContext)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item(key = "header") {
                SectionHeader(
                    title = AppStrings.pick(lang, "Gemini AI Compliance Co-Pilot", "Gemini AI የቅጥ አማካሪ"),
                    subtitle = AppStrings.pick(
                        lang,
                        "Powered by Firebase AI Logic (gemini-3.8-flash)",
                        "በFirebase AI Logic (gemini-3.8-flash) የሚሰራ",
                    ),
                    action = {
                        if (messages.isNotEmpty() || aiState is GeminiUiState.Error) {
                            IconButton(
                                onClick = { vm.clearGeminiConversation() },
                                modifier = Modifier.testTag("ai_clear_button"),
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear AI conversation")
                            }
                        }
                    },
                )
            }

            item(key = "status_card") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                AppStrings.pick(lang, "Live Posture Grounding", "የቀጥታ የቅጥ መረጃ ማገናኛ"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(
                                label = "App Check · 3.8 Flash",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    AppStrings.pick(
                                        lang,
                                        "Attach live org scores, SLA breaches & SOC incidents",
                                        "የድርጅት ውጤቶችን፣ SLA እና SOC ክስተቶችን አካትት",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = includeLiveContext,
                                onCheckedChange = { includeLiveContext = it },
                                modifier = Modifier.testTag("ai_context_switch"),
                            )
                        }
                    }
                }
            }

            item(key = "quick_prompts") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        AppStrings.pick(lang, "Quick Compliance & SOC Briefs", "ፈጣን የAI ትንታኔዎች"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(QUICK_PROMPTS, key = { it.id }) { qp ->
                            SuggestionChip(
                                onClick = {
                                    if (!isGenerating) {
                                        vm.askGemini(qp.prompt, includeLiveContext = includeLiveContext)
                                    }
                                },
                                enabled = !isGenerating,
                                icon = {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = {
                                    Text(AppStrings.pick(lang, qp.labelEn, qp.labelAm))
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ai_quick_${qp.id}"),
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty() && aiState is GeminiUiState.Idle) {
                item(key = "empty_prompt_hint") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                AppStrings.pick(
                                    lang,
                                    "Ask Gemini about Ethiopian cyber regulations or active posture",
                                    "ስለ ኢትዮጵያ የሳይበር ደንቦች ወይም ስለ አሁኑ የቅጥ ሁኔታ Geminiን ይጠይቁ",
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                AppStrings.pick(
                                    lang,
                                    "Tap any quick brief above or enter a question below about Proclamation 1321/2024, NBE-IT directives, SLA remediation, or active Ethio-CERT SOC incidents.",
                                    "ከላይ ካሉት ፈጣን ትንታኔዎች አንዱን ይንኩ ወይም ከታች ስለ አዋጅ 1321/2024፣ NBE-IT፣ SLA እና SOC ክስተቶች ጥያቄ ይጻፉ።",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatMessageCard(msg = msg)
            }

            when (val st = aiState) {
                is GeminiUiState.Loading -> {
                    item(key = "ai_loading") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_loading_indicator"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(14.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                Text(
                                    AppStrings.pick(lang, "Gemini is analyzing compliance posture…", "Gemini የቅጥ ሁኔታውን በመተንተን ላይ ነው…"),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                is GeminiUiState.Streaming -> {
                    item(key = "ai_streaming") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_streaming_card"),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Streaming response",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "Gemini 3.8 Flash",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = st.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                is GeminiUiState.Error -> {
                    item(key = "ai_error") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_error_card"),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = "AI Error",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        AppStrings.pick(lang, "Gemini Request Error", "የGemini ጥያቄ ስህተት"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                Text(
                                    text = st.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                val lastUserPrompt = messages.lastOrNull { it.isUser }?.text
                                if (!lastUserPrompt.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { vm.askGemini(lastUserPrompt, includeLiveContext = includeLiveContext) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("ai_retry_button"),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(AppStrings.pick(lang, "Retry", "እንደገና ሞክር"))
                                    }
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        HorizontalDivider()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            AppStrings.pick(
                                lang,
                                "Ask about PD-1321, SLA risk, SOC…",
                                "ስለ PD-1321፣ SLA ወይም SOC ይጠይቁ…",
                            ),
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendCurrentPrompt() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input"),
                )
                if (isGenerating) {
                    FilledTonalIconButton(
                        onClick = { vm.cancelGeminiGeneration() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_stop_button"),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop generation")
                    }
                } else {
                    FilledIconButton(
                        onClick = { sendCurrentPrompt() },
                        enabled = promptText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_send_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt to Gemini")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageCard(msg: GeminiChatMessage) {
    val isUser = msg.isUser
    val container = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 32.dp else 0.dp,
                end = if (isUser) 0.dp else 16.dp,
            )
            .testTag(if (isUser) "ai_user_msg" else "ai_model_msg"),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (isUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isUser) contentColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (isUser) "Super Admin" else "Gemini 3.8 Flash",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) contentColor else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = formatDateTime(msg.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}
