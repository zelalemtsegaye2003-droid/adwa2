package com.example.i18n

import com.example.model.Lang

object AppStrings {
    fun pick(lang: Lang, en: String, am: String): String = if (lang == Lang.EN) en else am

    fun format(template: String, vararg args: Any): String {
        var out = template
        args.forEachIndexed { idx, arg ->
            out = out.replace("{$idx}", arg.toString())
        }
        return out
    }

    private val dict: Map<String, Pair<String, String>> = mapOf(
        // Shell & Navigation
        "appName" to ("Adwa Cyber" to "አድዋ ሳይበር"),
        "appSub" to ("Compliance Platform" to "የቅጥ ሥርዓት"),
        "phase1Badge" to ("Phase 1 MVP — Proclamation 1321/2024" to "ዙር 1 — አዋጅ 1321/2024"),
        "navDashboard" to ("Dashboard" to "ዳሽቦርድ"),
        "navCatalogue" to ("Rule Catalogue" to "የደንቦች ማህደር"),
        "navOrganizations" to ("Organizations" to "ተቋማት"),
        "navScans" to ("Compliance Scans" to "የቅጥ ፍተሻዎች"),
        "navFindings" to ("Findings & SLA" to "ግኝቶችና SLA"),
        "navEvidence" to ("Evidence Library" to "የማስረጃ ማኅደር"),
        "navReport" to ("Executive Report" to "የአመራር ሪፖርት"),
        "navSoc" to ("Security Operations" to "የደህንነት ሥራዎች"),
        "navAwareness" to ("Awareness Admin" to "ግንዛቤ (አስተዳዳሪ)"),
        "navLearn" to ("Learner Portal" to "የተማሪ በር"),
        "navTrackC" to ("Track C Gates" to "የTrack C በሮች"),
        "navTeam" to ("Team & Roles" to "ቡድን እና ሚናዎች"),
        "navAudit" to ("Audit Log" to "የምስክር መዝገብ"),
        "language" to ("Language" to "ቋንቋ"),
        "signOut" to ("Sign out" to "ውጣ"),
        "security" to ("MFA Security" to "የMFA ጥበቃ"),

        // Auth
        "signIn" to ("Sign in" to "ግባ"),
        "signInTitle" to ("Welcome back" to "እንኳን ደህና መጡ"),
        "signInSub" to ("Sign in to your Ethiopian compliance workspace" to "የቅጥ ሥራዎን ለመግባት ይግቡ"),
        "email" to ("Email" to "ኢሜይል"),
        "password" to ("Password" to "የመግቢያ ቃል"),
        "captchaLabel" to ("Security check" to "የደህንነት ማረጓጊያ"),
        "captchaPlaceholder" to ("Type characters" to "ፊደላቱን ይጻፉ"),
        "forgotPassword" to ("Forgot password?" to "የመግቢያ ቃል ረሱ?"),
        "createAccount" to ("Create organization" to "ተቋም ይፍጠሩ"),
        "demoAccounts" to ("Demo accounts" to "የሙከራ መለያዎች"),
        "mfaTitle" to ("Two-factor verification" to "የሁለት-ደረጃ ማረጓጊያ"),
        "mfaSub" to ("Enter the 6-digit code from your authenticator or a backup code" to "ከመተግበሪያዎ የ6-አሃዝ ኮድ ወይም የኋላ ኮድ ያስገቡ"),
        "mfaVerify" to ("Verify" to "አረጋግጥ"),
        "backToLogin" to ("Back to sign in" to "ወደ መግቢያ ተመለስ"),

        // Dashboard
        "dashTitle" to ("Compliance Posture Overview" to "የቅጥ ሁኔታ አጠቃላይ እይታ"),
        "dashSub" to ("Live posture across monitored organizations" to "የተቆጣጠሩት ተቋማት የአሁኑ ሁኔታ"),
        "avgScore" to ("Avg compliance score" to "አማካይ የቅጥ ውጤት"),
        "orgsMonitored" to ("Organizations" to "ተቋማት"),
        "rulesEnforced" to ("Rules enforced" to "የሚተገበሩ ደንቦች"),
        "auditDuration" to ("Avg. automated audit" to "አማካይ ራስ-ሰር ፍተሻ"),
        "vsManual" to ("vs 40–80 hrs manual (RQ1 target: >70% reduction)" to "ከ40–80 ሰዓት በእጅ ፍተሻ ፈንታ (RQ1 ግብ: >70%)"),
        "pillarPerf" to ("Pillar performance" to "የምሰሶዎች አፈጻጸም"),
        "findingsSeverity" to ("Findings by severity" to "ግኝቶች በአደጋ ደረጃ"),
        "articleRisk" to ("Article risk heatmap" to "በአንቀጽ የአደጋ ካርታ"),
        "recentScans" to ("Recent scans" to "የቅርብ ፍተሻዎች"),
        "inProgressScans" to ("In-progress assessments" to "በሂደት ላይ ያሉ ግምገማዎች"),
        "resumeScan" to ("Continue" to "ቀጥል"),

        // Catalogue
        "catTitle" to ("Rule Catalogue v1.0" to "የደንቦች ማህደር v1.0"),
        "catSub" to ("77 machine-readable obligations across PD-1321 & NBE-IT" to "77 የሕግ ግዴታዎች (PD-1321 እና NBE-IT)"),
        "searchPlaceholder" to ("Search rules, articles, keywords…" to "ደንቦችን ይፈልጉ…"),
        "allArticles" to ("All articles" to "ሁሉም አንቀጾች"),
        "allPillars" to ("All pillars" to "ሁሉም ምሰሶዎች"),
        "allSeverities" to ("All severities" to "ሁሉም ደረጃዎች"),
        "checkLogic" to ("Automated check" to "ራስ-ሰር ፍተሻ"),
        "penalty" to ("Penalty" to "ቅጣት"),
        "weight" to ("Weight" to "ክብደት"),
        "priority" to ("Priority" to "ቅድሚያ"),

        // Orgs
        "orgTitle" to ("Organizations" to "ተቋማት"),
        "orgSub" to ("Pilot and monitored organizations" to "የሙከራ እና የሚቆጣጠሩ ተቋማት"),
        "addOrg" to ("Add organization" to "ተቋም ያክሉ"),
        "newScan" to ("New scan" to "አዲስ ፍተሻ"),
        "noScans" to ("No scans yet" to "እስካሁን ፍተሻ የለም"),

        // Scan Wizard
        "scanTitle" to ("Compliance Scan" to "የቅጥ ፍተሻ"),
        "scanSub" to ("Evidence-based audit across obligations" to "በማስረጃ ላይ የተመሠረተ ፍተሻ"),
        "step1" to ("Setup" to "ዝግጅት"),
        "step2" to ("Assess" to "ግምገማ"),
        "step3" to ("Results" to "ውጤት"),
        "startScan" to ("Start scan" to "ፍተሻ ይጀምሩ"),
        "runEngine" to ("Run compliance engine" to "የቅጥ ሞተሩን ያሂዱ"),
        "answerYes" to ("Compliant" to "ተሟልቷል"),
        "answerPartial" to ("Partial" to "በከፊል"),
        "answerNo" to ("Non-compliant" to "አልተሟለም"),
        "answerNa" to ("N/A" to "አይመለከትም"),
        "autoSaved" to ("Draft saved" to "ረቂቅ ተቀምጧል"),
        "discardScan" to ("Discard draft" to "ረቂቁን ይጥሉ"),
        "answerAllToSubmit" to ("Answer every obligation before submitting" to "ለማስገባት ሁሉንም ግዴታዎች መመለስ አለብዎት"),
        "naNoteRequired" to ("N/A answers require a justification note (>=5 chars)" to "ለማመልከቻ-የለውም መልሶች ማብራሪያ (>=5 ፊደላት) ያስፈልጋል"),
        "evSuggestYes" to ("Evidence suggests Yes" to "ማስረጃ አዎ ይመልሳል"),
        "overallScore" to ("Overall compliance score" to "አጠቃላይ የቅጥ ውጤት"),
        "remediationPlan" to ("Smart remediation plan (risk-ranked)" to "የማስተካከያ ዕቅድ (በአደጋ ተራ)"),

        // Findings & SLA
        "findTitle" to ("Findings & Remediation" to "ግኝቶች እና ማስተካከያ"),
        "findSub" to ("Track corrective actions & Procl. 1426/2026 SLAs" to "ማስተካከያዎችንና የSLA ጊዜ ገደቦችን ይከታተሉ"),
        "slaPolicy" to ("SLA: Critical 30d · High 60d · Medium 90d" to "SLA: ወሳኝ 30 ቀን · ከፍተኛ 60 ቀን · መካከለኛ 90 ቀን"),
        "stOpen" to ("Open" to "ክፍት"),
        "stProgress" to ("In progress" to "በሂደት"),
        "stRemediated" to ("Remediated" to "ተስተካክሏል"),
        "stWaived" to ("Waived" to "ተተው"),
        "slaOnTrack" to ("On track" to "በጊዜው ላይ"),
        "slaDueSoon" to ("Due soon" to "ቀርቧል"),
        "slaOverdue" to ("Overdue" to "ዘግያል"),
        "slaClosedOnTime" to ("Closed on time" to "በወቅቱ ተዘግቷል"),
        "slaClosedLate" to ("Closed late" to "ዘግዶ ተዘግቷል"),
        "slaWaived" to ("SLA waived" to "ጊዜ ገደቡ ተቋርጧል"),
        "waiveTitle" to ("Waiver justification" to "የውልቀት ማብራሪያ"),

        // Evidence
        "evTitle" to ("Evidence Library" to "የማስረጃ ማኅደር"),
        "evSub" to ("Compliance artefacts sealed with SHA-256 checksums" to "በSHA-256 የተባሉ የቅጥ ማስረጃዎች"),
        "evUpload" to ("Upload evidence" to "ማስረጃ ይስቀሉ"),
        "evSatisfiedFull" to ("Satisfied (fresh evidence)" to "የተሟላ (ትኩስ ማስረጃ)"),
        "evStaleEvidence" to ("Stale evidence" to "አረመን ማስረጃ"),
        "evNoEvidence" to ("Coverage gap" to "ማስረጃ የለም"),

        // Report
        "repTitle" to ("Executive Report" to "ለአመራሮች ሪፖርት"),
        "repSub" to ("Board-ready summary of a completed scan" to "ለቦርድ የተዘጋጀ ማጠቃለያ"),
        "repDownloadPdf" to ("Export PDF" to "PDF አውጣ"),

        // SOC
        "socTitle" to ("Security Operations Centre" to "የደህንነት ሥራ ማዕከል"),
        "socSub" to ("CERT-as-a-Service · NIST SP 800-61 · Ethio-CERT sync" to "CERT-እንደ-አገልግሎት · NIST SP 800-61 · ከኢትዮ-CERT ጋር ማመሳሰል"),
        "socOverview" to ("Overview" to "አጠቃላይ እይታ"),
        "socIncidents" to ("Incidents" to "ክስተቶች"),
        "socThreats" to ("Threat Intel" to "የስጋት መረጃ"),
        "socSyncIngest" to ("Pull national feed" to "ብሔራዊ መረጃ አምጣ"),
        "socSyncReport" to ("Report upward (anonymised)" to "ማሳወቅ (ስም-አጥፋት)"),
        "incNotify" to ("Notify organisation" to "ተቋሙን አሳውቅ"),

        // Awareness & Learn
        "awTitle" to ("Multilingual Awareness Platform — Track B" to "ባለብዙ ቋንቋ የግንዛቤ መድረክ — መስመር B"),
        "awSub" to ("Amharic-first curriculum · 5 Addis Ababa pilot schools · RQ2 ANOVA" to "በአማርኛ የመጀመሪያ ትምህርት · በ5 አዲስ አበባ ትምህርት ቤቶች · RQ2 ANOVA"),
        "learnTitle" to ("Adwa Cyber Awareness" to "የአድዋ ሳይበር ግንዛቤ"),
        "learnSub" to ("Free cyber-safety lessons & AI tools — Amharic and English" to "ነፃ የሳይበር ደህንነት ትምህርቶችና AI መሳሪያዎች"),

        // Track C
        "gateTitle" to ("Track C — Gated Expansion" to "Track C — በበር የታጠረ መስፋፋት"),
        "gateSub" to ("Phase 4 (ACFS) & Phase 5 (CII) advance only through verified gates" to "ምዕራፍ 4 (ACFS) እና ምዕራፍ 5 (CII) በተረጋገጡ በሮች ብቻ ይራመዳሉ"),
        "gateOpenChip" to ("GATE OPEN" to "በር ክፍት ነው"),
        "gateLockedChip" to ("GATE LOCKED" to "በር ተቆልፏል"),

        // Severities & common
        "critical" to ("Critical" to "ወሳኝ"),
        "high" to ("High" to "ከፍተኛ"),
        "medium" to ("Medium" to "መካከለኛ"),
        "low" to ("Low" to "ዝቅተኛ"),
        "save" to ("Save" to "አስቀምጥ"),
        "cancel" to ("Cancel" to "ተው"),
        "close" to ("Close" to "ዝጋ"),
    )

