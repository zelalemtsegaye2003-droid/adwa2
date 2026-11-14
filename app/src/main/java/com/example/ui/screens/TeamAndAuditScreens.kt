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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.RbacMatrix
import com.example.domain.TotpHelper
import com.example.i18n.AppStrings
import com.example.model.AppRole
import com.example.model.Permission
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.viewmodel.AdwaViewModel

@Composable
fun TeamScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang
    val me = vm.currentUser()
    val orgById = remember(data.organizations) { data.organizations.associateBy { it.id } }

    val visibleUsers = remember(data.users, me) {
        if (vm.can(Permission.USERS_VIEW_ALL)) data.users
        else data.users.filter { it.organizationId == me?.organizationId }
    }
    val canManageUsers = vm.can(Permission.USERS_MANAGE_ALL) || vm.can(Permission.USERS_MANAGE_ORG)

    var showAddUser by remember { mutableStateOf(false) }
    var showMfaSetup by remember { mutableStateOf(false) }
    var showChangePw by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(
                title = AppStrings.t(lang, "navTeam"),
                subtitle = "${visibleUsers.size} accounts · Multi-tenant RBAC",
                action = {
                    if (canManageUsers) {
                        Button(onClick = { showAddUser = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("add_user_button")) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Provision")
                        }
                    }
                },
            )
        }

        // My Security Card (MFA + Password)
        if (me != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                        Text("My Account Security (${me.email})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Role: ${me.role.fullLabel} · MFA: ${if (me.mfaEnabled) "ENABLED (TOTP)" else "Not enabled"}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showMfaSetup = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("open_mfa_setup_button")) {
                                Text(if (me.mfaEnabled) "Manage MFA" else "Enable TOTP MFA")
                            }
                            OutlinedButton(onClick = { showChangePw = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Change Password")
                            }
                        }
                    }
                }
            }
        }

        items(visibleUsers, key = { it.id }) { u ->
            val orgName = u.organizationId?.let { orgById[it]?.name ?: it } ?: "All Organizations"
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("user_card_${u.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(u.role.shortLabel, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        StatusPill(
                            u.status,
                            if (u.status == "ACTIVE") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                            if (u.status == "ACTIVE") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (u.mfaEnabled) StatusPill("MFA ON", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Text(u.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${u.email} · $orgName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Last login: ${formatDateTime(u.lastLoginAtEpochMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (canManageUsers && u.id != me?.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            OutlinedButton(onClick = { vm.toggleTeamUserStatus(u.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (u.status == "ACTIVE") "Disable" else "Enable")
                            }
                            RbacMatrix.assignableOrgRoles.forEach { role ->
                                if (u.role != role) {
                                    TextButton(onClick = { vm.changeTeamUserRole(u.id, role) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("-> ${role.shortLabel}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddUser) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var pw by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(AppRole.COMPLIANCE_OFFICER) }
        var orgId by remember { mutableStateOf(me?.organizationId ?: data.organizations.firstOrNull()?.id) }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddUser = false },
            title = { Text("Provision Team User") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Initial Password (>=10 chars)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        RbacMatrix.assignableOrgRoles.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.shortLabel) })
                        }
                    }
                    if (me?.role == AppRole.SUPER_ADMIN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            data.organizations.forEach { o ->
                                FilterChip(selected = orgId == o.id, onClick = { orgId = o.id }, label = { Text(o.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val e = vm.addTeamUser(name, email, pw, role, orgId)
                    if (e == null) showAddUser = false else err = e
                }) { Text("Create User") }
            },
            dismissButton = { TextButton(onClick = { showAddUser = false }) { Text("Cancel") } },
        )
    }

    if (showMfaSetup && me != null) {
        val candidateSecret = remember { TotpHelper.generateSecretBase32() }
        var code by remember { mutableStateOf("") }
        var disablePw by remember { mutableStateOf("") }
        var backupList by remember { mutableStateOf<List<String>?>(null) }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showMfaSetup = false },
            title = { Text(if (me.mfaEnabled) "MFA Security Active" else "Enable TOTP Authenticator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (backupList != null) {
                        Text("Save these 8 single-use backup codes:", fontWeight = FontWeight.Bold)
                        Text(backupList!!.joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    } else if (me.mfaEnabled) {
                        Text("TOTP secret active (${me.totpSecretBase32}). Remaining backup codes: ${me.backupCodes.size}.")
                        OutlinedTextField(value = disablePw, onValueChange = { disablePw = it }, label = { Text("Enter password to disable MFA") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Add this Base32 secret in Google Authenticator / FreeOTP:", style = MaterialTheme.typography.bodySmall)
                        Text(candidateSecret, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { code = TotpHelper.currentCode(candidateSecret) }) {
                            Text("Preview Live Code (${TotpHelper.currentCode(candidateSecret)})")
                        }
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("6-digit verification code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (backupList != null) {
                    Button(onClick = { showMfaSetup = false }) { Text("Done") }
                } else if (me.mfaEnabled) {
                    Button(onClick = {
                        val e = vm.disableMfaForCurrentUser(disablePw)
                        if (e == null) showMfaSetup = false else err = e
                    }) { Text("Disable MFA") }
                } else {
                    Button(onClick = {
                        val (codes, e) = vm.enableMfaForCurrentUser(candidateSecret, code)
                        if (codes != null) backupList = codes else err = e
                    }) { Text("Activate MFA") }
                }
            },
            dismissButton = {
                if (backupList == null) TextButton(onClick = { showMfaSetup = false }) { Text("Close") }
            },
        )
    }

    if (showChangePw) {
        var cur by remember { mutableStateOf("") }
        var nxt by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showChangePw = false },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = cur, onValueChange = { cur = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nxt, onValueChange = { nxt = it }, label = { Text("New Password (>=10 chars)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val e = vm.changeCurrentPassword(cur, nxt)
                    if (e == null) showChangePw = false else err = e
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showChangePw = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AuditLogScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val me = vm.currentUser()
    val lang = ui.lang

    val visibleEvents = remember(data.authEvents, me) {
        if (vm.can(Permission.AUDIT_VIEW_ALL)) data.authEvents
        else data.authEvents.filter { it.organizationId == me?.organizationId }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionHeader(title = AppStrings.t(lang, "navAudit"), subtitle = "${visibleEvents.size} immutable security events")
        }
        items(visibleEvents, key = { it.id }) { ev ->
            val isWarn = ev.type.contains("FAILURE") || ev.type.contains("BLOCKED")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        StatusPill(
                            label = ev.type,
                            containerColor = if (isWarn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isWarn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(formatDateTime(ev.createdAtEpochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(ev.email, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(ev.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
