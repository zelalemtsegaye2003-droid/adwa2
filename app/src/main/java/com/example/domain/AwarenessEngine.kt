package com.example.domain

import kotlin.math.*

object AwarenessEngine {

    // ── RQ2 Statistical Analysis (Paired t + One-Way ANOVA) ────────────────────

    data class ScorePair(val pre: Double, val post: Double)

    data class GroupStat(
        val n: Int,
        val meanPre: Double,
        val meanPost: Double,
        val meanGain: Double,
        val sdGain: Double,
        val minGain: Double,
        val maxGain: Double,
    )

    data class PairedTResult(
        val n: Int,
        val meanGain: Double,
        val sdGain: Double,
        val t: Double,
        val df: Int,
        val p: Double,
        val significant: Boolean,
    )

    data class AnovaResult(
        val groups: List<String>,
        val k: Int,
        val n: Int,
        val dfBetween: Int,
        val dfWithin: Int,
        val ssBetween: Double,
        val ssWithin: Double,
        val msBetween: Double,
        val msWithin: Double,
        val f: Double,
        val p: Double,
        val etaSquared: Double,
        val significant: Boolean,
        val groupMeans: Map<String, Double>,
    )

    data class HistBucket(val label: String, val count: Int)

    private const val ALPHA = 0.05

