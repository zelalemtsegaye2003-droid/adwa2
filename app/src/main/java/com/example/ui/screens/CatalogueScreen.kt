package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SeedData
import com.example.domain.RulesEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.components.SectionHeader
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusPill
import com.example.viewmodel.AdwaViewModel

@Composable
fun CatalogueScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    var fwFilter by remember { mutableStateOf("ALL") }
    var severityFilter by remember { mutableStateOf("ALL") }
    var pillarFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var expandedCode by remember { mutableStateOf<String?>(null) }

    val visibleOrgs = vm.visibleOrganizations()
    val coverageOrgId = if (visibleOrgs.size == 1) visibleOrgs.first().id else null
    val coverageMap = remember(data.evidenceDocs, data.evidenceLinks, coverageOrgId) {
        RulesEngine.evaluateCoverage(SeedData.ALL_RULES, data.evidenceDocs, data.evidenceLinks, coverageOrgId)
    }

    val availablePillars = remember(fwFilter) {
        SeedData.ALL_RULES
            .filter { fwFilter == "ALL" || it.frameworkCode == fwFilter }
            .map { it.pillar }
            .distinct()
    }

    val filteredRules = remember(fwFilter, severityFilter, pillarFilter, search) {
        val q = search.trim().lowercase()
        SeedData.ALL_RULES.filter { r ->
            (fwFilter == "ALL" || r.frameworkCode == fwFilter) &&
                (severityFilter == "ALL" || r.severity == severityFilter) &&
                (pillarFilter == "ALL" || r.pillar == pillarFilter) &&
                (q.isEmpty() || r.code.lowercase().contains(q) ||
                    r.titleEn.lowercase().contains(q) ||
                    r.titleAm.lowercase().contains(q) ||
                    r.article.lowercase().contains(q) ||
                    r.statementEn.lowercase().contains(q))
        }
    }

    val satisfiedCount = coverageMap.values.count { it.status == RulesEngine.CoverageStatus.SATISFIED }
    val staleCount = coverageMap.values.count { it.status == RulesEngine.CoverageStatus.STALE }
    val gapCount = coverageMap.values.count { it.status == RulesEngine.CoverageStatus.GAP }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "catTitle"),
                subtitle = "${AppStrings.t(lang, "catSub")} · $satisfiedCount covered · $staleCount stale · $gapCount gaps",
            )
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(AppStrings.t(lang, "searchPlaceholder")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search rules") },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("catalogue_search_input"),
            )
        }

        // Framework + Severity + Pillar Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("ALL" to "All (77)", "PD-1321" to "PD-1321 (47)", "NBE-IT" to "NBE-IT (30)").forEach { (k, label) ->
                        FilterChip(
                            selected = fwFilter == k,
                            onClick = { fwFilter = k; pillarFilter = "ALL" },
                            label = { Text(label) },
                            modifier = Modifier.testTag("cat_fw_chip_$k"),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("ALL" to AppStrings.t(lang, "allSeverities"), "critical" to AppStrings.t(lang, "critical"), "high" to AppStrings.t(lang, "high"), "medium" to AppStrings.t(lang, "medium")).forEach { (k, label) ->
                        FilterChip(selected = severityFilter == k, onClick = { severityFilter = k }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = pillarFilter == "ALL", onClick = { pillarFilter = "ALL" }, label = { Text(AppStrings.t(lang, "allPillars")) })
                    availablePillars.forEach { p ->
                        FilterChip(selected = pillarFilter == p, onClick = { pillarFilter = p }, label = { Text(AppStrings.pillarLabel(lang, p)) })
                    }
                }
            }
        }

        items(filteredRules, key = { it.code }) { rule ->
            val cov = coverageMap[rule.code]
            val expanded = expandedCode == rule.code
            Card(
                onClick = { expandedCode = if (expanded) null else rule.code },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("rule_card_${rule.code}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StatusPill(rule.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        StatusPill(rule.article, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        SeverityBadge(rule.severity, lang)
                    }
                    Text(
                        text = if (lang == Lang.EN) rule.titleEn else rule.titleAm,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${AppStrings.pillarLabel(lang, rule.pillar)} · ${rule.priority} · Wt ${rule.weight}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val (covLabel, covBg, covFg) = when (cov?.status) {
                            RulesEngine.CoverageStatus.SATISFIED -> Triple("SATISFIED (${cov.freshCount})", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            RulesEngine.CoverageStatus.STALE -> Triple("STALE (${cov.staleCount})", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            else -> Triple("GAP", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(covLabel, covBg, covFg)
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            HorizontalDivider()
                            Text(if (lang == Lang.EN) rule.statementEn else rule.statementAm, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${AppStrings.t(lang, "checkLogic")}: ${rule.checkLogic}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${AppStrings.t(lang, "penalty")}: ${rule.penaltyRef}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Remediation: ${if (lang == Lang.EN) rule.remediationEn else rule.remediationAm}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            if (cov != null && cov.docs.isNotEmpty()) {
                                Text("Linked Evidence:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                cov.docs.forEach { ref ->
                                    Text("• ${ref.title} (v${ref.version}, ${if (ref.isFresh) "Fresh" else "Stale · ${ref.ageMonths}m"})", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
