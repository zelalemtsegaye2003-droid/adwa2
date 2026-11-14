package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object GeminiWorkflows {

    val policySchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Official compliance policy title"),
            "summary" to Schema.string(description = "Concise executive summary covering scope and regulatory intent"),
            "policyText" to Schema.string(description = "Complete structured policy document ready to save as compliance evidence"),
            "checklist" to Schema.array(
                items = Schema.string(),
                description = "3 to 6 verifiable implementation steps",
            ),
        )
    )

    val evidenceInspectSchema = Schema.obj(
        properties = mapOf(
            "verdict" to Schema.enumeration(
                values = listOf("VERIFIED", "PARTIAL", "GAP"),
                description = "Overall audit verification outcome for the uploaded visual evidence",
            ),
            "confidencePct" to Schema.integer(description = "Confidence score from 0 to 100"),
            "observedControls" to Schema.array(
                items = Schema.string(),
                description = "Specific controls, configurations, or dates visible in the image",
            ),
            "missingGaps" to Schema.array(
                items = Schema.string(),
                description = "Missing items or follow-up steps required to satisfy the obligation",
            ),
        )
    )

    val socEnrichSchema = Schema.obj(
        properties = mapOf(
            "mitreMapping" to Schema.string(description = "Primary MITRE ATT&CK tactic and technique ID"),
            "riskAssessment" to Schema.string(description = "2-sentence impact and urgency assessment for Ethiopian financial/CII infrastructure"),
            "containmentActions" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 immediate technical containment and Ethio-CERT reporting steps",
            ),
        )
    )

    fun createPolicyModel() = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = "gemini-3.8-flash",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = policySchema
        },
    )

    fun createVisionInspectorModel() = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = "gemini-3.8-flash",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = evidenceInspectSchema
        },
    )

    fun createSocEnrichModel() = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = "gemini-3.8-flash",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = socEnrichSchema
        },
    )

    suspend fun loadScaledBitmap(context: Context, uri: Uri, maxDimPx: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxOrig = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxOrig / sampleSize > maxDimPx) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }
}