    fun t(lang: Lang, key: String, vararg args: Any): String {
        val pair = dict[key] ?: return key
        val raw = if (lang == Lang.EN) pair.first else pair.second
        return if (args.isEmpty()) raw else format(raw, *args)
    }

    fun severityLabel(lang: Lang, severity: String): String = when (severity.lowercase()) {
        "critical" -> t(lang, "critical")
        "high" -> t(lang, "high")
        "medium" -> t(lang, "medium")
        "low" -> t(lang, "low")
        else -> severity
    }

    fun pillarLabel(lang: Lang, pillar: String): String = when (pillar) {
        "Legal" -> pick(lang, "Legal", "ሕጋዊ")
        "Technical" -> pick(lang, "Technical", "ቴክኒካል")
        "Organisational" -> pick(lang, "Organisational", "ድርጅታዊ")
        "Awareness" -> pick(lang, "Awareness", "ግንዛቤ")
        "Governance" -> pick(lang, "Governance", "የአስተዳደር ሥርዓት")
        "Automation" -> pick(lang, "Automation", "ራስ-ሰርነት")
        "Risk Management" -> pick(lang, "Risk Management", "የስጋት አስተዳደር")
        "Cyber Security" -> pick(lang, "Cyber Security", "የሳይበር ደህንነት")
        "Continuity" -> pick(lang, "Continuity", "ቀጣይነት")
        else -> pillar
    }
}