    private fun gammaln(x: Double): Double {
        val cof = doubleArrayOf(
            76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5,
        )
        var y = x
        var tmp = x + 5.5
        tmp -= (x + 0.5) * ln(tmp)
        var ser = 1.000000000190015
        for (j in 0 until 6) ser += cof[j] / ++y
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    private fun betacf(a: Double, b: Double, x: Double): Double {
        val maxIt = 200
        val eps = 3e-12
        val fpMin = 1e-300
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - (qab * x) / qap
        if (abs(d) < fpMin) d = fpMin
        d = 1.0 / d
        var h = d
        for (m in 1..maxIt) {
            val m2 = 2 * m
            var aa = (m * (b - m) * x) / ((qam + m2) * (a + m2))
            d = 1.0 + aa * d
            if (abs(d) < fpMin) d = fpMin
            c = 1.0 + aa / c
            if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d
            h *= d * c
            aa = (-(a + m) * (qab + m) * x) / ((a + m2) * (qap + m2))
            d = 1.0 + aa * d
            if (abs(d) < fpMin) d = fpMin
            c = 1.0 + aa / c
            if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d
            val del = d * c
            h *= del
            if (abs(del - 1.0) < eps) break
        }
        return h
    }

    fun betai(a: Double, b: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val bt = exp(gammaln(a + b) - gammaln(a) - gammaln(b) + a * ln(x) + b * ln(1.0 - x))
        return if (x < (a + 1.0) / (a + b + 2.0)) {
            (bt * betacf(a, b, x)) / a
        } else {
            1.0 - (bt * betacf(b, a, 1.0 - x)) / b
        }
    }

    fun tDistTwoSidedP(t: Double, df: Int): Double {
        if (!t.isFinite() || df <= 0) return Double.NaN
        return betai(df / 2.0, 0.5, df / (df + t * t))
    }

    fun fDistUpperP(f: Double, d1: Int, d2: Int): Double {
        if (!f.isFinite() || f <= 0.0 || d1 <= 0 || d2 <= 0) return Double.NaN
        return betai(d2 / 2.0, d1 / 2.0, d2 / (d2 + d1 * f))
    }

    private fun mean(xs: List<Double>): Double = if (xs.isEmpty()) 0.0 else xs.sum() / xs.size

    private fun sd(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = mean(xs)
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }

    private fun round2(x: Double): Double = round(x * 100.0) / 100.0
    private fun round4(x: Double): Double = round(x * 10000.0) / 10000.0

    fun pairedT(pairs: List<ScorePair>): PairedTResult? {
        val n = pairs.size
        if (n < 2) return null
        val gains = pairs.map { it.post - it.pre }
        val m = mean(gains)
        val s = sd(gains)
        val t = when {
            s > 0.0 -> m / (s / sqrt(n.toDouble()))
            m == 0.0 -> 0.0
            m > 0.0 -> Double.POSITIVE_INFINITY
            else -> Double.NEGATIVE_INFINITY
        }
        val df = n - 1
        val p = if (t.isFinite()) tDistTwoSidedP(t, df) else 0.0
        val clampedP = p.coerceIn(0.0, 1.0)
        return PairedTResult(
            n = n,
            meanGain = round2(m),
            sdGain = round2(s),
            t = if (t.isFinite()) round4(t) else t,
            df = df,
            p = round4(clampedP),
            significant = clampedP < ALPHA,
        )
    }

    fun anovaOneWay(grouped: Map<String, List<Double>>): AnovaResult? {
        val groups = grouped.keys.filter { grouped[it]?.isNotEmpty() == true }
        val k = groups.size
        val all = groups.flatMap { grouped[it].orEmpty() }
        val n = all.size
        if (k < 2 || n < k + 1) return null

        val grand = mean(all)
        val groupMeans = mutableMapOf<String, Double>()
        var ssBetween = 0.0
        var ssWithin = 0.0
        for (g in groups) {
            val xs = grouped[g].orEmpty()
            val gm = mean(xs)
            groupMeans[g] = round2(gm)
            ssBetween += xs.size * (gm - grand) * (gm - grand)
            for (x in xs) ssWithin += (x - gm) * (x - gm)
        }
        val dfBetween = k - 1
        val dfWithin = n - k
        val msBetween = ssBetween / dfBetween
        val msWithin = ssWithin / dfWithin
        val f = when {
            msWithin > 0.0 -> msBetween / msWithin
            msBetween > 0.0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        }
        val p = when {
            f.isFinite() && f > 0.0 -> fDistUpperP(f, dfBetween, dfWithin)
            f > 0.0 -> 0.0
            else -> 1.0
        }.coerceIn(0.0, 1.0)
        val ssTotal = ssBetween + ssWithin
        return AnovaResult(
            groups = groups,
            k = k,
            n = n,
            dfBetween = dfBetween,
            dfWithin = dfWithin,
            ssBetween = round2(ssBetween),
            ssWithin = round2(ssWithin),
            msBetween = round2(msBetween),
            msWithin = round2(msWithin),
            f = if (f.isFinite()) round4(f) else f,
            p = round4(p),
            etaSquared = if (ssTotal > 0.0) round4(ssBetween / ssTotal) else 0.0,
            significant = p < ALPHA,
            groupMeans = groupMeans,
        )
    }

    fun groupStat(pairs: List<ScorePair>): GroupStat {
        val gains = pairs.map { it.post - it.pre }
        return GroupStat(
            n = pairs.size,
            meanPre = round2(mean(pairs.map { it.pre })),
            meanPost = round2(mean(pairs.map { it.post })),
            meanGain = round2(mean(gains)),
            sdGain = round2(sd(gains)),
            minGain = if (gains.isNotEmpty()) round2(gains.min()) else 0.0,
            maxGain = if (gains.isNotEmpty()) round2(gains.max()) else 0.0,
        )
    }

    fun gainHistogram(gains: List<Double>, width: Int = 10): List<HistBucket> {
        val buckets = mutableMapOf<Int, Int>()
        for (g in gains) {
            val b = floor(g / width).toInt() * width
            buckets[b] = (buckets[b] ?: 0) + 1
        }
        val minVal = if (gains.isEmpty()) 0.0 else min(0.0, gains.min())
        val maxVal = if (gains.isEmpty()) 0.0 else max(0.0, gains.max())
        val minB = floor(minVal / width).toInt() * width
        val maxB = ceil(maxVal / width).toInt() * width
        val out = mutableListOf<HistBucket>()
        var b = minB
        while (b <= maxB) {
            out.add(HistBucket("$b–${b + width}", buckets[b] ?: 0))
            b += width
        }
        return out
    }

    // ── AI Enhancement Lab (Table 23) Engines ──────────────────────────────────

    data class PasswordFinding(val code: String, val severity: String, val textEn: String, val textAm: String)

    data class PasswordReport(
        val score: Int,
        val band: String,
        val bandEn: String,
        val bandAm: String,
        val entropyBits: Int,
        val crackTimeEn: String,
        val crackTimeAm: String,
        val findings: List<PasswordFinding>,
        val tipsEn: List<String>,
        val tipsAm: List<String>,
    )

    private val commonPasswords = setOf(
        "password", "123456", "12345678", "123456789", "qwerty", "abc123", "letmein",
        "admin", "welcome", "iloveyou", "monkey", "dragon", "sunshine", "football",
        "ethiopia", "addis", "abebe", "kebede", "tesfaye", "selam", "habesha",
        "lili123", "hello123", "pass123", "1234qwer", "qazwsx", "1q2w3e4r",
    )
    private val keyboardRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890")
    private val nameRegex = Regex("^(abebe|kebede|tesfaye|selam|habesha|ethiopia|addis|abraha|tigist|abebech)", RegexOption.IGNORE_CASE)
    private val yearRegex = Regex("(19[6-9]\\d|20[0-2]\\d)")
    private val phoneRegex = Regex("(^|\\D)(09\\d{8}|9\\d{8}|(\\+251)?9\\d{8})(\\D|$)")

    private fun hasSequentialRun(pw: String, minLen: Int = 4): Boolean {
        val s = pw.lowercase()
        for (i in 0..(s.length - minLen)) {
            val run = s.substring(i, i + minLen)
            var asc = true
            var desc = true
            for (j in 1 until run.length) {
                val d = run[j].code - run[j - 1].code
                if (d != 1) asc = false
                if (d != -1) desc = false
            }
            val kb = keyboardRows.any { it.contains(run) }
            if (asc || desc || kb) return true
        }
        return false
    }

    private fun hasRepeatRun(pw: String, minLen: Int = 3): Boolean {
        for (i in 0..(pw.length - minLen)) {
            val seg = pw.substring(i, i + minLen)
            if (seg.all { it == seg[0] }) return true
        }
        return false
    }

    private fun crackTime(bits: Double): Pair<String, String> {
        val seconds = 2.0.pow(bits - 1.0) / 1e8
        if (seconds < 1.0) return "less than a second" to "ከአንድ ሰከንድ ባነሰ ጊዜ"
        val units = listOf(
            Triple(60.0, "seconds", "ሰከንዶች"),
            Triple(60.0, "minutes", "ደቂቃዎች"),
            Triple(24.0, "hours", "ሰዓታት"),
            Triple(365.0, "days", "ቀናት"),
            Triple(100.0, "years", "ዓመታት"),
        )
        var v = seconds
        var labelEn = "years"
        var labelAm = "ዓመታት"
        for ((div, en, am) in units) {
            labelEn = en
            labelAm = am
            if (v < div * 10.0) break
            v /= div
        }
        if (labelEn == "years" && v >= 1_000_000.0) {
            return "over a million years — practically unbreakable" to "ከሚሊዮን ዓመት በላይ — በተግባር ሊሰበር አይችልም"
        }
        val n = round(v).toLong().toString()
        return "about $n $labelEn" to "ወደ $n $labelAm ገደማ"
    }

    fun scorePassword(pw: String): PasswordReport {
        if (pw.isEmpty()) {
            return PasswordReport(
                0, "very_weak", "Empty", "ባዶ", 0, "—", "—",
                emptyList(), listOf("Type a password to analyse."), listOf("ለመተንተን የይለፍ ቃል ይጻፉ።"),
            )
        }
        var pool = 0
        val hasLower = pw.any { it in 'a'..'z' }
        val hasUpper = pw.any { it in 'A'..'Z' }
        val hasDigit = pw.any { it.isDigit() }
        val hasSymbol = pw.any { !it.isLetterOrDigit() }
        val hasAmharic = pw.any { it.code in 0x1200..0x137F }
        if (hasLower) pool += 26
        if (hasUpper) pool += 26
        if (hasDigit) pool += 10
        if (hasSymbol) pool += 33
        if (hasAmharic) pool += 345
        val bits = pw.length * log2(max(1, pool).toDouble())

        var penalty = 0
        val findings = mutableListOf<PasswordFinding>()
        if (pw.lowercase() in commonPasswords) {
            penalty += 55
            findings.add(PasswordFinding("common", "critical", "Most frequently stolen password — attackers try it first.", "ከበይነገን በጣም የሚሰረቅ የይለፍ ቃል ነው።"))
        }
        val nameLike = nameRegex.containsMatchIn(pw)
        if (nameLike && pw.length <= 12) {
            penalty += 25
            findings.add(PasswordFinding("name", "warn", "Contains an Ethiopian name or place — add unrelated words.", "የኢትዮጵያ ስም ወይም ቦታ ይዟል።"))
        }
        if (hasSequentialRun(pw)) {
            penalty += 20
            findings.add(PasswordFinding("sequence", "warn", "Keyboard or alphabet run (abcd / 1234).", "የቁልፍ ሰሌዳ ወይም የፊደል ተከታይ ይዟል።"))
        }
        if (hasRepeatRun(pw)) {
            penalty += 15
            findings.add(PasswordFinding("repeat", "warn", "Repeats the same character (e.g. aaa).", "ተመሳሳይ ፊደል ይደግማል።"))
        }
        val hasYear = yearRegex.containsMatchIn(pw)
        if (hasYear) {
            penalty += 12
            findings.add(PasswordFinding("year", "warn", "Contains a year — attackers guess birth years early.", "ዓመት ይዟል።"))
        }
        val hasPhone = phoneRegex.containsMatchIn(pw)
        if (hasPhone) {
            penalty += 18
            findings.add(PasswordFinding("phone", "warn", "Looks like a phone number (+251/09…).", "የስልክ ቁጥር ይመስላል።"))
        }
        if (pw.length < 8) {
            penalty += 25
            findings.add(PasswordFinding("short", "critical", "Shorter than 8 characters — too small a search space.", "ከ8 ፊደላት ያነሰ ነው።"))
        }
        val classes = listOf(hasLower, hasUpper, hasDigit, hasSymbol, hasAmharic).count { it }
        if (classes <= 1 && pw.length < 16) {
            penalty += 10
            findings.add(PasswordFinding("mono", "info", "Only one character type — mix letters, numbers and symbols.", "አንድ ዓይነት ፊደል ብቻ ነው።"))
        }
        if (pw.length >= 12) {
            findings.add(PasswordFinding("len", "good", "12+ characters — length is your strongest defence.", "12+ ፊደላት — ርዝመት ዋነኛው ጥንካሬ ነው።"))
        }
        if (classes >= 3) {
            findings.add(PasswordFinding("mix", "good", "Good mix of character types.", "የፊደል ዓይነቶች በሚገባ ተደባልቀዋል።"))
        }
        if (hasAmharic) {
            findings.add(PasswordFinding("am", "good", "Amharic Fidel greatly enlarges the search space.", "የአማርኛ ፊደላት የፍለጋውን ቦታ ያስተሏቸዋል።"))
        }

        val effBits = max(0.0, bits - penalty)
        val score = ((effBits / 80.0) * 100.0).roundToInt().coerceIn(0, 100)
        val (band, bandEn, bandAm) = when {
            score < 25 -> Triple("very_weak", "Very weak", "በጣም ደካማ")
            score < 50 -> Triple("weak", "Weak", "ደካማ")
            score < 70 -> Triple("fair", "Fair", "መካከለኛ")
            score < 85 -> Triple("strong", "Strong", "ጠንካራ")
            else -> Triple("very_strong", "Very strong", "በጣም ጠንካራ")
        }
        val (ctEn, ctAm) = crackTime(effBits)
        val tipsEn = mutableListOf<String>()
        val tipsAm = mutableListOf<String>()
        if (pw.length < 12) {
            tipsEn.add("Use a 4-word passphrase like Blue-Donkey-7-Coffee! — long and memorable.")
            tipsAm.add("አራት ያልተዛመዱ ቃላት ይጠቀሙ፣ ለምሳሌ Blue-Donkey-7-Coffee!።")
        }
        if (nameLike || hasYear || hasPhone || pw.lowercase() in commonPasswords) {
            tipsEn.add("Remove names, years and phone numbers.")
            tipsAm.add("ስሞችን፣ ዓመታትንና የስልክ ቁጥሮችን ያስወግዱ።")
        }
        tipsEn.add("Enable two-factor authentication (2FA) wherever offered.")
        tipsAm.add("የሁለት ደረጃ ማረጋገጫ (2FA) ያንብሩት።")

        return PasswordReport(score, band, bandEn, bandAm, effBits.roundToInt(), ctEn, ctAm, findings, tipsEn, tipsAm)
    }

    data class PhishFeature(val code: String, val weight: Int, val en: String, val am: String)

    val PHISH_FEATURES = listOf(
        PhishFeature("link_mismatch", 30, "Displayed link hides a different real address", "የታየው አገናኝ የተለየ እውነተኛ አድራሻ ይደብቃል"),
        PhishFeature("sender_mismatch", 18, "Sender address does not match the brand it claims", "የመልኪያው አድራሻ ከሚጠራው ድርጅት ጋር አይመሳሰልም"),
        PhishFeature("otp_request", 22, "Asks for an OTP / verification code", "የማረጋገጫ ኮድ (OTP) ይጠይቃል"),
        PhishFeature("urgency", 12, "Artificial urgency — act now or lose something", "ውሸታማ ፍጥነት — ወዲያውኑ ካልሠሩ ያጣሉ"),
        PhishFeature("money_lure", 14, "Money lure — prize, refund or lottery in birr", "የገንዘብ ገሚሽ — በብር የሚሰጥ ሽልማት"),
        PhishFeature("generic_greeting", 6, "Generic greeting instead of your name", "በስምዎ ፈንታ አጠቃላይ ሰላምታ"),
        PhishFeature("info_request", 18, "Requests password, PIN or Fayda ID", "የይለፍ ቃል፣ PIN ወይም የፋይዳ ቁጥር ይጠይቃል"),
        PhishFeature("short_link", 12, "Shortened link hides destination", "አጭር አገናኝ መድረሻውን ይደብቃል"),
    )

    fun phishiness(hits: List<String>): Int {
        val weights = PHISH_FEATURES.associate { it.code to it.weight }
        return min(100, hits.sumOf { weights[it] ?: 0 })
    }

    data class FootprintResult(
        val risk: Int,
        val bandEn: String,
        val bandAm: String,
        val categoryScores: Map<String, Int>,
        val tipsEn: List<String>,
        val tipsAm: List<String>,
    )

    fun scoreFootprint(
        answers: Map<String, Int>,
        questions: List<com.example.data.FootprintQuestionData>,
    ): FootprintResult {
        val cats = listOf("exposure", "accounts", "sharing", "network")
        val catScores = mutableMapOf<String, Int>()
        var answeredTotal = 0
        for (cat in cats) {
            val qs = questions.filter { it.category == cat }
            var sum = 0
            var n = 0
            for (q in qs) {
                val idx = answers[q.id] ?: continue
                val pts = q.options.getOrNull(idx)?.points ?: continue
                sum += pts
                n++
            }
            answeredTotal += n
            catScores[cat] = if (n > 0) ((sum.toDouble() / (n * 3.0)) * 100.0).roundToInt() else 0
        }
        val risk = if (answeredTotal == 0) 0 else (catScores.values.sum().toDouble() / cats.size).roundToInt()
        val (bandEn, bandAm) = when {
            risk < 25 -> "Low exposure" to "ዝቅተኛ ተጋላጭነት"
            risk < 50 -> "Moderate exposure" to "መካከለኛ ተጋላጭነት"
            risk < 75 -> "Elevated exposure" to "ከፍተኛ ተጋላጭነት"
            else -> "High exposure" to "በጣም ከፍተኛ ተጋላጭነት"
        }
        val tipBank = mapOf(
            "exposure" to ("Set profiles to friends-only and remove phone number & birth date from public bios." to "መገለጫዎችን ወደ ግል ቀይሩ፤ ስልክ ቁጥርና የልደት ቀንን ያስወግዱ።"),
            "accounts" to ("Give each important account a unique passphrase and enable 2FA." to "ለእያንዳንዱ መለያ የተለየ ረጅም ሐረግና 2FA ያንብሩ።"),
            "sharing" to ("Turn off photo location tagging and accept requests only from known people." to "የቦታ ምልክትን ዝግተው ከምታውቋቸው ሰዎች ብቻ ይቀበሉ።"),
            "network" to ("Use a PIN/fingerprint lock, keep updates automatic, and avoid logins on public Wi-Fi." to "PIN/አሻራ ይጠቀሙ፣ ዝማኔዎችን ያንብሩ፣ በይፋዊ ዋይፋይ አይግቡ።"),
        )
        val worst = catScores.entries.sortedByDescending { it.value }.take(2).filter { it.value >= 34 }
        val tipsEn = worst.mapNotNull { tipBank[it.key]?.first }.ifEmpty {
            listOf("Your habits are strong — keep reviewing privacy settings after every app update.")
        }
        val tipsAm = worst.mapNotNull { tipBank[it.key]?.second }.ifEmpty {
            listOf("ልማዶችዎ ጠንካራ ናቸው — ከእያንዳንድ መተግበሪያ ዝማኔ በኋላ የግላዊነት ማስተካከያዎችን ይገምግሙ።")
        }
        return FootprintResult(risk, bandEn, bandAm, catScores, tipsEn, tipsAm)
    }
}
