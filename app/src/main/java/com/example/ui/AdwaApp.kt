package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.components.StatusPill
import com.example.ui.screens.*
import com.example.viewmodel.AdwaViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdwaApp(
    vm: AdwaViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(ui.snackbarMessage) {
        ui.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearToast()
        }
    }

    val currentUser = ui.currentUserId?.let { id -> data.users.find { it.id == id } }

    // Unauthenticated / Public /learn gate
    if (currentUser == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (ui.currentScreen == Screen.LEARN) {
                    LearnPortalScreen(vm)
                } else {
                    LoginScreen(vm)
                }
            }
        }
        return
    }

    // Back handler on non-dashboard primary destinations returns cleanly to Dashboard
    if (ui.currentScreen != Screen.DASHBOARD) {
        BackHandler { vm.navigate(Screen.DASHBOARD) }
    }

    val allowed = vm.allowedScreens()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isExpanded = widthSizeClass != WindowWidthSizeClass.Compact

    fun screenTitle(screen: Screen): String = when (screen) {
        Screen.LOGIN -> AppStrings.t(lang, "signIn")
        Screen.DASHBOARD -> AppStrings.t(lang, "navDashboard")
        Screen.CATALOGUE -> AppStrings.t(lang, "navCatalogue")
        Screen.ORGANIZATIONS -> AppStrings.t(lang, "navOrganizations")
        Screen.SCANS -> AppStrings.t(lang, "navScans")
        Screen.FINDINGS -> AppStrings.t(lang, "navFindings")
        Screen.EVIDENCE -> AppStrings.t(lang, "navEvidence")
        Screen.REPORT -> AppStrings.t(lang, "navReport")
        Screen.SOC -> AppStrings.t(lang, "navSoc")
        Screen.AWARENESS -> AppStrings.t(lang, "navAwareness")
        Screen.LEARN -> AppStrings.t(lang, "navLearn")
        Screen.TRACK_C -> AppStrings.t(lang, "navTrackC")
        Screen.TEAM -> AppStrings.t(lang, "navTeam")
        Screen.AUDIT -> AppStrings.t(lang, "navAudit")
    }

    fun screenIcon(screen: Screen): ImageVector = when (screen) {
        Screen.DASHBOARD -> Icons.Default.Dashboard
        Screen.CATALOGUE -> Icons.AutoMirrored.Filled.MenuBook
        Screen.ORGANIZATIONS -> Icons.Default.Apartment
        Screen.SCANS -> Icons.Default.FactCheck
        Screen.FINDINGS -> Icons.Default.BugReport
        Screen.EVIDENCE -> Icons.Default.FolderSpecial
        Screen.REPORT -> Icons.Default.Assessment
        Screen.SOC -> Icons.Default.Security
        Screen.AWARENESS -> Icons.Default.Insights
        Screen.LEARN -> Icons.Default.School
        Screen.TRACK_C -> Icons.Default.AccountTree
        Screen.TEAM -> Icons.Default.Group
        Screen.AUDIT -> Icons.Default.History
        Screen.LOGIN -> Icons.Default.Lock
    }

    val drawerContent: @Composable () -> Unit = {
        ModalDrawerSheet(modifier = Modifier.widthIn(max = 300.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(AppStrings.t(lang, "appName"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(AppStrings.t(lang, "appSub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(currentUser.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    StatusPill(currentUser.role.shortLabel, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                allowed.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screenIcon(screen), contentDescription = null) },
                        label = { Text(screenTitle(screen), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = ui.currentScreen == screen,
                        onClick = {
                            vm.navigate(screen)
                            if (!isExpanded) scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("nav_item_${screen.name}"),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                    label = { Text(AppStrings.pick(lang, "Reset Demo Data", "የሙከራ ውሂብ ዳግም ጀምር")) },
                    selected = false,
                    onClick = {
                        vm.resetDemoData()
                        if (!isExpanded) scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("nav_reset_demo"),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    label = { Text(AppStrings.t(lang, "signOut")) },
                    selected = false,
                    onClick = { vm.logout() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("nav_sign_out"),
                )
            }
        }
    }

    val mainScaffold: @Composable () -> Unit = {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (!isExpanded) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_button"),
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                            }
                        }
                    },
                    title = {
                        Text(screenTitle(ui.currentScreen), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        TextButton(
                            onClick = { vm.toggleLanguage() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("topbar_lang_toggle"),
                        ) {
                            Text(if (lang == Lang.EN) "አማ" else "EN")
                        }
                        IconButton(
                            onClick = { vm.toggleDarkMode() },
                            modifier = Modifier.testTag("topbar_theme_toggle"),
                        ) {
                            Icon(Icons.Default.Brightness6, contentDescription = "Toggle dark mode")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Box(modifier = Modifier.fillMaxSize().widthIn(max = 980.dp)) {
                    when (ui.currentScreen) {
                        Screen.LOGIN -> LoginScreen(vm)
                        Screen.DASHBOARD -> DashboardScreen(vm)
                        Screen.CATALOGUE -> CatalogueScreen(vm)
                        Screen.ORGANIZATIONS -> OrganizationsScreen(vm)
                        Screen.SCANS -> ScanWizardScreen(vm)
                        Screen.FINDINGS -> FindingsScreen(vm)
                        Screen.EVIDENCE -> EvidenceScreen(vm)
                        Screen.REPORT -> ExecutiveReportScreen(vm)
                        Screen.SOC -> SocScreen(vm)
                        Screen.AWARENESS -> AwarenessAdminScreen(vm)
                        Screen.LEARN -> LearnPortalScreen(vm)
                        Screen.TRACK_C -> TrackCScreen(vm)
                        Screen.TEAM -> TeamScreen(vm)
                        Screen.AUDIT -> AuditLogScreen(vm)
                    }
                }
            }
        }
    }

    if (isExpanded) {
        PermanentNavigationDrawer(drawerContent = drawerContent) {
            mainScaffold()
        }
    } else {
        ModalNavigationDrawer(drawerState = drawerState, drawerContent = drawerContent) {
            mainScaffold()
        }
    }
}
