package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdwaRepository
import com.example.data.AdwaState
import com.example.data.SeedData
import com.example.data.local.ComplianceSnapshotEntity
import com.example.domain.GeminiWorkflows
import com.example.domain.PdfReportGenerator
import com.example.domain.PostureProbeEngine
import com.example.domain.RbacMatrix
import com.example.domain.SlaEngine
import com.example.domain.TotpHelper
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val AI_TAG = "AdwaGeminiVm"

enum class Screen {
    LOGIN,
    DASHBOARD,
    MONITORS,
    TRUST_AI,
    SOC,
    HUB,
    AI_ASSISTANT,
    CATALOGUE,
    ORGANIZATIONS,
    SCANS,
    FINDINGS,
    EVIDENCE,
    REPORT,
    AWARENESS,
    LEARN,
    TRACK_C,
    TEAM,
    AUDIT,
}

sealed interface GeminiUiState {
    data object Idle : GeminiUiState
    data object Loading : GeminiUiState
    data class Streaming(val text: String) : GeminiUiState
    data class Success(val text: String) : GeminiUiState
    data class Error(val message: String) : GeminiUiState
}

data class GeminiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class PolicyDraftResult(
    val ruleCode: String,
    val orgId: String,
    val title: String,
    val summary: String,
    val policyText: String,
    val checklist: List<String>,
)

sealed interface PolicyDraftState {
    data object Idle : PolicyDraftState
    data object Loading : PolicyDraftState
    data class Ready(val draft: PolicyDraftResult) : PolicyDraftState
    data class Error(val message: String) : PolicyDraftState
}

data class EvidenceInspectResult(
    val ruleCode: String,
    val verdict: String,
    val confidencePct: Int,
    val observedControls: List<String>,
    val missingGaps: List<String>,
)

sealed interface EvidenceInspectState {
    data object Idle : EvidenceInspectState
    data object Loading : EvidenceInspectState
    data class Ready(val result: EvidenceInspectResult) : EvidenceInspectState
    data class Error(val message: String) : EvidenceInspectState
}

data class AdwaUiState(
    val currentUserId: String? = "usr_super",
    val pendingMfaUserId: String? = null,
    val currentScreen: Screen = Screen.DASHBOARD,
    val isPublicLearnMode: Boolean = false,
    val lang: Lang = Lang.EN,
    val darkModeOverride: Boolean? = true,
    val activeScanId: String? = null,
    val preselectedOrgId: String? = null,
    val preselectedFramework: String? = null,
    val selectedReportScanId: String? = null,
    val activeLearnerId: String? = null,
    val activeLearnClassId: String? = null,
    val snackbarMessage: String? = null,
    val monitorsTab: Int = 0,
    val trustAiTab: Int = 0,
    val isSweepingMonitors: Boolean = false,
    val probeUrl: String = "https://www.ethio-cert.gov.et",
    val enrichingIncidentId: String? = null,
)

class AdwaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AdwaRepository(application)
    val dataState: StateFlow<AdwaState> = repo.state

    val roomRiskRegister: StateFlow<List<RiskRegisterEntry>> = repo.observeRiskRegister()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.state.value.riskRegister)

    val roomComplianceSnapshots: StateFlow<List<ComplianceSnapshotEntity>> = repo.observeComplianceSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow(AdwaUiState())
    val ui: StateFlow<AdwaUiState> = _ui.asStateFlow()

    private val generativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Adwa Cyber AI Co-Pilot, a senior cybersecurity compliance and SOC advisor " +
                        "specializing in Ethiopian Proclamation 1321/2024 (Personal Data Protection), " +
                        "National Bank of Ethiopia NBE-IT Directive requirements, Proclamation 1426/2026 remediation SLAs, " +
                        "NIST SP 800-61 incident response, and Ethio-CERT coordination. " +
                        "Provide clear, structured, actionable guidance in English or Amharic matching the user's request."
                )
            },
        )
    }

    private val policyModel by lazy { GeminiWorkflows.createPolicyModel() }
    private val visionInspectorModel by lazy { GeminiWorkflows.createVisionInspectorModel() }
    private val socEnrichModel by lazy { GeminiWorkflows.createSocEnrichModel() }

    private val _geminiState = MutableStateFlow<GeminiUiState>(GeminiUiState.Idle)
    val geminiState: StateFlow<GeminiUiState> = _geminiState.asStateFlow()

    private val _geminiMessages = MutableStateFlow<List<GeminiChatMessage>>(emptyList())
    val geminiMessages: StateFlow<List<GeminiChatMessage>> = _geminiMessages.asStateFlow()

    private val _policyDraftState = MutableStateFlow<PolicyDraftState>(PolicyDraftState.Idle)
    val policyDraftState: StateFlow<PolicyDraftState> = _policyDraftState.asStateFlow()

    private val _evidenceInspectState = MutableStateFlow<EvidenceInspectState>(EvidenceInspectState.Idle)
    val evidenceInspectState: StateFlow<EvidenceInspectState> = _evidenceInspectState.asStateFlow()

    private var activeAiJob: Job? = null

    init {
        // Record Super Admin active session audit trail without blocking startup
        repo.markUserLogin("usr_super")
    }

    fun currentUser(): UserAccount? {
        val uid = _ui.value.currentUserId ?: return null
        return dataState.value.users.find { it.id == uid }
    }

    fun pendingMfaUser(): UserAccount? {
        val uid = _ui.value.pendingMfaUserId ?: return null
        return dataState.value.users.find { it.id == uid }
    }

    fun can(permission: Permission): Boolean = RbacMatrix.can(currentUser()?.role, permission)

    fun allowedScreens(): List<Screen> {
        val u = currentUser() ?: return listOf(Screen.LOGIN, Screen.LEARN)
        val r = u.role
        return buildList {
            add(Screen.DASHBOARD)
            add(Screen.MONITORS)
            add(Screen.TRUST_AI)
            add(Screen.SOC)
            add(Screen.HUB)
            add(Screen.CATALOGUE)
            add(Screen.ORGANIZATIONS)
            add(Screen.SCANS)
            add(Screen.FINDINGS)
            add(Screen.EVIDENCE)
            add(Screen.REPORT)
            if (RbacMatrix.can(r, Permission.AWARENESS_MANAGE)) add(Screen.AWARENESS)
            add(Screen.LEARN)
            if (RbacMatrix.can(r, Permission.GOVERNANCE_VIEW) || RbacMatrix.can(r, Permission.CII_VIEW_OWN)) add(Screen.TRACK_C)
            if (RbacMatrix.can(r, Permission.USERS_VIEW_ALL) || RbacMatrix.can(r, Permission.USERS_VIEW_ORG)) add(Screen.TEAM)
            if (RbacMatrix.can(r, Permission.AUDIT_VIEW_ALL) || RbacMatrix.can(r, Permission.AUDIT_VIEW_ORG)) add(Screen.AUDIT)
        }
    }

    fun visibleOrganizations(): List<Organization> {
        val u = currentUser() ?: return emptyList()
        val all = dataState.value.organizations
        return if (can(Permission.ORGS_VIEW_ALL)) all else all.filter { it.id == u.organizationId }
    }

    fun visibleScans(): List<ComplianceScan> {
        val u = currentUser() ?: return emptyList()
        val all = dataState.value.scans
        return if (can(Permission.SCANS_VIEW_ALL)) all else all.filter { it.organizationId == u.organizationId }
    }

    fun visibleEvidence(): List<EvidenceDocument> {
        val u = currentUser() ?: return emptyList()
        val all = dataState.value.evidenceDocs
        return if (can(Permission.EVIDENCE_VIEW_ALL)) all else all.filter { it.organizationId == u.organizationId }
    }

    fun visibleIncidents(): List<SecurityIncident> {
        val u = currentUser() ?: return emptyList()
        val all = dataState.value.incidents
        return if (can(Permission.SOC_VIEW_ALL)) all else all.filter { it.organizationId == u.organizationId }
    }

    fun visibleIndicators(): List<ThreatIndicator> {
        val u = currentUser() ?: return emptyList()
        val all = dataState.value.threatIndicators
        if (can(Permission.SOC_VIEW_ALL)) return all
        return all.filter { ind ->
            ind.sharing == "HUB" || ind.organizationId == u.organizationId || u.organizationId in ind.sharedWithOrgIds
        }
    }

    // ── Navigation & Shell ────────────────────────────────────────────────────

    fun navigate(screen: Screen) {
        _ui.update { it.copy(currentScreen = screen, isPublicLearnMode = false) }
    }

    fun openPublicLearnPortal() {
        _ui.update { it.copy(currentScreen = Screen.LEARN, isPublicLearnMode = true, lang = Lang.AM) }
    }

    fun closeLearnPortal() {
        val hasUser = _ui.value.currentUserId != null
        _ui.update {
            it.copy(
                currentScreen = if (hasUser) Screen.DASHBOARD else Screen.LOGIN,
                isPublicLearnMode = false,
            )
        }
    }

    fun toggleLanguage() {
        _ui.update { it.copy(lang = if (it.lang == Lang.EN) Lang.AM else Lang.EN) }
    }

    fun setLanguage(lang: Lang) {
        _ui.update { it.copy(lang = lang) }
    }

    fun toggleDarkMode() {
        _ui.update { st ->
            val next = when (st.darkModeOverride) {
                null -> true
                true -> false
                false -> null
            }
            st.copy(darkModeOverride = next)
        }
    }

    fun toast(msg: String) {
        _ui.update { it.copy(snackbarMessage = msg) }
    }

    fun clearToast() {
        _ui.update { it.copy(snackbarMessage = null) }
    }

    // ── Auth Actions ──────────────────────────────────────────────────────────

    fun attemptLogin(email: String, password: String): String? {
        val user = dataState.value.users.find { it.email.equals(email.trim(), ignoreCase = true) }
        if (user == null || user.password != password) {
            repo.recordAuthEvent(email.trim(), user?.organizationId, "LOGIN_FAILURE", "Invalid email or password")
            return "Invalid email or password"
        }
        if (user.status != "ACTIVE") {
            repo.recordAuthEvent(user.email, user.organizationId, "LOGIN_BLOCKED", "Account is disabled")
            return "This account has been disabled"
        }
        if (user.mfaEnabled && !user.totpSecretBase32.isNullOrBlank()) {
            _ui.update { it.copy(pendingMfaUserId = user.id) }
            return null
        }
        repo.markUserLogin(user.id)
        repo.recordAuthEvent(user.email, user.organizationId, "LOGIN_SUCCESS", "Authenticated (${user.role.name})")
        _ui.update { it.copy(currentUserId = user.id, pendingMfaUserId = null, currentScreen = Screen.DASHBOARD, isPublicLearnMode = false) }
        return null
    }

    fun quickDemoLogin(user: UserAccount) {
        if (user.status != "ACTIVE") {
            toast("Account is disabled")
            return
        }
        if (user.mfaEnabled && !user.totpSecretBase32.isNullOrBlank()) {
            _ui.update { it.copy(pendingMfaUserId = user.id) }
            return
        }
        repo.markUserLogin(user.id)
        repo.recordAuthEvent(user.email, user.organizationId, "LOGIN_SUCCESS", "Demo login (${user.role.name})")
        _ui.update { it.copy(currentUserId = user.id, pendingMfaUserId = null, currentScreen = Screen.DASHBOARD, isPublicLearnMode = false) }
    }

    fun verifyMfa(code: String): String? {
        val user = pendingMfaUser() ?: return "No active MFA challenge"
        val secret = user.totpSecretBase32 ?: ""
        val totpOk = TotpHelper.verifyTotp(secret, code)
        val backupOk = if (!totpOk) repo.consumeBackupCode(user.id, code) else false
        if (!totpOk && !backupOk) {
            repo.recordAuthEvent(user.email, user.organizationId, "MFA_FAILURE", "Invalid 6-digit TOTP or backup code")
            return "Invalid 6-digit authenticator code or backup code"
        }
        repo.markUserLogin(user.id)
        repo.recordAuthEvent(user.email, user.organizationId, "MFA_VERIFIED", if (backupOk) "Authenticated with single-use backup code" else "Authenticated with TOTP code")
        _ui.update { it.copy(currentUserId = user.id, pendingMfaUserId = null, currentScreen = Screen.DASHBOARD, isPublicLearnMode = false) }
        return null
    }

    fun cancelMfa() {
        _ui.update { it.copy(pendingMfaUserId = null) }
    }

    fun logout() {
        currentUser()?.let { u ->
            repo.recordAuthEvent(u.email, u.organizationId, "LOGOUT", "User signed out")
        }
        _ui.update {
            AdwaUiState(
                currentUserId = null,
                currentScreen = Screen.LOGIN,
                lang = it.lang,
                darkModeOverride = it.darkModeOverride,
            )
        }
    }

    fun registerOrgAndSignIn(orgName: String, sector: String, adminName: String, email: String, password: String): String? {
        if (orgName.isBlank() || adminName.isBlank() || email.isBlank()) return "All fields are required"
        if (password.length < 10 || !password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            return "Password must be at least 10 characters with letters and numbers"
        }
        if (dataState.value.users.any { it.email.equals(email.trim(), ignoreCase = true) }) {
            return "An account with this email already exists"
        }
        val user = repo.registerOrganizationAccount(orgName, sector, adminName, email, password)
        _ui.update { it.copy(currentUserId = user.id, pendingMfaUserId = null, currentScreen = Screen.DASHBOARD) }
        toast("Workspace created & signed in")
        return null
    }

    fun resetPassword(email: String, newPassword: String): String? {
        if (newPassword.length < 10 || !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() }) {
            return "Password must be at least 10 characters with letters and numbers"
        }
        val ok = repo.resetPasswordByEmail(email, newPassword)
        return if (ok) {
            toast("Password updated. You can sign in now.")
            null
        } else {
            "No account found for that email"
        }
    }

    fun enableMfaForCurrentUser(secret: String, code: String): Pair<List<String>?, String?> {
        val u = currentUser() ?: return null to "Not signed in"
        if (!TotpHelper.verifyTotp(secret, code)) {
            return null to "Code does not match secret. Check the current 6 digits."
        }
        val backups = TotpHelper.generateBackupCodes()
        repo.enableMfa(u.id, secret, backups)
        toast("Two-factor authentication enabled")
        return backups to null
    }

    fun disableMfaForCurrentUser(password: String): String? {
        val u = currentUser() ?: return "Not signed in"
        if (u.password != password) return "Incorrect password"
        repo.disableMfa(u.id)
        toast("Two-factor authentication disabled")
        return null
    }

    fun changeCurrentPassword(currentPw: String, newPw: String): String? {
        val u = currentUser() ?: return "Not signed in"
        if (u.password != currentPw) return "Current password is incorrect"
        if (newPw.length < 10 || !newPw.any { it.isLetter() } || !newPw.any { it.isDigit() }) {
            return "New password must be >= 10 characters with letters & numbers"
        }
        repo.changePassword(u.id, newPw)
        toast("Password updated")
        return null
    }

    fun addTeamUser(name: String, email: String, password: String, role: AppRole, orgId: String?): String? {
        val actor = currentUser() ?: return "Not signed in"
        if (name.isBlank() || email.isBlank()) return "Name and email required"
        if (password.length < 10 || !password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            return "Password must be >= 10 chars with letters & numbers"
        }
        if (dataState.value.users.any { it.email.equals(email.trim(), ignoreCase = true) }) {
            return "Email already registered"
        }
        val effectiveOrgId = if (actor.role == AppRole.SUPER_ADMIN) orgId else actor.organizationId
        repo.addUser(email, name, role, effectiveOrgId, password, actor.email)
        toast("User provisioned")
        return null
    }

    fun toggleTeamUserStatus(userId: String) {
        val actor = currentUser() ?: return
        if (userId == actor.id) {
            toast("You cannot disable your own account")
            return
        }
        repo.toggleUserStatus(userId, actor.email)
    }

    fun changeTeamUserRole(userId: String, role: AppRole) {
        val actor = currentUser() ?: return
        repo.changeUserRole(userId, role, actor.email)
    }

    // ── Organizations & Scans ─────────────────────────────────────────────────

    fun addOrganization(name: String, nameAm: String, sector: String, tier: String, email: String, notes: String) {
        repo.addOrganization(name, nameAm, sector, tier, email, notes)
        toast("Organization added")
    }

    fun launchNewScanForOrg(orgId: String, frameworkCode: String = "PD-1321") {
        _ui.update {
            it.copy(
                currentScreen = Screen.SCANS,
                activeScanId = null,
                preselectedOrgId = orgId,
                preselectedFramework = frameworkCode,
            )
        }
    }

    fun openScan(scanId: String) {
        _ui.update { it.copy(currentScreen = Screen.SCANS, activeScanId = scanId) }
    }

    fun startDraftScan(orgId: String, frameworkCode: String, scanName: String) {
        val u = currentUser() ?: return
        val id = repo.startDraftScan(orgId, frameworkCode, scanName, u.name)
        _ui.update { it.copy(activeScanId = id) }
    }

    fun saveDraftAnswer(scanId: String, ruleCode: String, answer: Answer) = repo.saveDraftAnswer(scanId, ruleCode, answer)
    fun saveDraftNote(scanId: String, ruleCode: String, note: String) = repo.saveDraftNote(scanId, ruleCode, note)

    fun discardDraftScan(scanId: String) {
        repo.discardDraftScan(scanId)
        _ui.update { it.copy(activeScanId = null) }
        toast("Draft discarded")
    }

    fun submitDraftScan(scanId: String) {
        val done = repo.submitDraftScan(scanId)
        if (done != null) {
            _ui.update { it.copy(activeScanId = done.id, selectedReportScanId = done.id) }
            toast("Compliance Engine scored ${done.overallScore}% (${done.findingsCount} findings)")
        }
    }

    fun clearActiveScan() {
        _ui.update { it.copy(activeScanId = null) }
    }

    fun openExecutiveReport(scanId: String) {
        _ui.update { it.copy(currentScreen = Screen.REPORT, selectedReportScanId = scanId) }
    }

    fun selectReportScan(scanId: String) {
        _ui.update { it.copy(selectedReportScanId = scanId) }
    }

    fun updateFindingStatus(scanId: String, resultId: String, status: String, waiverNote: String? = null) {
        repo.updateFindingStatus(scanId, resultId, status, waiverNote)
        toast("Finding marked $status")
    }

    // ── Evidence Library ──────────────────────────────────────────────────────

    fun uploadEvidenceFromUri(
        context: Context,
        uri: Uri?,
        title: String,
        description: String,
        category: EvidenceCategory,
        tags: String,
        orgId: String,
        supersedeDocId: String? = null,
    ) {
        val u = currentUser() ?: return
        var fileName = "${title.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(24)}.txt"
        var mimeType = "text/plain"
        var bytes = "$title\n$description\nTags: $tags".toByteArray()

        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && nameIdx >= 0) fileName = c.getString(nameIdx)
                }
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bytes = stream.readBytes()
                }
            }
        }
        val doc = repo.uploadEvidence(title, description, category, tags, fileName, mimeType, bytes, orgId, u.name, supersedeDocId)
        toast("Sealed ${doc.fileName} (SHA-256 ${doc.checksumSha256.take(8)}…)")
    }

    fun verifyEvidence(docId: String) {
        repo.verifyEvidence(docId)
        toast("SHA-256 verified & freshness timestamp refreshed")
    }

    fun toggleArchiveEvidence(docId: String) = repo.toggleArchiveEvidence(docId)
    fun linkEvidenceRule(docId: String, ruleCode: String) = repo.linkEvidenceToRule(docId, ruleCode)
    fun unlinkEvidenceRule(linkId: String) = repo.unlinkEvidenceRule(linkId)

    // ── PDF Exports ───────────────────────────────────────────────────────────

    fun exportAndShareScanPdf(context: Context, scan: ComplianceScan) {
        val org = dataState.value.organizations.find { it.id == scan.organizationId }
        val fw = SeedData.frameworkFor(scan.frameworkCode)
        val rules = SeedData.rulesFor(scan.frameworkCode)
        val file: File = PdfReportGenerator.exportExecutiveReportPdf(context, scan, org, fw, rules, _ui.value.lang)
        PdfReportGenerator.sharePdf(context, file, "Adwa Executive Report — ${scan.name}")
    }

    fun exportAndShareGateDossierPdf(context: Context) {
        val file = PdfReportGenerator.exportGateDossierPdf(context, dataState.value.expansionGates)
        PdfReportGenerator.sharePdf(context, file, "Adwa Track C Governance Gate Dossier")
    }

    // ── Phase 2 SOC ───────────────────────────────────────────────────────────

    fun createIncident(orgId: String, title: String, desc: String, category: String, severity: String) {
        val u = currentUser() ?: return
        val inc = repo.createIncident(orgId, title, desc, category, severity, u.name)
        toast("${inc.ref} created (${inc.priorityLabel} · ${inc.priorityScore.toInt()})")
    }

    fun notifyIncidentOrg(incidentId: String) {
        val u = currentUser() ?: return
        repo.notifyIncidentOrg(incidentId, u.name)
        toast("Organization notified & RQ3 latency recorded")
    }

    fun advanceIncident(incidentId: String, note: String? = null) {
        val u = currentUser() ?: return
        repo.advanceIncident(incidentId, u.name, note)
    }

    fun addIncidentNote(incidentId: String, note: String) {
        val u = currentUser() ?: return
        if (note.isNotBlank()) repo.addIncidentNote(incidentId, note, u.name)
    }

    fun contributeIndicator(
        type: String, value: String, title: String, category: String, severity: String, tlp: String, confidence: Int, orgId: String?, p2pIds: List<String>
    ) {
        val ind = repo.contributeThreatIndicator(type, value, title, category, severity, tlp, confidence, orgId, p2pIds)
        toast("Published ${ind.sharing} indicator (${ind.tlp})")
    }

    fun recordIndicatorSighting(id: String) = repo.recordIndicatorSighting(id)
    fun toggleIndicatorActive(id: String) = repo.toggleIndicatorActive(id)

    fun pullEthioCertFeed() {
        val run = repo.pullEthioCertFeed()
        toast(run.detail)
    }

    fun reportUpwardEthioCert() {
        val u = currentUser() ?: return
        val orgId = if (can(Permission.SOC_VIEW_ALL)) null else u.organizationId
        val run = repo.reportUpwardToEthioCert(orgId)
        toast(run.detail)
    }

    // ── Phase 3 Awareness & Learn ─────────────────────────────────────────────

    fun addAwarenessSchoolAndClass(schoolName: String, subCity: String, className: String, grade: Int, groupType: String, teacherName: String) {
        repo.addAwarenessSchoolAndClass(schoolName, subCity, className, grade, groupType, teacherName)
        toast("School & class created")
    }

    fun issueLearnerCodes(classId: String, count: Int) {
        repo.issueLearnerCodes(classId, count)
        toast("Issued $count learner code(s)")
    }

    fun updateLearnerConsent(learnerId: String, consent: String) = repo.updateLearnerConsent(learnerId, consent)

    fun joinLearnClass(joinCode: String): String? {
        val cls = dataState.value.awarenessClasses.find { it.joinCode.equals(joinCode.trim(), ignoreCase = true) }
            ?: return "Class join code not found (try ADWA-M2S-9A)"
        _ui.update { it.copy(activeLearnClassId = cls.id) }
        return null
    }

    fun signInLearnerCode(learnerCode: String): String? {
        val lrn = dataState.value.awarenessLearners.find { it.code.equals(learnerCode.trim(), ignoreCase = true) }
            ?: return "Participant code not found (try AW-DEMO01 or AW-DEMO02)"
        _ui.update { it.copy(activeLearnerId = lrn.id, activeLearnClassId = lrn.classId) }
        return null
    }

    fun clearLearnerSession() {
        _ui.update { it.copy(activeLearnerId = null) }
    }

    fun submitLearnerAssessment(learnerId: String, phase: String, correct: Int, total: Int) {
        repo.submitLearnerAssessment(learnerId, phase, correct, total)
        toast("Assessment recorded ($correct/$total)")
    }

    fun completeModuleQuiz(learnerId: String, moduleCode: String, score: Double) {
        repo.recordModuleQuizCompletion(learnerId, moduleCode, score)
        toast("$moduleCode completed (${score.toInt()}%)")
    }

    fun pingTool(toolId: String) {
        repo.recordToolPing(toolId, _ui.value.lang.name.lowercase())
    }

    // ── Phase 4 & 5 Track C ───────────────────────────────────────────────────

    fun transitionGate(gateId: String, toStatus: String, note: String?) {
        val u = currentUser() ?: return
        val err = repo.transitionExpansionGate(gateId, toStatus, note, u.name)
        toast(err ?: "Gate status updated to $toStatus")
    }

    fun attachGateEvidenceFromUri(context: Context, gateId: String, uri: Uri?, description: String?) {
        val u = currentUser() ?: return
        var fileName = "gate_verification_artefact.txt"
        var bytes = "Gate $gateId verification artefact ($description)".toByteArray()
        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && nameIdx >= 0) fileName = c.getString(nameIdx)
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bytes = stream.readBytes()
                }
            }
        }
        repo.attachGateEvidence(gateId, fileName, description, bytes, u.name)
        toast("Attached $fileName")
    }

    fun updateAcfsItemStatus(itemId: String, newStatus: String) = repo.updateAcfsItemStatus(itemId, newStatus)

    fun recordAcfsDecision(decision: String, rationale: String) {
        val u = currentUser() ?: return
        if (rationale.isBlank()) {
            toast("Rationale is required")
            return
        }
        repo.recordAcfsBoardDecision(decision, rationale, u.name)
        toast("Board decision logged: $decision")
    }

    fun advanceCiiMou(operatorId: String, mouId: String) = repo.advanceCiiMouStatus(operatorId, mouId)
    fun advanceCiiAuth(operatorId: String, authId: String) = repo.advanceCiiAuthorizationStatus(operatorId, authId)
    fun advanceCiiSystem(operatorId: String, systemId: String) {
        val err = repo.advanceCiiSystemReadiness(operatorId, systemId)
        if (err != null) toast(err) else toast("System readiness advanced")
    }

    fun resetDemoData() {
        repo.resetDemoData()
        repo.markUserLogin("usr_super")
        _ui.update {
            AdwaUiState(
                currentUserId = "usr_super",
                currentScreen = Screen.DASHBOARD,
                lang = it.lang,
                darkModeOverride = it.darkModeOverride,
            )
        }
        toast("Demo state restored (Super Admin active)")
    }

    // ── Firebase AI Logic (Gemini 3.8 Flash) ──────────────────────────────────

    fun buildPostureContextSummary(): String {
        val state = dataState.value
        val orgs = visibleOrganizations()
        val scans = visibleScans().filter { it.status == "complete" }
        val avgScore = if (scans.isNotEmpty()) scans.mapNotNull { it.overallScore }.average() else 0.0
        val ruleByCode = SeedData.ALL_RULES.associateBy { it.code }
        val openFindings = scans.flatMap { sc -> sc.results.filter { it.isFinding && it.status != "remediated" && it.status != "waived" } }
        val slaInfos = scans.flatMap { sc ->
            sc.results.filter { it.isFinding }.map { res ->
                SlaEngine.slaInfo(res.status, res.dueAtEpochMs, res.remediatedAtEpochMs, ruleByCode[res.ruleCode]?.severity)
            }
        }
        val sla = SlaEngine.summarizeSla(slaInfos)
        val openIncidents = visibleIncidents().filter { it.status != IncidentStatus.CLOSED }
        val topGaps = openFindings
            .take(5)
            .mapNotNull { res -> ruleByCode[res.ruleCode]?.let { "${it.code} (${it.severity}): ${it.titleEn}" } }
            .joinToString("; ")

        return buildString {
            appendLine("Live Adwa Cyber Posture Snapshot:")
            appendLine("- Signed-in Operator: ${currentUser()?.name ?: "Operator"} (${currentUser()?.role?.shortLabel ?: "Admin"})")
            appendLine("- Monitored Organizations: ${orgs.size} (${orgs.joinToString(", ") { it.name }})")
            appendLine("- Completed Scans: ${scans.size}, Avg Compliance Score: ${String.format("%.1f", avgScore)}%")
            appendLine("- Open Findings: ${openFindings.size}, SLA Overdue: ${sla.overdue}, Due Soon: ${sla.dueSoon}")
            if (topGaps.isNotBlank()) appendLine("- Sample Open Rule Gaps: $topGaps")
            appendLine("- Active SOC Incidents: ${openIncidents.size} (${openIncidents.take(4).joinToString(", ") { "${it.ref}:${it.severity}" }})")
            appendLine("- Threat Indicators Tracked: ${visibleIndicators().size}")
            appendLine("- UI Language Preference: ${if (_ui.value.lang == Lang.AM) "Amharic (አማርኛ)" else "English"}")
        }
    }

    fun askGemini(userPrompt: String, includeLiveContext: Boolean = true) {
        val trimmed = userPrompt.trim()
        if (trimmed.isBlank()) return

        activeAiJob?.cancel()
        val userMsg = GeminiChatMessage(isUser = true, text = trimmed)
        _geminiMessages.update { it + userMsg }

        activeAiJob = viewModelScope.launch {
            _geminiState.value = GeminiUiState.Loading
            val accumulatedText = StringBuilder()
            val fullPrompt = if (includeLiveContext) {
                "${buildPostureContextSummary()}\n\nUser Question:\n$trimmed"
            } else {
                trimmed
            }

            try {
                withTimeout(60_000L) {
                    generativeModel.generateContentStream(fullPrompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulatedText.append(chunkText)
                            _geminiState.value = GeminiUiState.Streaming(accumulatedText.toString())
                        }
                    }
                }
                val finalText = accumulatedText.toString().trim()
                if (finalText.isNotBlank()) {
                    _geminiMessages.update { it + GeminiChatMessage(isUser = false, text = finalText) }
                    _geminiState.value = GeminiUiState.Success(finalText)
                } else {
                    _geminiState.value = GeminiUiState.Error("Gemini returned an empty response. Please try again.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Gemini generation timed out after 60s", e)
                _geminiState.value = GeminiUiState.Error("Request timed out after 60 seconds. Check connectivity and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Firebase AI Logic generation failed", e)
                _geminiState.value = GeminiUiState.Error(e.localizedMessage ?: "Gemini request failed.")
            }
        }
    }

    fun cancelGeminiGeneration() {
        activeAiJob?.cancel()
        activeAiJob = null
        val partial = (_geminiState.value as? GeminiUiState.Streaming)?.text?.trim()
        if (!partial.isNullOrBlank()) {
            _geminiMessages.update { it + GeminiChatMessage(isUser = false, text = "$partial\n\n[Generation stopped]") }
        }
        _geminiState.value = GeminiUiState.Idle
    }

    fun clearGeminiConversation() {
        activeAiJob?.cancel()
        activeAiJob = null
        _geminiMessages.value = emptyList()
        _geminiState.value = GeminiUiState.Idle
    }

    // ── Enterprise GRC: CCM, Cross-Map, Risk Register, Trust Center & HRS ─────

    fun openMonitorsTab(tabIndex: Int) {
        _ui.update { it.copy(currentScreen = Screen.MONITORS, monitorsTab = tabIndex, isPublicLearnMode = false) }
    }

    fun openTrustAiTab(tabIndex: Int) {
        _ui.update { it.copy(currentScreen = Screen.TRUST_AI, trustAiTab = tabIndex, isPublicLearnMode = false) }
    }

    fun setProbeUrl(url: String) {
        _ui.update { it.copy(probeUrl = url) }
    }

    fun runPostureSweep(context: Context, customUrl: String? = null) {
        if (_ui.value.isSweepingMonitors) return
        val url = (customUrl ?: _ui.value.probeUrl).trim().ifEmpty { "https://www.ethio-cert.gov.et" }
        _ui.update { it.copy(isSweepingMonitors = true, probeUrl = url) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val updated = PostureProbeEngine.runAllProbes(
                    context = context,
                    monitors = dataState.value.controlMonitors,
                    probeUrl = url,
                    users = dataState.value.users,
                    evidenceDocs = dataState.value.evidenceDocs,
                )
                val passed = updated.count { it.status == "PASS" }
                val warned = updated.count { it.status == "WARN" }
                val failed = updated.count { it.status == "FAIL" }
                val run = MonitorCheckRun(
                    id = "run_${UUID.randomUUID().toString().take(8)}",
                    monitorCount = updated.size,
                    passed = passed,
                    warned = warned,
                    failed = failed,
                    probeUrl = url,
                    durationMs = System.currentTimeMillis() - started,
                    summary = "$passed pass · $warned warn · $failed fail ($url)",
                    executedAtEpochMs = System.currentTimeMillis(),
                )
                repo.saveMonitorSweep(updated, run)
                toast("Posture sweep complete: $passed/${updated.size} passing")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Monitor sweep failed", e)
                toast("Posture sweep error: ${e.localizedMessage}")
            } finally {
                _ui.update { it.copy(isSweepingMonitors = false) }
            }
        }
    }

    fun createRiskFromMonitor(monitor: ControlMonitor) {
        val actor = currentUser()?.name ?: "Super Admin"
        val fwList = monitor.mappedRuleCodes.map { if (it.startsWith("NBE")) "NBE-IT" else "PD-1321" }.distinct()
        val entry = repo.addRiskEntry(
            titleEn = "${monitor.code}: ${monitor.titleEn}",
            description = monitor.detail,
            frameworks = fwList,
            linkedRuleCodes = monitor.mappedRuleCodes,
            likelihood = if (monitor.status == "FAIL") 4 else 3,
            impact = if (monitor.status == "FAIL") 5 else 4,
            treatment = "MITIGATE",
            ownerName = actor,
            sourceMonitorId = monitor.id,
        )
        toast("Logged ${entry.code} to Risk Register")
    }

    fun addCustomRisk(
        titleEn: String,
        description: String,
        frameworks: List<String>,
        linkedRuleCodes: List<String>,
        likelihood: Int,
        impact: Int,
        treatment: String,
    ) {
        if (titleEn.isBlank()) {
            toast("Risk title is required")
            return
        }
        val actor = currentUser()?.name ?: "Super Admin"
        val entry = repo.addRiskEntry(titleEn, description, frameworks, linkedRuleCodes, likelihood, impact, treatment, actor)
        toast("Created risk ${entry.code} (Residual ${entry.residualScore}/25)")
    }

    fun updateRiskStatus(riskId: String, status: String, treatment: String? = null) {
        repo.updateRiskStatus(riskId, status, treatment)
        toast("Risk updated to $status")
    }

    fun deleteRiskEntry(riskId: String) {
        repo.deleteRiskEntry(riskId)
        toast("Risk removed from Risk Register")
    }

    fun updateTrustCenter(transform: (TrustCenterConfig) -> TrustCenterConfig) = repo.updateTrustCenterConfig(transform)

    fun publishTrustCenterSnapshot() {
        val monitors = dataState.value.controlMonitors
        val scans = visibleScans().filter { it.status == "complete" }
        val avg = if (scans.isNotEmpty()) scans.mapNotNull { it.overallScore }.average() else 0.0
        repo.publishTrustCenterSnapshot("score=$avg|monitors=${monitors.count { it.status == "PASS" }}/${monitors.size}|ts=${System.currentTimeMillis()}")
        toast("Trust Center snapshot sealed & published")
    }

    fun exportAndShareTrustCenterPdf(context: Context) {
        val cfg = dataState.value.trustCenter ?: SeedData.buildInitialState().trustCenter!!
        val scans = visibleScans().filter { it.status == "complete" }
        val avg = if (scans.isNotEmpty()) scans.mapNotNull { it.overallScore }.average() else 0.0
        val openRisks = dataState.value.riskRegister.count { it.status == "OPEN" || it.status == "IN_PROGRESS" }
        val activeIncs = visibleIncidents().count { it.status != IncidentStatus.CLOSED }
        val file = PdfReportGenerator.exportTrustCenterDossierPdf(
            context = context,
            config = cfg,
            avgScore = avg,
            monitors = dataState.value.controlMonitors,
            openRisks = openRisks,
            activeIncidents = activeIncs,
        )
        PdfReportGenerator.sharePdf(context, file, "Adwa Trust Center Assurance Dossier")
    }

    fun togglePlaybookStep(incidentId: String, stepId: String) = repo.toggleIncidentPlaybookStep(incidentId, stepId)

    fun launchPhishingCampaign(titleEn: String, targetGroup: String, sentCount: Int, reportedCount: Int, clickedCount: Int) {
        if (titleEn.isBlank()) {
            toast("Campaign title is required")
            return
        }
        repo.launchAwarenessCampaign(titleEn, targetGroup, sentCount, reportedCount, clickedCount)
        toast("Campaign launched & Human Risk Score recalculated")
    }

    fun draftPolicyWithGemini(ruleCode: String, orgId: String) {
        val rule = SeedData.ALL_RULES.find { it.code == ruleCode } ?: return
        val org = dataState.value.organizations.find { it.id == orgId }
        val langLabel = if (_ui.value.lang == Lang.AM) "Amharic and English" else "English"
        viewModelScope.launch {
            _policyDraftState.value = PolicyDraftState.Loading
            try {
                val prompt = "Draft an institutional cybersecurity compliance policy satisfying obligation [${rule.code}] " +
                    "'${rule.titleEn}' (${rule.frameworkCode}, ${rule.article}) for organization '${org?.name ?: "Ethiopian Financial Institution"}' " +
                    "(sector: ${org?.sector ?: "Banking"}). Write clearly in $langLabel. Statement: ${rule.statementEn}. Remediation goal: ${rule.remediationEn}"
                val resp = withTimeout(60_000L) { policyModel.generateContent(prompt) }
                val raw = resp.text ?: throw IllegalStateException("Empty policy response from Gemini.")
                val json = JSONObject(raw)
                val arr = json.getJSONArray("checklist")
                val checklist = List(arr.length()) { arr.getString(it) }
                _policyDraftState.value = PolicyDraftState.Ready(
                    PolicyDraftResult(
                        ruleCode = ruleCode,
                        orgId = orgId,
                        title = json.getString("title"),
                        summary = json.getString("summary"),
                        policyText = json.getString("policyText"),
                        checklist = checklist,
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Policy draft timed out", e)
                _policyDraftState.value = PolicyDraftState.Error("Policy drafting timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Policy draft failed", e)
                _policyDraftState.value = PolicyDraftState.Error(e.localizedMessage ?: "Failed to generate policy.")
            }
        }
    }

    fun sealDraftedPolicyToEvidence() {
        val ready = (_policyDraftState.value as? PolicyDraftState.Ready)?.draft ?: return
        val u = currentUser() ?: return
        val content = buildString {
            appendLine(ready.title)
            appendLine("Rule: ${ready.ruleCode}")
            appendLine()
            appendLine("Summary: ${ready.summary}")
            appendLine()
            appendLine(ready.policyText)
            appendLine()
            appendLine("Verification Checklist:")
            ready.checklist.forEach { appendLine("- [ ] $it") }
        }
        val slug = ready.ruleCode.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val doc = repo.uploadEvidence(
            title = ready.title,
            description = ready.summary,
            category = EvidenceCategory.POLICY,
            tags = "${ready.ruleCode},ai-policy,verified",
            fileName = "policy_$slug.txt",
            mimeType = "text/plain",
            bytes = content.toByteArray(),
            organizationId = ready.orgId,
            uploadedByName = "${u.name} (Gemini Draft)",
        )
        repo.linkEvidenceToRule(doc.id, ready.ruleCode)
        toast("Sealed ${doc.fileName} & linked to ${ready.ruleCode}")
    }

    fun inspectEvidenceImageWithGemini(context: Context, uri: Uri, ruleCode: String) {
        val rule = SeedData.ALL_RULES.find { it.code == ruleCode } ?: return
        viewModelScope.launch {
            _evidenceInspectState.value = EvidenceInspectState.Loading
            try {
                val bitmap = GeminiWorkflows.loadScaledBitmap(context, uri)
                    ?: throw IllegalStateException("Unable to decode selected image.")
                val prompt = content {
                    image(bitmap)
                    text(
                        "Inspect this uploaded compliance screenshot/document photo against Ethiopian regulatory obligation " +
                            "[${rule.code}] '${rule.titleEn}' (${rule.statementEn}). Identify specific visible evidence controls and any gaps."
                    )
                }
                val resp = withTimeout(60_000L) { visionInspectorModel.generateContent(prompt) }
                val raw = resp.text ?: throw IllegalStateException("Empty inspection response from Gemini.")
                val json = JSONObject(raw)
                val obsArr = json.getJSONArray("observedControls")
                val gapArr = json.getJSONArray("missingGaps")
                _evidenceInspectState.value = EvidenceInspectState.Ready(
                    EvidenceInspectResult(
                        ruleCode = ruleCode,
                        verdict = json.getString("verdict"),
                        confidencePct = json.optInt("confidencePct", 80),
                        observedControls = List(obsArr.length()) { obsArr.getString(it) },
                        missingGaps = List(gapArr.length()) { gapArr.getString(it) },
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Multimodal evidence inspection timed out", e)
                _evidenceInspectState.value = EvidenceInspectState.Error("Image inspection timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Multimodal evidence inspection failed", e)
                _evidenceInspectState.value = EvidenceInspectState.Error(e.localizedMessage ?: "Failed to inspect image evidence.")
            }
        }
    }

    fun enrichIncidentWithGemini(incidentId: String) {
        val inc = dataState.value.incidents.find { it.id == incidentId } ?: return
        viewModelScope.launch {
            _ui.update { it.copy(enrichingIncidentId = incidentId) }
            try {
                val prompt = "Enrich security incident ${inc.ref}: '${inc.title}' (category: ${inc.category}, severity: ${inc.severity}, " +
                    "status: ${inc.status.name}, description: ${inc.description}). Map to MITRE ATT&CK and Ethio-CERT/NIST SP 800-61 containment."
                val resp = withTimeout(60_000L) { socEnrichModel.generateContent(prompt) }
                val raw = resp.text ?: throw IllegalStateException("Empty enrichment response.")
                val json = JSONObject(raw)
                val actionsArr = json.getJSONArray("containmentActions")
                val actions = List(actionsArr.length()) { "• ${actionsArr.getString(it)}" }.joinToString("\n")
                val note = "${json.getString("mitreMapping")}\n${json.getString("riskAssessment")}\n$actions"
                repo.saveIncidentAiNote(incidentId, note)
                toast("Gemini enriched ${inc.ref} with MITRE & containment steps")
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Incident enrichment timed out", e)
                toast("AI enrichment timed out after 60s")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Incident enrichment failed", e)
                toast("AI enrichment error: ${e.localizedMessage ?: "failed"}")
            } finally {
                _ui.update { it.copy(enrichingIncidentId = null) }
            }
        }
    }
}
