package com.example.data

import android.content.Context
import com.example.data.local.AdwaDatabase
import com.example.data.local.ComplianceSnapshotEntity
import com.example.data.local.RiskRegisterRepository
import com.example.domain.*
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AdwaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val stateFile = File(appContext.filesDir, "adwa_state_v1.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = AdwaDatabase.getInstance(appContext)
    val riskRepository = RiskRegisterRepository(db.riskRegisterDao(), db.complianceDataDao())

    private val _state = MutableStateFlow(loadOrSeed())
    val state: StateFlow<AdwaState> = _state.asStateFlow()

    init {
        scope.launch {
            val initial = _state.value
            riskRepository.seedIfEmpty(
                risks = initial.riskRegister,
                scans = initial.scans,
                monitors = initial.controlMonitors,
            )
            riskRepository.allRisks.collect { roomRisks ->
                if (_state.value.riskRegister != roomRisks) {
                    updateAndPersist { st -> st.copy(riskRegister = roomRisks) }
                }
            }
        }
    }

    fun observeRiskRegister(): Flow<List<RiskRegisterEntry>> = riskRepository.allRisks

    fun observeComplianceSnapshots(): Flow<List<ComplianceSnapshotEntity>> = riskRepository.complianceSnapshots

    private fun uid(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"

    private fun updateAndPersist(transform: (AdwaState) -> AdwaState) {
        _state.update(transform)
        val snapshot = _state.value
        scope.launch {
            runCatching {
                stateFile.writeText(serialize(snapshot).toString())
            }
        }
    }

    fun resetDemoData() {
        val fresh = SeedData.buildInitialState()
        _state.value = fresh
        scope.launch {
            runCatching { stateFile.writeText(serialize(fresh).toString()) }
            runCatching {
                riskRepository.replaceAll(
                    risks = fresh.riskRegister,
                    scans = fresh.scans,
                    monitors = fresh.controlMonitors,
                )
            }
        }
    }

    // ── Auth & Users ──────────────────────────────────────────────────────────

    fun recordAuthEvent(email: String, orgId: String?, type: String, detail: String) {
        val ev = AuthEvent(uid("ae"), email, orgId, type, detail, System.currentTimeMillis())
        updateAndPersist { it.copy(authEvents = (listOf(ev) + it.authEvents).take(250)) }
    }

    fun markUserLogin(userId: String) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(users = st.users.map { if (it.id == userId) it.copy(lastLoginAtEpochMs = now) else it })
        }
    }

    fun consumeBackupCode(userId: String, code: String): Boolean {
        val clean = code.trim().uppercase()
        val u = _state.value.users.find { it.id == userId } ?: return false
        if (clean !in u.backupCodes) return false
        updateAndPersist { st ->
            st.copy(users = st.users.map {
                if (it.id == userId) it.copy(backupCodes = it.backupCodes.filterNot { c -> c == clean }) else it
            })
        }
        return true
    }

    fun enableMfa(userId: String, secretBase32: String, backupCodes: List<String>) {
        val u = _state.value.users.find { it.id == userId } ?: return
        updateAndPersist { st ->
            st.copy(users = st.users.map {
                if (it.id == userId) it.copy(mfaEnabled = true, totpSecretBase32 = secretBase32, backupCodes = backupCodes) else it
            })
        }
        recordAuthEvent(u.email, u.organizationId, "MFA_ENABLED", "Two-factor TOTP authenticator enabled")
    }

    fun disableMfa(userId: String) {
        val u = _state.value.users.find { it.id == userId } ?: return
        updateAndPersist { st ->
            st.copy(users = st.users.map {
                if (it.id == userId) it.copy(mfaEnabled = false, totpSecretBase32 = null, backupCodes = emptyList()) else it
            })
        }
        recordAuthEvent(u.email, u.organizationId, "MFA_DISABLED", "Two-factor TOTP authenticator disabled")
    }

    fun changePassword(userId: String, newPassword: String) {
        val u = _state.value.users.find { it.id == userId } ?: return
        updateAndPersist { st ->
            st.copy(users = st.users.map { if (it.id == userId) it.copy(password = newPassword) else it })
        }
        recordAuthEvent(u.email, u.organizationId, "PASSWORD_CHANGED", "Account password updated")
    }

    fun resetPasswordByEmail(email: String, newPassword: String): Boolean {
        val u = _state.value.users.find { it.email.equals(email.trim(), ignoreCase = true) } ?: return false
        updateAndPersist { st ->
            st.copy(users = st.users.map { if (it.id == u.id) it.copy(password = newPassword) else it })
        }
        recordAuthEvent(u.email, u.organizationId, "PASSWORD_RESET", "Password reset via recovery verification")
        return true
    }

    fun registerOrganizationAccount(
        orgName: String,
        sector: String,
        adminName: String,
        email: String,
        password: String,
    ): UserAccount {
        val orgId = uid("org")
        val slug = orgName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { orgId }
        val org = Organization(
            id = orgId,
            name = orgName.trim(),
            nameAm = orgName.trim(),
            slug = slug,
            sector = sector,
            tier = "Pilot",
            contactEmail = email.trim(),
            status = "Active",
            notes = "Self-registered workspace",
        )
        val user = UserAccount(
            id = uid("usr"),
            email = email.trim(),
            password = password,
            name = adminName.trim(),
            role = AppRole.ORG_ADMIN,
            organizationId = orgId,
            status = "ACTIVE",
            mfaEnabled = false,
            totpSecretBase32 = null,
            backupCodes = emptyList(),
            lastLoginAtEpochMs = System.currentTimeMillis(),
        )
        updateAndPersist { st ->
            st.copy(
                organizations = st.organizations + org,
                users = st.users + user,
            )
        }
        recordAuthEvent(user.email, orgId, "ORG_REGISTERED", "Created workspace ${org.name}")
        return user
    }

    fun addUser(
        email: String,
        name: String,
        role: AppRole,
        organizationId: String?,
        password: String,
        actorEmail: String,
    ) {
        val user = UserAccount(
            id = uid("usr"),
            email = email.trim(),
            password = password,
            name = name.trim(),
            role = role,
            organizationId = organizationId,
            status = "ACTIVE",
            mfaEnabled = false,
            totpSecretBase32 = null,
            backupCodes = emptyList(),
            lastLoginAtEpochMs = null,
        )
        updateAndPersist { it.copy(users = it.users + user) }
        recordAuthEvent(actorEmail, organizationId, "USER_CREATED", "Provisioned ${user.email} (${role.name})")
    }

    fun toggleUserStatus(userId: String, actorEmail: String) {
        val target = _state.value.users.find { it.id == userId } ?: return
        val nextStatus = if (target.status == "ACTIVE") "DISABLED" else "ACTIVE"
        updateAndPersist { st ->
            st.copy(users = st.users.map { if (it.id == userId) it.copy(status = nextStatus) else it })
        }
        recordAuthEvent(actorEmail, target.organizationId, "USER_STATUS_CHANGED", "${target.email} -> $nextStatus")
    }

    fun changeUserRole(userId: String, newRole: AppRole, actorEmail: String) {
        val target = _state.value.users.find { it.id == userId } ?: return
        updateAndPersist { st ->
            st.copy(users = st.users.map { if (it.id == userId) it.copy(role = newRole) else it })
        }
        recordAuthEvent(actorEmail, target.organizationId, "ROLE_CHANGED", "${target.email} -> ${newRole.name}")
    }

    // ── Organizations ─────────────────────────────────────────────────────────

    fun addOrganization(name: String, nameAm: String, sector: String, tier: String, email: String, notes: String?) {
        val id = uid("org")
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { id }
        val org = Organization(
            id = id,
            name = name.trim(),
            nameAm = nameAm.trim().ifEmpty { name.trim() },
            slug = slug,
            sector = sector,
            tier = tier,
            contactEmail = email.trim().ifEmpty { null },
            status = "Active",
            notes = notes?.trim()?.ifEmpty { null },
        )
        updateAndPersist { it.copy(organizations = it.organizations + org) }
    }

    // ── Scans & Findings ──────────────────────────────────────────────────────

    fun startDraftScan(orgId: String, frameworkCode: String, scanName: String, auditorName: String): String {
        val id = uid("scan")
        val scan = ComplianceScan(
            id = id,
            organizationId = orgId,
            frameworkCode = frameworkCode,
            name = scanName.trim(),
            status = "in_progress",
            auditorName = auditorName,
            overallScore = null,
            pillarScores = emptyMap(),
            findingsCount = 0,
            criticalFindings = 0,
            startedAtEpochMs = System.currentTimeMillis(),
            completedAtEpochMs = null,
            durationMinutes = null,
            results = emptyList(),
        )
        updateAndPersist { st ->
            st.copy(
                scans = listOf(scan) + st.scans,
                draftAnswers = st.draftAnswers + (id to emptyMap()),
                draftNotes = st.draftNotes + (id to emptyMap()),
            )
        }
        return id
    }

    fun saveDraftAnswer(scanId: String, ruleCode: String, answer: Answer) {
        updateAndPersist { st ->
            val current = st.draftAnswers[scanId].orEmpty()
            st.copy(draftAnswers = st.draftAnswers + (scanId to (current + (ruleCode to answer))))
        }
    }

    fun saveDraftNote(scanId: String, ruleCode: String, note: String) {
        updateAndPersist { st ->
            val current = st.draftNotes[scanId].orEmpty()
            st.copy(draftNotes = st.draftNotes + (scanId to (current + (ruleCode to note))))
        }
    }

    fun discardDraftScan(scanId: String) {
        updateAndPersist { st ->
            st.copy(
                scans = st.scans.filterNot { it.id == scanId && it.status == "in_progress" },
                draftAnswers = st.draftAnswers - scanId,
                draftNotes = st.draftNotes - scanId,
            )
        }
        scope.launch { runCatching { riskRepository.deleteSnapshot(scanId) } }
    }

    fun submitDraftScan(scanId: String): ComplianceScan? {
        val st = _state.value
        val draft = st.scans.find { it.id == scanId } ?: return null
        val fw = SeedData.frameworkFor(draft.frameworkCode) ?: return null
        val rules = SeedData.rulesFor(draft.frameworkCode)
        val answers = st.draftAnswers[scanId].orEmpty()
        val notes = st.draftNotes[scanId].orEmpty()
        val now = System.currentTimeMillis()
        val eval = ComplianceEngine.evaluate(
            scanId = scanId,
            rules = rules,
            answers = answers,
            notes = notes,
            pillars = fw.pillars,
            nowEpochMs = now,
        )
        val rawMin = maxOf(2.0, (now - draft.startedAtEpochMs) / 60_000.0)
        val durationMin = kotlin.math.round(minOf(rawMin, 55.0) * 10.0) / 10.0
        val completed = draft.copy(
            status = "complete",
            overallScore = eval.overallScore,
            pillarScores = eval.pillarScores,
            findingsCount = eval.findingsCount,
            criticalFindings = eval.criticalFindings,
            completedAtEpochMs = now,
            durationMinutes = durationMin,
            results = eval.results,
        )
        updateAndPersist { cur ->
            cur.copy(
                scans = cur.scans.map { if (it.id == scanId) completed else it },
                draftAnswers = cur.draftAnswers - scanId,
                draftNotes = cur.draftNotes - scanId,
            )
        }
        scope.launch { runCatching { riskRepository.saveSnapshot(completed) } }
        return completed
    }

    fun updateFindingStatus(scanId: String, resultId: String, newStatus: String, waiverNote: String? = null) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            val updatedScans = st.scans.map { scan ->
                if (scan.id != scanId) scan
                else {
                    val updatedResults = scan.results.map { res ->
                        if (res.id != resultId) res
                        else res.copy(
                            status = newStatus,
                            waiverNote = if (newStatus == "waived") waiverNote?.trim() else res.waiverNote,
                            remediatedAtEpochMs = if (newStatus == "remediated") now else if (newStatus == "open" || newStatus == "in_progress") null else res.remediatedAtEpochMs,
                        )
                    }
                    scan.copy(
                        results = updatedResults,
                        findingsCount = updatedResults.count { it.isFinding && it.status != "remediated" && it.status != "waived" },
                    )
                }
            }
            st.copy(scans = updatedScans)
        }
    }

    // ── Evidence Library ──────────────────────────────────────────────────────

    fun uploadEvidence(
        title: String,
        description: String,
        category: EvidenceCategory,
        tags: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        organizationId: String,
        uploadedByName: String,
        supersedeDocId: String? = null,
    ): EvidenceDocument {
        val now = System.currentTimeMillis()
        val prev = supersedeDocId?.let { id -> _state.value.evidenceDocs.find { it.id == id } }
        val newId = uid("ev")
        val rootId = prev?.rootId ?: prev?.id ?: newId
        val version = (prev?.version ?: 0) + 1
        val doc = EvidenceDocument(
            id = newId,
            title = title.trim(),
            description = description.trim(),
            category = category,
            tags = tags.trim(),
            fileName = fileName,
            mimeType = mimeType,
            fileSize = bytes.size.toLong(),
            checksumSha256 = RulesEngine.sha256Hex(bytes),
            status = "ACTIVE",
            version = version,
            rootId = rootId,
            downloads = 0,
            lastVerifiedAtEpochMs = now,
            organizationId = organizationId,
            uploadedByName = uploadedByName,
            createdAtEpochMs = now,
            contentPreview = bytes.decodeToString().take(180).replace("\n", " "),
        )
        val autoRules = RulesEngine.parseRuleRefs("$tags $title $description", SeedData.ALL_RULES)
        val newLinks = autoRules.map { code ->
            EvidenceLink(uid("el"), code, newId, "SATISFIES", "AUTO_TAG")
        }
        updateAndPersist { st ->
            val updatedDocs = st.evidenceDocs.map { d ->
                if (prev != null && d.id == prev.id) d.copy(status = "SUPERSEDED") else d
            }
            st.copy(
                evidenceDocs = listOf(doc) + updatedDocs,
                evidenceLinks = st.evidenceLinks + newLinks,
            )
        }
        return doc
    }

    fun verifyEvidence(docId: String) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(evidenceDocs = st.evidenceDocs.map { if (it.id == docId) it.copy(lastVerifiedAtEpochMs = now) else it })
        }
    }

    fun toggleArchiveEvidence(docId: String) {
        updateAndPersist { st ->
            st.copy(evidenceDocs = st.evidenceDocs.map {
                if (it.id == docId) {
                    val next = if (it.status == "ACTIVE") "ARCHIVED" else "ACTIVE"
                    it.copy(status = next)
                } else it
            })
        }
    }

    fun linkEvidenceToRule(docId: String, ruleCode: String, linkType: String = "SATISFIES") {
        val exists = _state.value.evidenceLinks.any { it.evidenceId == docId && it.ruleCode == ruleCode }
        if (exists) return
        val link = EvidenceLink(uid("el"), ruleCode, docId, linkType, "MANUAL")
        updateAndPersist { it.copy(evidenceLinks = it.evidenceLinks + link) }
    }

    fun unlinkEvidenceRule(linkId: String) {
        updateAndPersist { it.copy(evidenceLinks = it.evidenceLinks.filterNot { l -> l.id == linkId }) }
    }

    // ── Phase 2: SOC / CERT-as-a-Service ──────────────────────────────────────

    fun createIncident(
        organizationId: String,
        title: String,
        description: String,
        category: String,
        severity: String,
        actorName: String,
    ): SecurityIncident {
        val now = System.currentTimeMillis()
        val org = _state.value.organizations.find { it.id == organizationId }
        val pri = SocEngine.prioritiseIncident(severity, category, org?.sector, confidence = 75, ageMinutes = 0)
        val num = (_state.value.incidents.size + 47).toString().padStart(4, '0')
        val inc = SecurityIncident(
            id = uid("inc"),
            organizationId = organizationId,
            ref = "INC-2026-$num",
            title = title.trim(),
            titleAm = title.trim(),
            description = description.trim(),
            category = category,
            severity = severity,
            status = IncidentStatus.DETECTED,
            source = "ANALYST_REPORT",
            detectionMethod = "MANUAL_TRIAGE",
            priorityScore = pri.score.toDouble(),
            priorityLabel = pri.label,
            detectedAtEpochMs = now,
            notifiedAtEpochMs = null,
            triagedAtEpochMs = null,
            containedAtEpochMs = null,
            eradicatedAtEpochMs = null,
            recoveredAtEpochMs = null,
            closedAtEpochMs = null,
            reportedToEthiocertAtEpochMs = null,
            matchedIndicatorIds = emptyList(),
            events = listOf(IncidentEvent(uid("ie"), "DETECTED", pri.rationale, actorName, now)),
        )
        updateAndPersist { it.copy(incidents = listOf(inc) + it.incidents) }
        return inc
    }

    fun notifyIncidentOrg(incidentId: String, actorName: String) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(incidents = st.incidents.map { inc ->
                if (inc.id != incidentId || inc.notifiedAtEpochMs != null) inc
                else {
                    val mins = maxOf(1, ((now - inc.detectedAtEpochMs) / 60_000L).toInt())
                    inc.copy(
                        notifiedAtEpochMs = now,
                        events = inc.events + IncidentEvent(uid("ie"), "NOTIFIED", "Organization notified ($mins min latency)", actorName, now),
                    )
                }
            })
        }
    }

    fun advanceIncident(incidentId: String, actorName: String, note: String? = null) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(incidents = st.incidents.map { inc ->
                val next = inc.status.next() ?: return@map inc
                if (inc.id != incidentId || !SocEngine.canAdvanceTo(inc.status, next)) inc
                else inc.copy(
                    status = next,
                    triagedAtEpochMs = if (next == IncidentStatus.TRIAGED) now else inc.triagedAtEpochMs,
                    containedAtEpochMs = if (next == IncidentStatus.CONTAINED) now else inc.containedAtEpochMs,
                    eradicatedAtEpochMs = if (next == IncidentStatus.ERADICATED) now else inc.eradicatedAtEpochMs,
                    recoveredAtEpochMs = if (next == IncidentStatus.RECOVERED) now else inc.recoveredAtEpochMs,
                    closedAtEpochMs = if (next == IncidentStatus.CLOSED) now else inc.closedAtEpochMs,
                    events = inc.events + IncidentEvent(uid("ie"), next.eventType, note?.ifBlank { null } ?: "Advanced to ${next.labelEn}", actorName, now),
                )
            })
        }
    }

    fun addIncidentNote(incidentId: String, note: String, actorName: String) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(incidents = st.incidents.map { inc ->
                if (inc.id != incidentId) inc
                else inc.copy(events = inc.events + IncidentEvent(uid("ie"), "NOTE", note.trim(), actorName, now))
            })
        }
    }

    fun contributeThreatIndicator(
        type: String,
        value: String,
        titleEn: String,
        category: String,
        severity: String,
        tlp: String,
        confidence: Int,
        organizationId: String?,
        p2pRecipientIds: List<String>,
    ): ThreatIndicator {
        val now = System.currentTimeMillis()
        val sens = SocEngine.classifySensitivity(category, tlp, confidence)
        val (sharing, rationale) = SocEngine.hybridSharingDecision(tlp, sens, category)
        val ind = ThreatIndicator(
            id = uid("ind"),
            type = type,
            value = value.trim(),
            titleEn = titleEn.trim(),
            titleAm = titleEn.trim(),
            descriptionEn = "Contributed via Adwa member portal",
            descriptionAm = "በአባል ተቋም የተጋራ ስጋት",
            category = category,
            severity = severity,
            tlp = tlp,
            source = if (sharing == "P2P") "MEMBER_P2P" else "MEMBER_HUB",
            sourceRef = "MEM-${System.currentTimeMillis() % 10000}",
            confidence = confidence,
            sensitivity = sens,
            sharing = sharing,
            sharingRationale = rationale,
            organizationId = organizationId,
            sharedWithOrgIds = if (sharing == "P2P") p2pRecipientIds else emptyList(),
            firstSeenAtEpochMs = now,
            lastSeenAtEpochMs = now,
            sightings = 1,
            isActive = true,
        )
        updateAndPersist { it.copy(threatIndicators = listOf(ind) + it.threatIndicators) }
        return ind
    }

    fun recordIndicatorSighting(indicatorId: String) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(threatIndicators = st.threatIndicators.map {
                if (it.id == indicatorId) it.copy(sightings = it.sightings + 1, lastSeenAtEpochMs = now) else it
            })
        }
    }

    fun toggleIndicatorActive(indicatorId: String) {
        updateAndPersist { st ->
            st.copy(threatIndicators = st.threatIndicators.map {
                if (it.id == indicatorId) it.copy(isActive = !it.isActive) else it
            })
        }
    }

    fun pullEthioCertFeed(): SyncRun {
        val now = System.currentTimeMillis()
        val existingValues = _state.value.threatIndicators.map { it.value }.toSet()
        var ingested = 0
        var updated = 0
        val newList = mutableListOf<ThreatIndicator>()
        for ((idx, f) in SocEngine.ETHIOCERT_FEED.withIndex()) {
            if (f.value in existingValues) {
                updated++
            } else {
                ingested++
                newList.add(
                    ThreatIndicator(
                        id = uid("ind_ec"),
                        type = f.type, value = f.value, titleEn = f.titleEn, titleAm = f.titleAm,
                        descriptionEn = "Ethio-CERT Bulletin ${f.bulletin}",
                        descriptionAm = "ብሔራዊ መግለጫ ${f.bulletin}",
                        category = f.category, severity = f.severity, tlp = f.tlp,
                        source = "ETHIO_CERT", sourceRef = f.bulletin,
                        confidence = f.confidence, sensitivity = f.sensitivity,
                        sharing = f.sharing, sharingRationale = "${f.tlp} national advisory",
                        organizationId = null, sharedWithOrgIds = emptyList(),
                        firstSeenAtEpochMs = now, lastSeenAtEpochMs = now,
                        sightings = 1 + idx, isActive = true,
                    )
                )
            }
        }
        val run = SyncRun(
            id = uid("sync"),
            direction = "INGEST",
            status = "SUCCESS",
            source = "LIVE",
            ingested = ingested,
            updated = updated,
            reported = 0,
            payloadHash = RulesEngine.sha256Hex("ingest:$now".toByteArray()),
            receiptId = "EC-INGEST-${now % 100000}",
            organizationId = null,
            detail = "Synchronised Ethio-CERT national STIX feed ($ingested new, $updated refreshed)",
            createdAtEpochMs = now,
        )
        updateAndPersist { st ->
            st.copy(
                threatIndicators = newList + st.threatIndicators.map {
                    if (it.source == "ETHIO_CERT") it.copy(lastSeenAtEpochMs = now, sightings = it.sightings + 1) else it
                },
                syncRuns = listOf(run) + st.syncRuns,
            )
        }
        return run
    }

    fun reportUpwardToEthioCert(orgId: String?): SyncRun {
        val now = System.currentTimeMillis()
        val pendingIncidents = _state.value.incidents.filter {
            (orgId == null || it.organizationId == orgId) && it.reportedToEthiocertAtEpochMs == null
        }
        val count = pendingIncidents.size
        val receipt = "EC-RCPT-${(now / 1000) % 100000}"
        val payload = pendingIncidents.joinToString("|") { "${it.ref}:${it.category}:${it.severity}" }
        val run = SyncRun(
            id = uid("sync"),
            direction = "REPORT",
            status = "SUCCESS",
            source = "LIVE",
            ingested = 0,
            updated = 0,
            reported = count,
            payloadHash = RulesEngine.sha256Hex("report:$now:$payload".toByteArray()),
            receiptId = receipt,
            organizationId = orgId,
            detail = "Anonymised upward report of $count incident(s) — receipt $receipt",
            createdAtEpochMs = now,
        )
        val pendingIds = pendingIncidents.map { it.id }.toSet()
        updateAndPersist { st ->
            st.copy(
                incidents = st.incidents.map { if (it.id in pendingIds) it.copy(reportedToEthiocertAtEpochMs = now) else it },
                syncRuns = listOf(run) + st.syncRuns,
            )
        }
        return run
    }

    // ── Phase 3: Track B Awareness ────────────────────────────────────────────

    fun addAwarenessSchoolAndClass(
        schoolName: String,
        subCity: String,
        className: String,
        grade: Int,
        groupType: String,
        teacherName: String,
    ) {
        val schId = uid("sch")
        val clsId = uid("cls")
        val codeSuffix = (10..99).random()
        val prefix = schoolName.filter { it.isLetter() }.take(3).uppercase().ifEmpty { "SCH" }
        val joinCode = "ADWA-$prefix-$grade$codeSuffix"
        val school = AwarenessSchool(schId, schoolName.trim(), schoolName.trim(), subCity.trim(), teacherName.trim())
        val cls = AwarenessClass(clsId, schId, className.trim(), grade, groupType, joinCode, teacherName.trim())
        updateAndPersist { st ->
            st.copy(
                awarenessSchools = st.awarenessSchools + school,
                awarenessClasses = st.awarenessClasses + cls,
            )
        }
    }

    fun issueLearnerCodes(classId: String, count: Int) {
        val cls = _state.value.awarenessClasses.find { it.id == classId } ?: return
        val existingCount = _state.value.awarenessLearners.count { it.classId == classId }
        val newLearners = (1..count).map { i ->
            val seq = (existingCount + i).toString().padStart(3, '0')
            val code = "AW-${cls.joinCode.takeLast(2)}$seq"
            AwarenessLearner(uid("lrn"), classId, code, cls.grade, "PENDING", null, null, null, null, null, null, null, emptyMap())
        }
        updateAndPersist { it.copy(awarenessLearners = it.awarenessLearners + newLearners) }
    }

    fun updateLearnerConsent(learnerId: String, consent: String) {
        updateAndPersist { st ->
            st.copy(awarenessLearners = st.awarenessLearners.map {
                if (it.id == learnerId) it.copy(consent = consent, consentRef = if (consent == "GRANTED") "GUARDIAN-${it.code}" else null) else it
            })
        }
    }

    fun submitLearnerAssessment(learnerId: String, phase: String, correct: Int, total: Int) {
        val score = kotlin.math.round((correct.toDouble() / total.coerceAtLeast(1)) * 10000.0) / 100.0
        updateAndPersist { st ->
            st.copy(awarenessLearners = st.awarenessLearners.map { l ->
                if (l.id != learnerId) l
                else if (phase == "PRE") l.copy(preScore = score, preCorrect = correct, preTotal = total)
                else l.copy(postScore = score, postCorrect = correct, postTotal = total)
            })
        }
    }

    fun recordModuleQuizCompletion(learnerId: String, moduleCode: String, score: Double) {
        updateAndPersist { st ->
            st.copy(awarenessLearners = st.awarenessLearners.map { l ->
                if (l.id != learnerId) l
                else l.copy(completedModules = l.completedModules + (moduleCode to score))
            })
        }
    }

    fun recordToolPing(toolId: String, lang: String) {
        val ping = AwarenessToolPing(toolId, lang, System.currentTimeMillis())
        updateAndPersist { it.copy(awarenessToolPings = it.awarenessToolPings + ping) }
    }

    // ── Phase 4 & 5: Track C Gated Expansion ──────────────────────────────────

    fun transitionExpansionGate(gateId: String, toStatus: String, note: String?, actorName: String): String? {
        val gate = _state.value.expansionGates.find { it.id == gateId } ?: return "Gate not found"
        if (!TrackCEngine.canTransitionGate(gate.status, toStatus)) {
            return "Invalid transition from ${gate.status} to $toStatus"
        }
        if (toStatus == "SATISFIED" && gate.evidence.isEmpty()) {
            return "At least one sealed verification artefact is required before marking SATISFIED."
        }
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(expansionGates = st.expansionGates.map { g ->
                if (g.id != gateId) g
                else g.copy(
                    status = toStatus,
                    notes = note?.trim()?.ifEmpty { null } ?: g.notes,
                    events = g.events + GateEvent(uid("gev"), "STATUS_CHANGE", g.status, toStatus, note, actorName, now),
                )
            })
        }
        return null
    }

    fun attachGateEvidence(gateId: String, fileName: String, description: String?, bytes: ByteArray, actorName: String) {
        val now = System.currentTimeMillis()
        val ev = GateEvidence(uid("ge"), fileName, description?.trim()?.ifEmpty { null }, RulesEngine.sha256Hex(bytes), bytes.size.toLong(), actorName, now)
        updateAndPersist { st ->
            st.copy(expansionGates = st.expansionGates.map { g ->
                if (g.id != gateId) g
                else g.copy(
                    evidence = listOf(ev) + g.evidence,
                    events = g.events + GateEvent(uid("gev"), "EVIDENCE_ATTACHED", null, null, "Attached $fileName", actorName, now),
                )
            })
        }
    }

    fun updateAcfsItemStatus(itemId: String, newStatus: String) {
        updateAndPersist { st ->
            st.copy(acfsItems = st.acfsItems.map { if (it.id == itemId) it.copy(status = newStatus) else it })
        }
    }

    fun recordAcfsBoardDecision(decision: String, rationale: String, actorName: String) {
        val dec = AcfsGateDecision(uid("dec"), decision, rationale.trim(), actorName, System.currentTimeMillis())
        updateAndPersist { it.copy(acfsDecisions = listOf(dec) + it.acfsDecisions) }
    }

    fun advanceCiiMouStatus(operatorId: String, mouId: String) {
        val order = listOf("DRAFTING", "LEGAL_REVIEW", "SIGNED", "ACTIVE")
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(ciiOperators = st.ciiOperators.map { op ->
                if (op.id != operatorId) op
                else op.copy(mous = op.mous.map { m ->
                    if (m.id != mouId) m
                    else {
                        val idx = order.indexOf(m.status)
                        val next = if (idx in 0 until order.lastIndex) order[idx + 1] else m.status
                        m.copy(status = next, signedAtEpochMs = if (next == "SIGNED" || next == "ACTIVE") m.signedAtEpochMs ?: now else m.signedAtEpochMs)
                    }
                })
            })
        }
    }

    fun advanceCiiAuthorizationStatus(operatorId: String, authId: String) {
        val order = listOf("PENDING", "UNDER_REVIEW", "GRANTED", "ACTIVE")
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            st.copy(ciiOperators = st.ciiOperators.map { op ->
                if (op.id != operatorId) op
                else op.copy(authorizations = op.authorizations.map { a ->
                    if (a.id != authId) a
                    else {
                        val idx = order.indexOf(a.status)
                        val next = if (idx in 0 until order.lastIndex) order[idx + 1] else a.status
                        a.copy(status = next, issuedAtEpochMs = if (next == "GRANTED" || next == "ACTIVE") a.issuedAtEpochMs ?: now else a.issuedAtEpochMs)
                    }
                })
            })
        }
    }

    fun advanceCiiSystemReadiness(operatorId: String, systemId: String): String? {
        val op = _state.value.ciiOperators.find { it.id == operatorId } ?: return "Operator not found"
        val sys = op.systems.find { it.id == systemId } ?: return "System not found"
        val order = listOf("REGISTERED", "INVENTORY_MAPPED", "AGENT_READY", "MONITORING_AUTHORIZED")
        val idx = order.indexOf(sys.readiness)
        if (idx < 0 || idx >= order.lastIndex) return null
        val next = order[idx + 1]
        if (next == "MONITORING_AUTHORIZED" && !TrackCEngine.isOperatorGateOpen(op)) {
            return "Blocked: double-lock requires ACTIVE/SIGNED MOU and GRANTED/ACTIVE INSA Authorization first."
        }
        updateAndPersist { st ->
            st.copy(ciiOperators = st.ciiOperators.map { o ->
                if (o.id != operatorId) o
                else o.copy(systems = o.systems.map { s -> if (s.id == systemId) s.copy(readiness = next) else s })
            })
        }
        return null
    }

    // ── Enterprise GRC: CCM, Risk Register, Trust Center, HRS & Playbooks ─────

    fun saveMonitorSweep(updatedMonitors: List<ControlMonitor>, run: MonitorCheckRun) {
        updateAndPersist { st ->
            st.copy(
                controlMonitors = updatedMonitors,
                monitorRuns = (listOf(run) + st.monitorRuns).take(30),
            )
        }
        scope.launch { runCatching { riskRepository.saveMonitors(updatedMonitors) } }
    }

    fun addRiskEntry(
        titleEn: String,
        description: String,
        frameworks: List<String>,
        linkedRuleCodes: List<String>,
        likelihood: Int,
        impact: Int,
        treatment: String,
        ownerName: String,
        sourceMonitorId: String? = null,
    ): RiskRegisterEntry {
        val nextNum = _state.value.riskRegister.size + 1
        val entry = RiskRegisterEntry(
            id = uid("risk"),
            code = "RSK-2026-%02d".format(nextNum),
            titleEn = titleEn.trim(),
            titleAm = titleEn.trim(),
            description = description.trim(),
            frameworks = frameworks.ifEmpty { listOf("PD-1321") },
            linkedRuleCodes = linkedRuleCodes,
            likelihood = likelihood.coerceIn(1, 5),
            impact = impact.coerceIn(1, 5),
            residualScore = likelihood.coerceIn(1, 5) * impact.coerceIn(1, 5),
            treatment = treatment,
            status = "OPEN",
            ownerName = ownerName,
            dueAtEpochMs = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000,
            sourceMonitorId = sourceMonitorId,
        )
        updateAndPersist { st ->
            st.copy(riskRegister = listOf(entry) + st.riskRegister)
        }
        scope.launch { runCatching { riskRepository.upsertRisk(entry) } }
        return entry
    }

    fun updateRiskStatus(riskId: String, newStatus: String, treatment: String? = null) {
        updateAndPersist { st ->
            st.copy(
                riskRegister = st.riskRegister.map { r ->
                    if (r.id != riskId) r
                    else r.copy(
                        status = newStatus,
                        treatment = treatment ?: r.treatment,
                        residualScore = if (newStatus == "MITIGATED") maxOf(1, r.residualScore / 2) else r.residualScore,
                    )
                }
            )
        }
        scope.launch { runCatching { riskRepository.updateRiskStatus(riskId, newStatus, treatment) } }
    }

    fun deleteRiskEntry(riskId: String) {
        updateAndPersist { st ->
            st.copy(riskRegister = st.riskRegister.filterNot { it.id == riskId })
        }
        scope.launch { runCatching { riskRepository.deleteRiskById(riskId) } }
    }

    fun updateTrustCenterConfig(transform: (TrustCenterConfig) -> TrustCenterConfig) {
        updateAndPersist { st ->
            val current = st.trustCenter ?: SeedData.buildInitialState().trustCenter!!
            val updated = transform(current)
            st.copy(trustCenter = updated)
        }
    }

    fun publishTrustCenterSnapshot(summaryPayload: String) {
        val hash = RulesEngine.sha256Hex(summaryPayload.toByteArray())
        val now = System.currentTimeMillis()
        updateTrustCenterConfig { cfg ->
            cfg.copy(lastPublishedAtEpochMs = now, publishedSha256 = hash)
        }
    }

    fun toggleIncidentPlaybookStep(incidentId: String, stepId: String) {
        updateAndPersist { st ->
            val current = st.incidentPlaybookChecks[incidentId].orEmpty()
            val updated = if (stepId in current) current - stepId else current + stepId
            st.copy(incidentPlaybookChecks = st.incidentPlaybookChecks + (incidentId to updated))
        }
    }

    fun saveIncidentAiNote(incidentId: String, note: String) {
        updateAndPersist { st ->
            st.copy(incidentAiNotes = st.incidentAiNotes + (incidentId to note))
        }
    }

    fun launchAwarenessCampaign(titleEn: String, targetGroup: String, sentCount: Int, reportedCount: Int, clickedCount: Int): AwarenessCampaign {
        val camp = AwarenessCampaign(
            id = uid("camp"),
            titleEn = titleEn.trim(),
            titleAm = titleEn.trim(),
            targetGroup = targetGroup,
            sentCount = sentCount.coerceAtLeast(1),
            reportedCount = reportedCount.coerceIn(0, sentCount.coerceAtLeast(1)),
            clickedCount = clickedCount.coerceIn(0, sentCount.coerceAtLeast(1)),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        updateAndPersist { st ->
            st.copy(awarenessCampaigns = listOf(camp) + st.awarenessCampaigns)
        }
        return camp
    }

    // ── JSON Persistence ──────────────────────────────────────────────────────

    private fun loadOrSeed(): AdwaState {
        if (!stateFile.exists()) {
            val fresh = SeedData.buildInitialState()
            runCatching { stateFile.writeText(serialize(fresh).toString()) }
            return fresh
        }
        return runCatching {
            deserialize(JSONObject(stateFile.readText()))
        }.getOrElse {
            val fresh = SeedData.buildInitialState()
            runCatching { stateFile.writeText(serialize(fresh).toString()) }
            fresh
        }
    }

    private fun JSONObject.optStr(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
    private fun JSONObject.optLng(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.optDbl(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
    private fun JSONObject.optIntN(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun serialize(s: AdwaState): JSONObject = JSONObject().apply {
        put("organizations", JSONArray().apply {
            s.organizations.forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id); put("name", o.name); put("nameAm", o.nameAm); put("slug", o.slug)
                    put("sector", o.sector); put("tier", o.tier); put("contactEmail", o.contactEmail ?: JSONObject.NULL)
                    put("status", o.status); put("notes", o.notes ?: JSONObject.NULL)
                })
            }
        })
        put("users", JSONArray().apply {
            s.users.forEach { u ->
                put(JSONObject().apply {
                    put("id", u.id); put("email", u.email); put("password", u.password); put("name", u.name)
                    put("role", u.role.name); put("organizationId", u.organizationId ?: JSONObject.NULL)
                    put("status", u.status); put("mfaEnabled", u.mfaEnabled)
                    put("totpSecretBase32", u.totpSecretBase32 ?: JSONObject.NULL)
                    put("backupCodes", JSONArray(u.backupCodes))
                    put("lastLoginAtEpochMs", u.lastLoginAtEpochMs ?: JSONObject.NULL)
                })
            }
        })
        put("authEvents", JSONArray().apply {
            s.authEvents.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("email", e.email); put("organizationId", e.organizationId ?: JSONObject.NULL)
                    put("type", e.type); put("detail", e.detail); put("createdAtEpochMs", e.createdAtEpochMs)
                })
            }
        })
        put("scans", JSONArray().apply {
            s.scans.forEach { sc ->
                put(JSONObject().apply {
                    put("id", sc.id); put("organizationId", sc.organizationId); put("frameworkCode", sc.frameworkCode)
                    put("name", sc.name); put("status", sc.status); put("auditorName", sc.auditorName)
                    put("overallScore", sc.overallScore ?: JSONObject.NULL)
                    put("pillarScores", JSONObject().apply { sc.pillarScores.forEach { (k, v) -> put(k, v) } })
                    put("findingsCount", sc.findingsCount); put("criticalFindings", sc.criticalFindings)
                    put("startedAtEpochMs", sc.startedAtEpochMs)
                    put("completedAtEpochMs", sc.completedAtEpochMs ?: JSONObject.NULL)
                    put("durationMinutes", sc.durationMinutes ?: JSONObject.NULL)
                    put("results", JSONArray().apply {
                        sc.results.forEach { r ->
                            put(JSONObject().apply {
                                put("id", r.id); put("scanId", r.scanId); put("ruleCode", r.ruleCode)
                                put("answer", r.answer.name); put("answeredAtEpochMs", r.answeredAtEpochMs ?: JSONObject.NULL)
                                put("evidenceNote", r.evidenceNote ?: JSONObject.NULL); put("score", r.score)
                                put("riskRank", r.riskRank); put("isFinding", r.isFinding); put("predictive", r.predictive)
                                put("findingEn", r.findingEn ?: JSONObject.NULL); put("findingAm", r.findingAm ?: JSONObject.NULL)
                                put("status", r.status); put("waiverNote", r.waiverNote ?: JSONObject.NULL)
                                put("dueAtEpochMs", r.dueAtEpochMs ?: JSONObject.NULL)
                                put("remediatedAtEpochMs", r.remediatedAtEpochMs ?: JSONObject.NULL)
                            })
                        }
                    })
                })
            }
        })
        put("draftAnswers", JSONObject().apply {
            s.draftAnswers.forEach { (scanId, map) ->
                put(scanId, JSONObject().apply { map.forEach { (k, v) -> put(k, v.name) } })
            }
        })
        put("draftNotes", JSONObject().apply {
            s.draftNotes.forEach { (scanId, map) ->
                put(scanId, JSONObject().apply { map.forEach { (k, v) -> put(k, v) } })
            }
        })
        put("evidenceDocs", JSONArray().apply {
            s.evidenceDocs.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("title", d.title); put("description", d.description)
                    put("category", d.category.name); put("tags", d.tags); put("fileName", d.fileName)
                    put("mimeType", d.mimeType); put("fileSize", d.fileSize); put("checksumSha256", d.checksumSha256)
                    put("status", d.status); put("version", d.version); put("rootId", d.rootId ?: JSONObject.NULL)
                    put("downloads", d.downloads); put("lastVerifiedAtEpochMs", d.lastVerifiedAtEpochMs ?: JSONObject.NULL)
                    put("organizationId", d.organizationId); put("uploadedByName", d.uploadedByName)
                    put("createdAtEpochMs", d.createdAtEpochMs); put("contentPreview", d.contentPreview)
                })
            }
        })
        put("evidenceLinks", JSONArray().apply {
            s.evidenceLinks.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("ruleCode", l.ruleCode); put("evidenceId", l.evidenceId)
                    put("linkType", l.linkType); put("source", l.source)
                })
            }
        })
        put("incidents", JSONArray().apply {
            s.incidents.forEach { inc ->
                put(JSONObject().apply {
                    put("id", inc.id); put("organizationId", inc.organizationId); put("ref", inc.ref)
                    put("title", inc.title); put("titleAm", inc.titleAm); put("description", inc.description)
                    put("category", inc.category); put("severity", inc.severity); put("status", inc.status.name)
                    put("source", inc.source); put("detectionMethod", inc.detectionMethod)
                    put("priorityScore", inc.priorityScore); put("priorityLabel", inc.priorityLabel)
                    put("detectedAtEpochMs", inc.detectedAtEpochMs)
                    put("notifiedAtEpochMs", inc.notifiedAtEpochMs ?: JSONObject.NULL)
                    put("triagedAtEpochMs", inc.triagedAtEpochMs ?: JSONObject.NULL)
                    put("containedAtEpochMs", inc.containedAtEpochMs ?: JSONObject.NULL)
                    put("eradicatedAtEpochMs", inc.eradicatedAtEpochMs ?: JSONObject.NULL)
                    put("recoveredAtEpochMs", inc.recoveredAtEpochMs ?: JSONObject.NULL)
                    put("closedAtEpochMs", inc.closedAtEpochMs ?: JSONObject.NULL)
                    put("reportedToEthiocertAtEpochMs", inc.reportedToEthiocertAtEpochMs ?: JSONObject.NULL)
                    put("matchedIndicatorIds", JSONArray(inc.matchedIndicatorIds))
                    put("events", JSONArray().apply {
                        inc.events.forEach { ev ->
                            put(JSONObject().apply {
                                put("id", ev.id); put("type", ev.type); put("note", ev.note ?: JSONObject.NULL)
                                put("actorName", ev.actorName ?: JSONObject.NULL); put("createdAtEpochMs", ev.createdAtEpochMs)
                            })
                        }
                    })
                })
            }
        })
        put("threatIndicators", JSONArray().apply {
            s.threatIndicators.forEach { ind ->
                put(JSONObject().apply {
                    put("id", ind.id); put("type", ind.type); put("value", ind.value)
                    put("titleEn", ind.titleEn); put("titleAm", ind.titleAm)
                    put("descriptionEn", ind.descriptionEn ?: JSONObject.NULL)
                    put("descriptionAm", ind.descriptionAm ?: JSONObject.NULL)
                    put("category", ind.category); put("severity", ind.severity); put("tlp", ind.tlp)
                    put("source", ind.source); put("sourceRef", ind.sourceRef ?: JSONObject.NULL)
                    put("confidence", ind.confidence); put("sensitivity", ind.sensitivity)
                    put("sharing", ind.sharing); put("sharingRationale", ind.sharingRationale)
                    put("organizationId", ind.organizationId ?: JSONObject.NULL)
                    put("sharedWithOrgIds", JSONArray(ind.sharedWithOrgIds))
                    put("firstSeenAtEpochMs", ind.firstSeenAtEpochMs); put("lastSeenAtEpochMs", ind.lastSeenAtEpochMs)
                    put("sightings", ind.sightings); put("isActive", ind.isActive)
                })
            }
        })
        put("syncRuns", JSONArray().apply {
            s.syncRuns.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("direction", r.direction); put("status", r.status); put("source", r.source)
                    put("ingested", r.ingested); put("updated", r.updated); put("reported", r.reported)
                    put("payloadHash", r.payloadHash ?: JSONObject.NULL); put("receiptId", r.receiptId ?: JSONObject.NULL)
                    put("organizationId", r.organizationId ?: JSONObject.NULL); put("detail", r.detail)
                    put("createdAtEpochMs", r.createdAtEpochMs)
                })
            }
        })
        put("awarenessSchools", JSONArray().apply {
            s.awarenessSchools.forEach { sc ->
                put(JSONObject().apply {
                    put("id", sc.id); put("name", sc.name); put("nameAm", sc.nameAm)
                    put("subCity", sc.subCity); put("contactPerson", sc.contactPerson ?: JSONObject.NULL)
                })
            }
        })
        put("awarenessClasses", JSONArray().apply {
            s.awarenessClasses.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("schoolId", c.schoolId); put("name", c.name); put("grade", c.grade)
                    put("groupType", c.groupType); put("joinCode", c.joinCode); put("teacherName", c.teacherName ?: JSONObject.NULL)
                })
            }
        })
        put("awarenessLearners", JSONArray().apply {
            s.awarenessLearners.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("classId", l.classId); put("code", l.code); put("grade", l.grade)
                    put("consent", l.consent); put("consentRef", l.consentRef ?: JSONObject.NULL)
                    put("preScore", l.preScore ?: JSONObject.NULL); put("preCorrect", l.preCorrect ?: JSONObject.NULL)
                    put("preTotal", l.preTotal ?: JSONObject.NULL)
                    put("postScore", l.postScore ?: JSONObject.NULL); put("postCorrect", l.postCorrect ?: JSONObject.NULL)
                    put("postTotal", l.postTotal ?: JSONObject.NULL)
                    put("completedModules", JSONObject().apply { l.completedModules.forEach { (k, v) -> put(k, v) } })
                })
            }
        })
        put("awarenessToolPings", JSONArray().apply {
            s.awarenessToolPings.forEach { p ->
                put(JSONObject().apply {
                    put("toolId", p.toolId); put("lang", p.lang); put("createdAtEpochMs", p.createdAtEpochMs)
                })
            }
        })
        put("expansionGates", JSONArray().apply {
            s.expansionGates.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("phase", g.phase); put("code", g.code)
                    put("titleEn", g.titleEn); put("titleAm", g.titleAm)
                    put("detailEn", g.detailEn); put("detailAm", g.detailAm)
                    put("category", g.category); put("status", g.status); put("blocking", g.blocking)
                    put("notes", g.notes ?: JSONObject.NULL)
                    put("evidence", JSONArray().apply {
                        g.evidence.forEach { e ->
                            put(JSONObject().apply {
                                put("id", e.id); put("fileName", e.fileName); put("description", e.description ?: JSONObject.NULL)
                                put("checksumSha256", e.checksumSha256); put("fileSize", e.fileSize)
                                put("uploadedByName", e.uploadedByName); put("createdAtEpochMs", e.createdAtEpochMs)
                            })
                        }
                    })
                    put("events", JSONArray().apply {
                        g.events.forEach { ev ->
                            put(JSONObject().apply {
                                put("id", ev.id); put("kind", ev.kind)
                                put("fromStatus", ev.fromStatus ?: JSONObject.NULL); put("toStatus", ev.toStatus ?: JSONObject.NULL)
                                put("note", ev.note ?: JSONObject.NULL); put("actorName", ev.actorName ?: JSONObject.NULL)
                                put("createdAtEpochMs", ev.createdAtEpochMs)
                            })
                        }
                    })
                })
            }
        })
        put("acfsItems", JSONArray().apply {
            s.acfsItems.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("domain", a.domain); put("code", a.code)
                    put("titleEn", a.titleEn); put("titleAm", a.titleAm); put("description", a.description)
                    put("status", a.status); put("owner", a.owner ?: JSONObject.NULL)
                    put("notes", a.notes ?: JSONObject.NULL); put("evidenceRef", a.evidenceRef ?: JSONObject.NULL)
                    put("sortOrder", a.sortOrder)
                })
            }
        })
        put("acfsDecisions", JSONArray().apply {
            s.acfsDecisions.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("decision", d.decision); put("rationale", d.rationale)
                    put("decidedBy", d.decidedBy); put("createdAtEpochMs", d.createdAtEpochMs)
                })
            }
        })
        put("ciiOperators", JSONArray().apply {
            s.ciiOperators.forEach { op ->
                put(JSONObject().apply {
                    put("id", op.id); put("code", op.code); put("name", op.name); put("nameAm", op.nameAm)
                    put("sector", op.sector); put("tier", op.tier)
                    put("orgId", op.orgId ?: JSONObject.NULL); put("contactEmail", op.contactEmail ?: JSONObject.NULL)
                    put("mous", JSONArray().apply {
                        op.mous.forEach { m ->
                            put(JSONObject().apply {
                                put("id", m.id); put("version", m.version); put("status", m.status)
                                put("scope", JSONArray(m.scope)); put("dataTerms", m.dataTerms ?: JSONObject.NULL)
                                put("signedAtEpochMs", m.signedAtEpochMs ?: JSONObject.NULL)
                                put("expiresAtEpochMs", m.expiresAtEpochMs ?: JSONObject.NULL)
                            })
                        }
                    })
                    put("authorizations", JSONArray().apply {
                        op.authorizations.forEach { a ->
                            put(JSONObject().apply {
                                put("id", a.id); put("reference", a.reference); put("status", a.status)
                                put("issuedAtEpochMs", a.issuedAtEpochMs ?: JSONObject.NULL)
                                put("expiresAtEpochMs", a.expiresAtEpochMs ?: JSONObject.NULL)
                                put("scope", a.scope ?: JSONObject.NULL)
                            })
                        }
                    })
                    put("systems", JSONArray().apply {
                        op.systems.forEach { sy ->
                            put(JSONObject().apply {
                                put("id", sy.id); put("name", sy.name); put("nameAm", sy.nameAm ?: JSONObject.NULL)
                                put("category", sy.category); put("criticality", sy.criticality); put("readiness", sy.readiness)
                            })
                        }
                    })
                })
            }
        })
        put("controlMonitors", JSONArray().apply {
            s.controlMonitors.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("code", m.code); put("titleEn", m.titleEn); put("titleAm", m.titleAm)
                    put("category", m.category); put("sourceType", m.sourceType); put("targetHint", m.targetHint)
                    put("mappedRuleCodes", JSONArray(m.mappedRuleCodes)); put("status", m.status)
                    put("detail", m.detail); put("lastCheckedAtEpochMs", m.lastCheckedAtEpochMs ?: JSONObject.NULL)
                })
            }
        })
        put("monitorRuns", JSONArray().apply {
            s.monitorRuns.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("monitorCount", r.monitorCount); put("passed", r.passed)
                    put("warned", r.warned); put("failed", r.failed); put("probeUrl", r.probeUrl)
                    put("durationMs", r.durationMs); put("summary", r.summary); put("executedAtEpochMs", r.executedAtEpochMs)
                })
            }
        })
        put("riskRegister", JSONArray().apply {
            s.riskRegister.forEach { rk ->
                put(JSONObject().apply {
                    put("id", rk.id); put("code", rk.code); put("titleEn", rk.titleEn); put("titleAm", rk.titleAm)
                    put("description", rk.description); put("frameworks", JSONArray(rk.frameworks))
                    put("linkedRuleCodes", JSONArray(rk.linkedRuleCodes)); put("likelihood", rk.likelihood)
                    put("impact", rk.impact); put("residualScore", rk.residualScore); put("treatment", rk.treatment)
                    put("status", rk.status); put("ownerName", rk.ownerName)
                    put("dueAtEpochMs", rk.dueAtEpochMs ?: JSONObject.NULL)
                    put("sourceMonitorId", rk.sourceMonitorId ?: JSONObject.NULL)
                })
            }
        })
        s.trustCenter?.let { tc ->
            put("trustCenter", JSONObject().apply {
                put("organizationId", tc.organizationId); put("publicTitle", tc.publicTitle)
                put("securityContactEmail", tc.securityContactEmail); put("ndaRequired", tc.ndaRequired)
                put("showComplianceScores", tc.showComplianceScores); put("showControlMonitors", tc.showControlMonitors)
                put("showEthioCertStatus", tc.showEthioCertStatus); put("showSubprocessors", tc.showSubprocessors)
                put("subprocessors", JSONArray(tc.subprocessors))
                put("lastPublishedAtEpochMs", tc.lastPublishedAtEpochMs ?: JSONObject.NULL)
                put("publishedSha256", tc.publishedSha256 ?: JSONObject.NULL)
            })
        }
        put("awarenessCampaigns", JSONArray().apply {
            s.awarenessCampaigns.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("titleEn", c.titleEn); put("titleAm", c.titleAm)
                    put("targetGroup", c.targetGroup); put("sentCount", c.sentCount)
                    put("reportedCount", c.reportedCount); put("clickedCount", c.clickedCount)
                    put("createdAtEpochMs", c.createdAtEpochMs)
                })
            }
        })
        put("incidentPlaybookChecks", JSONObject().apply {
            s.incidentPlaybookChecks.forEach { (k, v) -> put(k, JSONArray(v)) }
        })
        put("incidentAiNotes", JSONObject().apply {
            s.incidentAiNotes.forEach { (k, v) -> put(k, v) }
        })
    }

    private fun deserialize(j: JSONObject): AdwaState {
        fun JSONArray.strList() = List(length()) { getString(it) }
        fun <T> JSONArray.mapObj(fn: (JSONObject) -> T): List<T> = List(length()) { fn(getJSONObject(it)) }

        val orgs = j.getJSONArray("organizations").mapObj { o ->
            Organization(o.getString("id"), o.getString("name"), o.getString("nameAm"), o.getString("slug"),
                o.getString("sector"), o.getString("tier"), o.optStr("contactEmail"), o.getString("status"), o.optStr("notes"))
        }
        val users = j.getJSONArray("users").mapObj { u ->
            UserAccount(u.getString("id"), u.getString("email"), u.getString("password"), u.getString("name"),
                AppRole.valueOf(u.getString("role")), u.optStr("organizationId"), u.getString("status"),
                u.getBoolean("mfaEnabled"), u.optStr("totpSecretBase32"),
                u.getJSONArray("backupCodes").strList(), u.optLng("lastLoginAtEpochMs"))
        }
        val authEvents = j.getJSONArray("authEvents").mapObj { e ->
            AuthEvent(e.getString("id"), e.getString("email"), e.optStr("organizationId"),
                e.getString("type"), e.getString("detail"), e.getLong("createdAtEpochMs"))
        }
        val scans = j.getJSONArray("scans").mapObj { sc ->
            val pObj = sc.getJSONObject("pillarScores")
            val pillars = buildMap { pObj.keys().forEach { k -> put(k, pObj.getDouble(k)) } }
            val results = sc.getJSONArray("results").mapObj { r ->
                ScanResult(r.getString("id"), r.getString("scanId"), r.getString("ruleCode"),
                    Answer.valueOf(r.getString("answer")), r.optLng("answeredAtEpochMs"), r.optStr("evidenceNote"),
                    r.getDouble("score"), r.getDouble("riskRank"), r.getBoolean("isFinding"), r.getBoolean("predictive"),
                    r.optStr("findingEn"), r.optStr("findingAm"), r.getString("status"), r.optStr("waiverNote"),
                    r.optLng("dueAtEpochMs"), r.optLng("remediatedAtEpochMs"))
            }
            ComplianceScan(sc.getString("id"), sc.getString("organizationId"), sc.getString("frameworkCode"),
                sc.getString("name"), sc.getString("status"), sc.getString("auditorName"),
                sc.optDbl("overallScore"), pillars, sc.getInt("findingsCount"), sc.getInt("criticalFindings"),
                sc.getLong("startedAtEpochMs"), sc.optLng("completedAtEpochMs"), sc.optDbl("durationMinutes"), results)
        }
        val dAnsObj = j.getJSONObject("draftAnswers")
        val draftAnswers = buildMap {
            dAnsObj.keys().forEach { sid ->
                val inner = dAnsObj.getJSONObject(sid)
                put(sid, buildMap { inner.keys().forEach { rc -> put(rc, Answer.valueOf(inner.getString(rc))) } })
            }
        }
        val dNotesObj = j.getJSONObject("draftNotes")
        val draftNotes = buildMap {
            dNotesObj.keys().forEach { sid ->
                val inner = dNotesObj.getJSONObject(sid)
                put(sid, buildMap { inner.keys().forEach { rc -> put(rc, inner.getString(rc)) } })
            }
        }
        val docs = j.getJSONArray("evidenceDocs").mapObj { d ->
            EvidenceDocument(d.getString("id"), d.getString("title"), d.getString("description"),
                EvidenceCategory.valueOf(d.getString("category")), d.getString("tags"), d.getString("fileName"),
                d.getString("mimeType"), d.getLong("fileSize"), d.getString("checksumSha256"), d.getString("status"),
                d.getInt("version"), d.optStr("rootId"), d.getInt("downloads"), d.optLng("lastVerifiedAtEpochMs"),
                d.getString("organizationId"), d.getString("uploadedByName"), d.getLong("createdAtEpochMs"), d.getString("contentPreview"))
        }
        val links = j.getJSONArray("evidenceLinks").mapObj { l ->
            EvidenceLink(l.getString("id"), l.getString("ruleCode"), l.getString("evidenceId"), l.getString("linkType"), l.getString("source"))
        }
        val incidents = j.getJSONArray("incidents").mapObj { inc ->
            SecurityIncident(inc.getString("id"), inc.getString("organizationId"), inc.getString("ref"),
                inc.getString("title"), inc.getString("titleAm"), inc.getString("description"),
                inc.getString("category"), inc.getString("severity"), IncidentStatus.valueOf(inc.getString("status")),
                inc.getString("source"), inc.getString("detectionMethod"), inc.getDouble("priorityScore"), inc.getString("priorityLabel"),
                inc.getLong("detectedAtEpochMs"), inc.optLng("notifiedAtEpochMs"), inc.optLng("triagedAtEpochMs"),
                inc.optLng("containedAtEpochMs"), inc.optLng("eradicatedAtEpochMs"), inc.optLng("recoveredAtEpochMs"),
                inc.optLng("closedAtEpochMs"), inc.optLng("reportedToEthiocertAtEpochMs"),
                inc.getJSONArray("matchedIndicatorIds").strList(),
                inc.getJSONArray("events").mapObj { ev ->
                    IncidentEvent(ev.getString("id"), ev.getString("type"), ev.optStr("note"), ev.optStr("actorName"), ev.getLong("createdAtEpochMs"))
                })
        }
        val indicators = j.getJSONArray("threatIndicators").mapObj { ind ->
            ThreatIndicator(ind.getString("id"), ind.getString("type"), ind.getString("value"),
                ind.getString("titleEn"), ind.getString("titleAm"), ind.optStr("descriptionEn"), ind.optStr("descriptionAm"),
                ind.getString("category"), ind.getString("severity"), ind.getString("tlp"),
                ind.getString("source"), ind.optStr("sourceRef"), ind.getInt("confidence"), ind.getInt("sensitivity"),
                ind.getString("sharing"), ind.getString("sharingRationale"), ind.optStr("organizationId"),
                ind.getJSONArray("sharedWithOrgIds").strList(), ind.getLong("firstSeenAtEpochMs"), ind.getLong("lastSeenAtEpochMs"),
                ind.getInt("sightings"), ind.getBoolean("isActive"))
        }
        val syncRuns = j.getJSONArray("syncRuns").mapObj { r ->
            SyncRun(r.getString("id"), r.getString("direction"), r.getString("status"), r.getString("source"),
                r.getInt("ingested"), r.getInt("updated"), r.getInt("reported"),
                r.optStr("payloadHash"), r.optStr("receiptId"), r.optStr("organizationId"),
                r.getString("detail"), r.getLong("createdAtEpochMs"))
        }
        val schools = j.getJSONArray("awarenessSchools").mapObj { sc ->
            AwarenessSchool(sc.getString("id"), sc.getString("name"), sc.getString("nameAm"), sc.getString("subCity"), sc.optStr("contactPerson"))
        }
        val classes = j.getJSONArray("awarenessClasses").mapObj { c ->
            AwarenessClass(c.getString("id"), c.getString("schoolId"), c.getString("name"), c.getInt("grade"),
                c.getString("groupType"), c.getString("joinCode"), c.optStr("teacherName"))
        }
        val learners = j.getJSONArray("awarenessLearners").mapObj { l ->
            val modObj = l.getJSONObject("completedModules")
            val mods = buildMap { modObj.keys().forEach { k -> put(k, modObj.getDouble(k)) } }
            AwarenessLearner(l.getString("id"), l.getString("classId"), l.getString("code"), l.getInt("grade"),
                l.getString("consent"), l.optStr("consentRef"), l.optDbl("preScore"), l.optIntN("preCorrect"), l.optIntN("preTotal"),
                l.optDbl("postScore"), l.optIntN("postCorrect"), l.optIntN("postTotal"), mods)
        }
        val pings = j.getJSONArray("awarenessToolPings").mapObj { p ->
            AwarenessToolPing(p.getString("toolId"), p.getString("lang"), p.getLong("createdAtEpochMs"))
        }
        val gates = j.getJSONArray("expansionGates").mapObj { g ->
            ExpansionGate(g.getString("id"), g.getString("phase"), g.getString("code"),
                g.getString("titleEn"), g.getString("titleAm"), g.getString("detailEn"), g.getString("detailAm"),
                g.getString("category"), g.getString("status"), g.getBoolean("blocking"), g.optStr("notes"),
                g.getJSONArray("evidence").mapObj { e ->
                    GateEvidence(e.getString("id"), e.getString("fileName"), e.optStr("description"),
                        e.getString("checksumSha256"), e.getLong("fileSize"), e.getString("uploadedByName"), e.getLong("createdAtEpochMs"))
                },
                g.getJSONArray("events").mapObj { ev ->
                    GateEvent(ev.getString("id"), ev.getString("kind"), ev.optStr("fromStatus"), ev.optStr("toStatus"),
                        ev.optStr("note"), ev.optStr("actorName"), ev.getLong("createdAtEpochMs"))
                })
        }
        val acfsItems = j.getJSONArray("acfsItems").mapObj { a ->
            AcfsGateItem(a.getString("id"), a.getString("domain"), a.getString("code"),
                a.getString("titleEn"), a.getString("titleAm"), a.getString("description"),
                a.getString("status"), a.optStr("owner"), a.optStr("notes"), a.optStr("evidenceRef"), a.getInt("sortOrder"))
        }
        val acfsDec = j.getJSONArray("acfsDecisions").mapObj { d ->
            AcfsGateDecision(d.getString("id"), d.getString("decision"), d.getString("rationale"),
                d.getString("decidedBy"), d.getLong("createdAtEpochMs"))
        }
        val ciiOps = j.getJSONArray("ciiOperators").mapObj { op ->
            CiiOperator(op.getString("id"), op.getString("code"), op.getString("name"), op.getString("nameAm"),
                op.getString("sector"), op.getString("tier"), op.optStr("orgId"), op.optStr("contactEmail"),
                op.getJSONArray("mous").mapObj { m ->
                    CiiMou(m.getString("id"), m.getInt("version"), m.getString("status"),
                        m.getJSONArray("scope").strList(), m.optStr("dataTerms"),
                        m.optLng("signedAtEpochMs"), m.optLng("expiresAtEpochMs"))
                },
                op.getJSONArray("authorizations").mapObj { a ->
                    CiiAuthorization(a.getString("id"), a.getString("reference"), a.getString("status"),
                        a.optLng("issuedAtEpochMs"), a.optLng("expiresAtEpochMs"), a.optStr("scope"))
                },
                op.getJSONArray("systems").mapObj { sy ->
                    CiiSystem(sy.getString("id"), sy.getString("name"), sy.optStr("nameAm"),
                        sy.getString("category"), sy.getString("criticality"), sy.getString("readiness"))
                })
        }
        val fallbackSeed by lazy { SeedData.buildInitialState() }
        val controlMonitors = j.optJSONArray("controlMonitors")?.mapObj { m ->
            ControlMonitor(m.getString("id"), m.getString("code"), m.getString("titleEn"), m.getString("titleAm"),
                m.getString("category"), m.getString("sourceType"), m.getString("targetHint"),
                m.getJSONArray("mappedRuleCodes").strList(), m.getString("status"), m.getString("detail"),
                m.optLng("lastCheckedAtEpochMs"))
        } ?: fallbackSeed.controlMonitors
        val monitorRuns = j.optJSONArray("monitorRuns")?.mapObj { r ->
            MonitorCheckRun(r.getString("id"), r.getInt("monitorCount"), r.getInt("passed"), r.getInt("warned"),
                r.getInt("failed"), r.getString("probeUrl"), r.getLong("durationMs"), r.getString("summary"),
                r.getLong("executedAtEpochMs"))
        } ?: emptyList()
        val riskRegister = j.optJSONArray("riskRegister")?.mapObj { rk ->
            RiskRegisterEntry(rk.getString("id"), rk.getString("code"), rk.getString("titleEn"), rk.getString("titleAm"),
                rk.getString("description"), rk.getJSONArray("frameworks").strList(),
                rk.getJSONArray("linkedRuleCodes").strList(), rk.getInt("likelihood"), rk.getInt("impact"),
                rk.getInt("residualScore"), rk.getString("treatment"), rk.getString("status"), rk.getString("ownerName"),
                rk.optLng("dueAtEpochMs"), rk.optStr("sourceMonitorId"))
        } ?: fallbackSeed.riskRegister
        val trustCenter = j.optJSONObject("trustCenter")?.let { tc ->
            TrustCenterConfig(tc.getString("organizationId"), tc.getString("publicTitle"), tc.getString("securityContactEmail"),
                tc.getBoolean("ndaRequired"), tc.getBoolean("showComplianceScores"), tc.getBoolean("showControlMonitors"),
                tc.getBoolean("showEthioCertStatus"), tc.getBoolean("showSubprocessors"),
                tc.getJSONArray("subprocessors").strList(), tc.optLng("lastPublishedAtEpochMs"), tc.optStr("publishedSha256"))
        } ?: fallbackSeed.trustCenter
        val campaigns = j.optJSONArray("awarenessCampaigns")?.mapObj { c ->
            AwarenessCampaign(c.getString("id"), c.getString("titleEn"), c.getString("titleAm"),
                c.getString("targetGroup"), c.getInt("sentCount"), c.getInt("reportedCount"), c.getInt("clickedCount"),
                c.getLong("createdAtEpochMs"))
        } ?: fallbackSeed.awarenessCampaigns
        val pbChecks = j.optJSONObject("incidentPlaybookChecks")?.let { obj ->
            buildMap { obj.keys().forEach { k -> put(k, obj.getJSONArray(k).strList()) } }
        } ?: fallbackSeed.incidentPlaybookChecks
        val aiNotes = j.optJSONObject("incidentAiNotes")?.let { obj ->
            buildMap { obj.keys().forEach { k -> put(k, obj.getString(k)) } }
        } ?: emptyMap()

        return AdwaState(
            orgs, users, authEvents, scans, draftAnswers, draftNotes, docs, links, incidents, indicators, syncRuns,
            schools, classes, learners, pings, gates, acfsItems, acfsDec, ciiOps,
            controlMonitors, monitorRuns, riskRegister, trustCenter, campaigns, pbChecks, aiNotes,
        )
    }
}
