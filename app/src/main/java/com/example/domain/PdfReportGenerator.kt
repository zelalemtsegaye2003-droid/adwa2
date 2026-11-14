package com.example.domain

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.i18n.AppStrings
import com.example.model.ComplianceScan
import com.example.model.ExpansionGate
import com.example.model.Framework
import com.example.model.Lang
import com.example.model.Organization
import com.example.model.Rule
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun exportExecutiveReportPdf(
        context: Context,
        scan: ComplianceScan,
        org: Organization?,
        framework: Framework?,
        rules: List<Rule>,
        lang: Lang,
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(4, 120, 87)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val h2Paint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
            isAntiAlias = true
        }

        var y = 44f
        fun nextPageIfNeeded(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 44f
            }
        }

        canvas.drawText("Adwa Cyber — Executive Compliance Report", 36f, y, titlePaint)
        y += 18f
        canvas.drawText("Generated: ${dateFmt.format(Date())} · Language: ${lang.name}", 36f, y, mutedPaint)
        y += 22f

        val orgName = org?.name ?: scan.organizationId
        val fwName = framework?.let { "${it.name} (${it.instrumentNo})" } ?: scan.frameworkCode
        canvas.drawText("Organization: $orgName", 36f, y, h2Paint); y += 16f
        canvas.drawText("Scan: ${scan.name}", 36f, y, bodyPaint); y += 14f
        canvas.drawText("Framework: $fwName", 36f, y, bodyPaint); y += 14f
        canvas.drawText("Auditor: ${scan.auditorName} · Completed: ${scan.completedAtEpochMs?.let { dateFmt.format(Date(it)) } ?: "—"}", 36f, y, bodyPaint); y += 22f

        val overall = scan.overallScore ?: 0.0
        canvas.drawText("Overall Compliance Score: ${overall}%", 36f, y, titlePaint); y += 18f
        canvas.drawText("Open / Total Findings: ${scan.findingsCount} (${scan.criticalFindings} critical)", 36f, y, bodyPaint); y += 20f

        canvas.drawText("Pillar Breakdown", 36f, y, h2Paint); y += 15f
        for ((pillar, score) in scan.pillarScores) {
            nextPageIfNeeded(16f)
            canvas.drawText("• ${AppStrings.pillarLabel(lang, pillar)}: $score%", 44f, y, bodyPaint)
            y += 14f
        }
        y += 12f

        val ruleMap = rules.associateBy { it.code }
        val topFindings = scan.results
            .filter { it.isFinding }
            .sortedByDescending { it.riskRank }
            .take(15)

        canvas.drawText("Top Risk-Ranked Findings & Remediation Priorities", 36f, y, h2Paint); y += 16f
        for ((idx, res) in topFindings.withIndex()) {
            val r = ruleMap[res.ruleCode] ?: continue
            nextPageIfNeeded(38f)
            val line1 = "${idx + 1}. [${r.code}] ${r.titleEn} (${r.severity.uppercase()} · Risk ${res.riskRank} · ${res.status})"
            canvas.drawText(line1.take(92), 40f, y, h2Paint.apply { textSize = 10f })
            y += 13f
            val rem = "Action: ${r.remediationEn}".take(105)
            canvas.drawText(rem, 48f, y, bodyPaint)
            y += 16f
        }

        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = scan.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "adwa_report_$safeName.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun exportGateDossierPdf(
        context: Context,
        gates: List<ExpansionGate>,
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(4, 120, 87)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val h2Paint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            isAntiAlias = true
        }

        var y = 44f
        fun nextPageIfNeeded(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 44f
            }
        }

        canvas.drawText("Adwa Cyber — Track C Governance Gate Dossier", 36f, y, titlePaint); y += 18f
        canvas.drawText("Generated: ${dateFmt.format(Date())}", 36f, y, bodyPaint); y += 22f

        for (phase in listOf("P4_ACFS", "P5_CII")) {
            val readiness = TrackCEngine.phaseReadiness(phase, gates)
            nextPageIfNeeded(30f)
            canvas.drawText(
                "$phase — ${if (readiness.open) "GATE OPEN" else "GATE LOCKED"} (${readiness.satisfied}/${readiness.total}, ${readiness.readinessPct}%)",
                36f, y, h2Paint
            )
            y += 18f
            for (g in gates.filter { it.phase == phase }) {
                nextPageIfNeeded(40f)
                canvas.drawText("• [${g.code}] ${g.titleEn} — ${g.status}", 44f, y, bodyPaint); y += 13f
                canvas.drawText("  Artefacts sealed: ${g.evidence.size} · Notes: ${g.notes ?: "—"}".take(95), 48f, y, bodyPaint); y += 16f
            }
            y += 10f
        }

        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "adwa_track_c_dossier.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun sharePdf(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
