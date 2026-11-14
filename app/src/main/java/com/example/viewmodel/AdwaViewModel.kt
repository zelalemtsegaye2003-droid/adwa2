package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.example.data.AdwaRepository
import com.example.data.AdwaState
import com.example.data.SeedData
import com.example.domain.PdfReportGenerator
import com.example.domain.RbacMatrix
import com.example.domain.TotpHelper
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

enum class Screen {
    LOGIN,
    DASHBOARD,
    CATALOGUE,
    ORGANIZATIONS,
    SCANS,
    FINDINGS,
    EVIDENCE,
    REPORT,
    SOC,
    AWARENESS,
    LEARN,
    TRACK_C,
    TEAM,
    AUDIT,
}

data class AdwaUiState(
    val currentUserId: String? = null,
    val pendingMfaUserId: String? = null,
    val currentScreen: Screen = Screen.LOGIN,
    val isPublicLearnMode: Boolean = false,
    val lang: Lang = Lang.EN,
    val darkModeOverride: Boolean? = null,
    val activeScanId: String? = null,
    val preselectedOrgId: String? = null,
    val preselectedFramework: String? = null,
    val selectedReportScanId: String? = null,
    val activeLearnerId: String? = null,
    val activeLearnClassId: String? = null,
    val snackbarMessage: String? = null,
)

class AdwaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AdwaRepository(application)
    val dataState: StateFlow<AdwaState> = repo.state

    private val _ui = MutableStateFlow(AdwaUiState())
    val ui: StateFlow<AdwaUiState> = _ui.asStateFlow()

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
            add(Screen.CATALOGUE)
            add(Screen.ORGANIZATIONS)
            add(Screen.SCANS)
            add(Screen.FINDINGS)
            add(Screen.EVIDENCE)
            add(Screen.REPORT)
            add(Screen.SOC)
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
        _ui.update { AdwaUiState(lang = it.lang, darkModeOverride = it.darkModeOverride) }
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
        _ui.update { AdwaUiState(lang = it.lang, darkModeOverride = it.darkModeOverride) }
        toast("Demo state restored")
    }
}
