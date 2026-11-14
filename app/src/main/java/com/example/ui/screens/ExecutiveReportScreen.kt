package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SeedData
import com.example.domain.ComplianceEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.components.*
import com.example.viewmodel.AdwaViewModel

@Composable
fun ExecutiveReportScreen(vm: AdwaViewModel) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val completedScans = vm.visibleScans().filter { it.status == "complete" }
        .sortedByDescending { it.completedAtEpochMs ?: 0L }
    val selectedScan = completedScans.find { it.id == ui.selectedReportScanId } ?: completedScans.firstOrNull()
    val orgById = remember(data.organizations) { data.organizations.associateBy { it.id } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "repTitle"),
                subtitle = AppStrings.t(lang, "repSub"),
                action = {
                    if (selectedScan != null) {
                        Button(
                            onClick = { vm.exportAndShareScanPdf(context, selectedScan) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("export_exec_pdf_button"),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(AppStrings.t(lang, "repDownloadPdf"))
                        }
                    }
                },
            )
        }

        if (completedScans.isEmpty()) {
            item {
                EmptyStateCard(Icons.Default.PictureAsPdf, "No completed scans yet", "Complete a Compliance Scan first to generate a Board Executive Report.")
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    completedScans.forEach { sc ->
                        FilterChip(
                            selected = selectedScan?.id == sc.id,
                            onClick = { vm.selectReportScan(sc.id) },
                            label = { Text(sc.name, maxLines = 1) },
                        )
                    }
                }
            }

            selectedScan?.let { scan ->
                val org = orgById[scan.organizationId]
                val fw = SeedData.frameworkFor(scan.frameworkCode)
                val rules = SeedData.rulesFor(scan.frameworkCode)
                val ruleByCode = rules.associateBy { it.code }
                val rq1 = ComplianceEngine.auditTimeComparison(scan.durationMinutes ?: 30.0)
                val topFindings = scan.results.filter { it.isFinding }.sortedByDescending { it.riskRank }

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
                            ScoreRing(score = scan.overallScore ?: 0.0, size = 84.dp, strokeWidth = 8.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(org?.name ?: scan.organizationId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${fw?.name ?: scan.frameworkCode} (${fw?.instrumentNo ?: ""})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Auditor: ${scan.auditorName} · ${formatDate(scan.completedAtEpochMs)}", style = MaterialTheme.typography.bodySmall)
                                Text("RQ1 Efficiency: ${rq1.engineMinutes}m (${rq1.reductionPercent}% faster vs manual)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                    Text("${AppStrings.t(lang, "remediationPlan")} (${topFindings.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                items(topFindings, key = { it.id }) { res ->
                    val r = ruleByCode[res.ruleCode] ?: return@items
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(r.code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                SeverityBadge(r.severity, lang)
                                Spacer(Modifier.weight(1f))
                                Text("Risk ${res.riskRank} · ${res.status.uppercase()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(if (lang == Lang.EN) r.titleEn else r.titleAm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(if (lang == Lang.EN) r.remediationEn else r.remediationAm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
