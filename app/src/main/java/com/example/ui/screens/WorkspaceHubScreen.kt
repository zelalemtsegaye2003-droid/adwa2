package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppStrings
import com.example.model.Permission
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.Screen

private data class HubTile(
    val screen: Screen,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@Composable
fun WorkspaceHubScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lang = ui.lang
    val user = vm.currentUser()

    val tiles = buildList {
        add(HubTile(Screen.CATALOGUE, AppStrings.t(lang, "navCatalogue"), "77 PD-1321 + NBE-IT rules", Icons.AutoMirrored.Filled.MenuBook))
        add(HubTile(Screen.ORGANIZATIONS, AppStrings.t(lang, "navOrganizations"), "Monitored banks, MFIs & entities", Icons.Default.Apartment))
        add(HubTile(Screen.SCANS, AppStrings.t(lang, "navScans"), "Guided compliance assessments", Icons.Default.FactCheck))
        add(HubTile(Screen.FINDINGS, AppStrings.t(lang, "navFindings"), "Procl. 1426/2026 remediation SLAs", Icons.Default.BugReport))
        add(HubTile(Screen.EVIDENCE, AppStrings.t(lang, "navEvidence"), "SHA-256 sealed artifacts", Icons.Default.FolderSpecial))
        add(HubTile(Screen.REPORT, AppStrings.t(lang, "navReport"), "Board PDF & pillar analytics", Icons.Default.Assessment))
        if (vm.can(Permission.AWARENESS_MANAGE)) {
            add(HubTile(Screen.AWARENESS, AppStrings.t(lang, "navAwareness"), "Human Risk Score & RQ2 ANOVA", Icons.Default.Insights))
        }
        add(HubTile(Screen.LEARN, AppStrings.t(lang, "navLearn"), "Public bilingual modules & tools", Icons.Default.School))
        if (vm.can(Permission.GOVERNANCE_VIEW) || vm.can(Permission.CII_VIEW_OWN)) {
            add(HubTile(Screen.TRACK_C, AppStrings.t(lang, "navTrackC"), "Phase 4 ACFS & Phase 5 CII gates", Icons.Default.AccountTree))
        }
        if (vm.can(Permission.USERS_VIEW_ALL) || vm.can(Permission.USERS_VIEW_ORG)) {
            add(HubTile(Screen.TEAM, AppStrings.t(lang, "navTeam"), "Users, RBAC & MFA settings", Icons.Default.Group))
        }
        if (vm.can(Permission.AUDIT_VIEW_ALL) || vm.can(Permission.AUDIT_VIEW_ORG)) {
            add(HubTile(Screen.AUDIT, AppStrings.t(lang, "navAudit"), "Security & auth event trail", Icons.Default.History))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "navMore"),
                subtitle = AppStrings.pick(lang, "All Adwa Cyber GRC, Track B & Track C modules", "ሁሉም የአድዋ ሳይበር መሳሪያዎች"),
            )
        }

        if (user != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(user.role.shortLabel, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        items(tiles, key = { it.screen.name }) { tile ->
            Card(
                onClick = { vm.navigate(tile.screen) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("hub_tile_${tile.screen.name}"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(tile.icon, contentDescription = tile.title, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open ${tile.title}")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                OutlinedButton(
                    onClick = { vm.resetDemoData() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_reset_demo"),
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.pick(lang, "Reset Demo", "ዳግም ጀምር"))
                }
                FilledTonalButton(
                    onClick = { vm.logout() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_sign_out"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.t(lang, "signOut"))
                }
            }
        }
    }
}
