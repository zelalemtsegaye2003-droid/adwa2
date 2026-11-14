package com.example.domain

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.example.model.ControlMonitor
import com.example.model.EvidenceDocument
import com.example.model.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

object PostureProbeEngine {

    data class ProbeOutcome(
        val status: String, // PASS | WARN | FAIL
        val detail: String,
    )

    suspend fun runAllProbes(
        context: Context,
        monitors: List<ControlMonitor>,
        probeUrl: String,
        users: List<UserAccount>,
        evidenceDocs: List<EvidenceDocument>,
    ): List<ControlMonitor> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val tlsProbeResult = probeLiveHttpsEndpoint(probeUrl)

        monitors.map { m ->
            val outcome = when (m.code) {
                "MON-DEV-LOCK" -> checkDeviceScreenLock(context)
                "MON-DEV-KEYSTORE" -> checkHardwareKeystore(context)
                "MON-DEV-BUILD" -> checkAppBuildHardening(context)
                "MON-NET-TRANSPORT" -> checkNetworkTransport(context)
                "MON-TLS-ENDPOINT" -> tlsProbeResult
                "MON-IAM-MFA" -> checkPrivilegedMfaCoverage(users)
                "MON-EVID-FRESH" -> checkEvidenceFreshness(evidenceDocs)
                else -> ProbeOutcome("WARN", "Monitor rule evaluated via local check")
            }
            m.copy(
                status = outcome.status,
                detail = outcome.detail,
                targetHint = if (m.code == "MON-TLS-ENDPOINT") probeUrl else m.targetHint,
                lastCheckedAtEpochMs = now,
            )
        }
    }

    private fun checkDeviceScreenLock(context: Context): ProbeOutcome {
        return runCatching {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val secure = km?.isDeviceSecure == true
            if (secure) {
                ProbeOutcome("PASS", "Device credential/biometric lock enforced (KeyguardManager.isDeviceSecure = true)")
            } else {
                ProbeOutcome("FAIL", "No PIN, pattern, password, or biometric screen lock configured on this device")
            }
        }.getOrElse { e ->
            ProbeOutcome("WARN", "Unable to inspect Keyguard state: ${e.message}")
        }
    }

    private fun checkHardwareKeystore(context: Context): ProbeOutcome {
        return runCatching {
            val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            val alias = "adwa_posture_probe_ec"
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(alias)) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                kpg.initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
                kpg.generateKeyPair()
            }
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val privKey = entry?.privateKey
            val hwBacked = if (privKey != null) {
                val factory = KeyFactory.getInstance(privKey.algorithm, "AndroidKeyStore")
                val keyInfo = factory.getKeySpec(privKey, KeyInfo::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                        keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
                } else {
                    @Suppress("DEPRECATION")
                    keyInfo.isInsideSecureHardware
                }
            } else false

            when {
                hwBacked && hasStrongBox -> ProbeOutcome("PASS", "Hardware TEE + StrongBox KeyStore verified on Android ${Build.VERSION.RELEASE}")
                hwBacked -> ProbeOutcome("PASS", "Hardware-backed AndroidKeyStore TEE verified (API ${Build.VERSION.SDK_INT})")
                else -> ProbeOutcome("WARN", "Software KeyStore active (no hardware TEE / emulator environment)")
            }
        }.getOrElse { e ->
            ProbeOutcome("WARN", "KeyStore probe inconclusive: ${e.localizedMessage}")
        }
    }

    private fun checkAppBuildHardening(context: Context): ProbeOutcome {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val allowBackup = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        return when {
            !isDebuggable && !allowBackup -> ProbeOutcome("PASS", "Release build verified · allowBackup=false")
            isDebuggable && !allowBackup -> ProbeOutcome("WARN", "Debuggable build active (expected during development) · allowBackup=false")
            else -> ProbeOutcome("FAIL", "Insecure flags detected: debuggable=$isDebuggable, allowBackup=$allowBackup")
        }
    }

    private fun checkNetworkTransport(context: Context): ProbeOutcome {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return ProbeOutcome("WARN", "ConnectivityManager unavailable")
            val net = cm.activeNetwork ?: return ProbeOutcome("FAIL", "No active network route available")
            val caps = cm.getNetworkCapabilities(net) ?: return ProbeOutcome("WARN", "Network capabilities unknown")
            val lp = cm.getLinkProperties(net)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lp?.isPrivateDnsActive == true else false
            val transport = listOfNotNull(
                if (vpn) "VPN" else null,
                if (wifi) "Wi-Fi" else null,
                if (cell) "Cellular" else null,
            ).ifEmpty { listOf("Ethernet/Other") }.joinToString("+")

            if (validated && (vpn || privateDns)) {
                ProbeOutcome("PASS", "Validated $transport · ${if (vpn) "VPN tunnel active" else "Private DNS active"}")
            } else if (validated) {
                ProbeOutcome("WARN", "Validated $transport · No active VPN or Private DNS detected")
            } else {
                ProbeOutcome("FAIL", "Network $transport not validated for outbound TLS traffic")
            }
        }.getOrElse { e ->
            ProbeOutcome("WARN", "Network probe error: ${e.message}")
        }
    }

    private fun probeLiveHttpsEndpoint(rawUrl: String): ProbeOutcome {
        val cleanUrl = rawUrl.trim().let { if (it.startsWith("https://")) it else "https://$it" }
        return runCatching {
            val started = System.currentTimeMillis()
            val conn = (URL(cleanUrl).openConnection() as HttpsURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "HEAD"
                instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                val rtt = System.currentTimeMillis() - started
                val cipher = conn.cipherSuite ?: "unknown"
                val certs = conn.serverCertificates
                val leaf = certs.firstOrNull() as? X509Certificate
                val daysLeft = leaf?.notAfter?.let { exp ->
                    TimeUnit.MILLISECONDS.toDays(exp.time - System.currentTimeMillis())
                }
                val hsts = conn.getHeaderField("Strict-Transport-Security")
                val minDaysOk = daysLeft == null || daysLeft >= 14
                when {
                    code in 200..399 && minDaysOk && !hsts.isNullOrBlank() ->
                        ProbeOutcome("PASS", "HTTP $code (${rtt}ms) · $cipher · Cert expires in ${daysLeft ?: "?"}d · HSTS present")
                    code in 200..499 && minDaysOk ->
                        ProbeOutcome("WARN", "HTTP $code (${rtt}ms) · $cipher · Cert ${daysLeft ?: "?"}d · HSTS header missing")
                    else ->
                        ProbeOutcome("FAIL", "HTTPS probe issue: HTTP $code · Cert remaining ${daysLeft ?: "?"}d")
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e ->
            ProbeOutcome("FAIL", "Live TLS probe failed for $cleanUrl: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun checkPrivilegedMfaCoverage(users: List<UserAccount>): ProbeOutcome {
        val privileged = users.filter {
            it.status == "ACTIVE" && (it.role.name == "SUPER_ADMIN" || it.role.name == "ORG_ADMIN" || it.role.name == "COMPLIANCE_OFFICER")
        }
        if (privileged.isEmpty()) return ProbeOutcome("WARN", "No active privileged operators found")
        val mfaEnabled = privileged.count { it.mfaEnabled }
        val pct = (mfaEnabled * 100) / privileged.size
        return when {
            pct == 100 -> ProbeOutcome("PASS", "$mfaEnabled/${privileged.size} privileged accounts have TOTP MFA enabled (100%)")
            pct >= 50 -> ProbeOutcome("WARN", "$mfaEnabled/${privileged.size} privileged accounts enforce TOTP MFA ($pct%)")
            else -> ProbeOutcome("FAIL", "Only $mfaEnabled/${privileged.size} privileged accounts enforce TOTP MFA ($pct% < 50%)")
        }
    }

    private fun checkEvidenceFreshness(docs: List<EvidenceDocument>): ProbeOutcome {
        val active = docs.filter { it.status == "ACTIVE" }
        if (active.isEmpty()) return ProbeOutcome("FAIL", "Evidence vault has 0 active artifacts")
        val freshCount = active.count { d -> RulesEngine.freshnessOf(d.category, d.createdAtEpochMs, d.lastVerifiedAtEpochMs).first }
        val pct = (freshCount * 100) / active.size
        return when {
            pct >= 80 -> ProbeOutcome("PASS", "$freshCount/${active.size} active evidence artifacts verified within freshness SLA ($pct%)")
            pct >= 50 -> ProbeOutcome("WARN", "$freshCount/${active.size} evidence artifacts are fresh ($pct% — review stale files)")
            else -> ProbeOutcome("FAIL", "Only $freshCount/${active.size} evidence artifacts are fresh ($pct%)")
        }
    }
}
