package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.TotpHelper
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.components.CaptchaBox
import com.example.ui.components.StatusPill
import com.example.viewmodel.AdwaViewModel

private fun generateCaptcha(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..5).map { chars.random() }.joinToString("")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(vm: AdwaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val lang = ui.lang
    val pendingMfaUser = data.users.find { it.id == ui.pendingMfaUserId }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var captchaCode by remember { mutableStateOf(generateCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var mfaCode by remember { mutableStateOf("") }
    var mfaHintCode by remember { mutableStateOf<String?>(null) }

    var showCreateOrg by remember { mutableStateOf(false) }
    var showForgotPw by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cyber_hero_banner),
                    contentDescription = "Adwa Cyber Command Backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF040911).copy(alpha = 0.45f),
                                    Color(0xFF052E26).copy(alpha = 0.90f),
                                )
                            )
                        ),
                )
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StatusPill(
                            label = AppStrings.t(lang, "phase1Badge"),
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White,
                        )
                        FilledTonalButton(
                            onClick = { vm.toggleLanguage() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("login_lang_toggle"),
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = "Switch language", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (lang == Lang.EN) "አማርኛ" else "English")
                        }
                    }
                    Column {
                        Text(
                            text = AppStrings.t(lang, "appName"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = AppStrings.t(lang, "signInSub"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }

        // Body Content Centered with width constraint for tablets
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .widthIn(max = 580.dp),
            ) {
                // Public Learner Portal entry banner
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = "Student & Citizen Learner Portal",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.pick(lang, "Public Learner Portal (/learn)", "ነፃ የሳይበር ግንዛቤ መማሪያ (/learn)"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = AppStrings.pick(lang, "No login needed — 15 bilingual lessons & 6 AI Safety Tools", "ያለ መለያ መግባት ይችላሉ — 15 ትምህርቶችና 6 የAI መሳሪያዎች"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        Button(
                            onClick = { vm.openPublicLearnPortal() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("learn_portal_entry_button"),
                        ) {
                            Text(AppStrings.pick(lang, "Open", "ክፈት"))
                        }
                    }
                }

                // Auth Card: Either MFA Challenge OR Email/Password Login
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(18.dp),
                    ) {
                        if (pendingMfaUser != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = "MFA", tint = MaterialTheme.colorScheme.primary)
                                Text(AppStrings.t(lang, "mfaTitle"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${AppStrings.t(lang, "mfaSub")} (${pendingMfaUser.email})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (errorMsg != null) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                                }
                            }
                            OutlinedTextField(
                                value = mfaCode,
                                onValueChange = { mfaCode = it },
                                label = { Text("6-digit TOTP or Backup Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                modifier = Modifier.fillMaxWidth().testTag("mfa_code_input"),
                            )
                            // Helper to test authenticator code locally in demo
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        val sec = pendingMfaUser.totpSecretBase32 ?: ""
                                        mfaHintCode = TotpHelper.currentCode(sec)
                                        mfaCode = mfaHintCode ?: ""
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("fill_demo_totp_button"),
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = "Get live TOTP", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(AppStrings.pick(lang, "Use Authenticator Code", "የTOTP ኮድ ሙላ"))
                                }
                                if (mfaHintCode != null) {
                                    Text("TOTP: $mfaHintCode", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        mfaCode = ""
                                        mfaHintCode = null
                                        errorMsg = null
                                        vm.cancelMfa()
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("mfa_cancel_button"),
                                ) {
                                    Text(AppStrings.t(lang, "backToLogin"))
                                }
                                Button(
                                    onClick = {
                                        errorMsg = vm.verifyMfa(mfaCode)
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("mfa_verify_button"),
                                ) {
                                    Text(AppStrings.t(lang, "mfaVerify"))
                                }
                            }
                        } else {
                            Text(AppStrings.t(lang, "signInTitle"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (errorMsg != null) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                                }
                            }
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; errorMsg = null },
                                label = { Text(AppStrings.t(lang, "email")) },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; errorMsg = null },
                                label = { Text(AppStrings.t(lang, "password")) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility",
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
                            )
                            Text(AppStrings.t(lang, "captchaLabel"), style = MaterialTheme.typography.labelMedium)
                            CaptchaBox(code = captchaCode, onRefresh = { captchaCode = generateCaptcha(); captchaInput = "" })
                            OutlinedTextField(
                                value = captchaInput,
                                onValueChange = { captchaInput = it.uppercase(); errorMsg = null },
                                label = { Text(AppStrings.t(lang, "captchaPlaceholder")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("captcha_input"),
                            )
                            Button(
                                onClick = {
                                    if (!captchaInput.trim().equals(captchaCode, ignoreCase = true)) {
                                        errorMsg = AppStrings.pick(lang, "Security check characters do not match", "የደህንነት ፊደላት አልተዛመዱም")
                                        captchaCode = generateCaptcha()
                                        captchaInput = ""
                                    } else {
                                        errorMsg = vm.attemptLogin(email, password)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("login_submit_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(AppStrings.t(lang, "signIn"))
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TextButton(
                                    onClick = { showForgotPw = true },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("forgot_password_button"),
                                ) {
                                    Text(AppStrings.t(lang, "forgotPassword"))
                                }
                                TextButton(
                                    onClick = { showCreateOrg = true },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("create_org_button"),
                                ) {
                                    Text(AppStrings.t(lang, "createAccount"))
                                }
                            }
                        }
                    }
                }

                // Quick Demo Accounts Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(AppStrings.t(lang, "demoAccounts"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            AppStrings.pick(lang, "Tap any role below to inspect RBAC tenant isolation, MFA TOTP verification, and workflows:", "ሚናዎችን፣ የተቋም መለያየትንና የMFA ማረጋገጫን ለመሞከር አንዱን ይንኩ፦"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        data.users.take(6).forEach { demo ->
                            val orgName = data.organizations.find { it.id == demo.organizationId }?.name ?: "All Organizations"
                            OutlinedCard(
                                onClick = {
                                    errorMsg = null
                                    mfaCode = ""
                                    mfaHintCode = null
                                    vm.quickDemoLogin(demo)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("demo_login_${demo.id}"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        if (demo.mfaEnabled) Icons.Default.LockPerson else Icons.Default.AccountCircle,
                                        contentDescription = demo.role.shortLabel,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${demo.name} · ${demo.role.shortLabel}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${demo.email} · $orgName",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (demo.mfaEnabled) {
                                        StatusPill("MFA", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateOrg) {
        CreateOrgAccountDialog(
            onDismiss = { showCreateOrg = false },
            onSubmit = { orgName, sector, adminName, regEmail, regPw ->
                val err = vm.registerOrgAndSignIn(orgName, sector, adminName, regEmail, regPw)
                if (err == null) showCreateOrg = false
                err
            },
        )
    }

    if (showForgotPw) {
        ForgotPasswordDialog(
            onDismiss = { showForgotPw = false },
            onSubmit = { resetEmail, newPw ->
                val err = vm.resetPassword(resetEmail, newPw)
                if (err == null) showForgotPw = false
                err
            },
        )
    }
}

@Composable
private fun CreateOrgAccountDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String) -> String?,
) {
    var orgName by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("Banking") }
    var adminName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Organization Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = orgName, onValueChange = { orgName = it }, label = { Text("Organization name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_org_name"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Banking", "Microfinance", "Telecom", "SME").forEach { s ->
                        FilterChip(selected = sector == s, onClick = { sector = s }, label = { Text(s, maxLines = 1) })
                    }
                }
                OutlinedTextField(value = adminName, onValueChange = { adminName = it }, label = { Text("Admin full name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_admin_name"))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Admin email") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_email"))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (>=10 chars, letters+numbers)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_password"))
            }
        },
        confirmButton = {
            Button(onClick = { err = onSubmit(orgName, sector, adminName, email, password) }, modifier = Modifier.testTag("reg_submit_button")) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> String?,
) {
    var email by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Account Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Registered email") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reset_email_input"))
                OutlinedTextField(value = newPw, onValueChange = { newPw = it }, label = { Text("New password (>=10 chars)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reset_new_pw_input"))
            }
        },
        confirmButton = {
            Button(onClick = { err = onSubmit(email, newPw) }, modifier = Modifier.testTag("reset_submit_button")) {
                Text("Update Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
