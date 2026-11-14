package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AwarenessEngine
import com.example.i18n.AppStrings
import com.example.model.Lang
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMonoFamily
import com.example.ui.theme.CyberNeonEmerald
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.YellowMedium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val shortDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
private val dateTimeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

fun formatDate(epochMs: Long?): String = epochMs?.let { shortDateFmt.format(Date(it)) } ?: "—"
fun formatDateTime(epochMs: Long?): String = epochMs?.let { dateTimeFmt.format(Date(it)) } ?: "—"

@Composable
fun CyberBackdrop(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val bgColor = MaterialTheme.colorScheme.background
    val gridColor = if (isDark) CyberCyan.copy(alpha = 0.055f) else TealSecondary.copy(alpha = 0.05f)
    val emeraldGlow = if (isDark) CyberNeonEmerald.copy(alpha = 0.14f) else EmeraldPrimary.copy(alpha = 0.08f)
    val cyanGlow = if (isDark) CyberCyan.copy(alpha = 0.10f) else TealSecondary.copy(alpha = 0.06f)
    val reticleColor = if (isDark) CyberNeonEmerald.copy(alpha = 0.09f) else EmeraldPrimary.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                // Soft top-right SOC radar glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(emeraldGlow, Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.08f),
                        radius = size.maxDimension * 0.55f,
                    ),
                    radius = size.maxDimension * 0.55f,
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                )
                // Soft bottom-left cyan network glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cyanGlow, Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.86f),
                        radius = size.maxDimension * 0.50f,
                    ),
                    radius = size.maxDimension * 0.50f,
                    center = Offset(size.width * 0.08f, size.height * 0.86f),
                )
                // Precision tactical coordinate grid
                val step = 36.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += step
                }
                // Subtle corner radar range rings
                val radarCenter = Offset(size.width * 0.94f, size.height * 0.07f)
                listOf(90.dp.toPx(), 165.dp.toPx(), 240.dp.toPx()).forEach { r ->
                    drawCircle(
                        color = reticleColor,
                        radius = r,
                        center = radarCenter,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
        content = content,
    )
}

@Composable
fun ScoreRing(
    score: Double,
    size: Dp = 72.dp,
    strokeWidth: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    val clamped = score.toFloat().coerceIn(0f, 100f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = when {
        clamped >= 75f -> MaterialTheme.colorScheme.primary
        clamped >= 50f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Score ${clamped.roundToInt()} percent" },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            // Subtle cyber halo glow behind progress arc
            drawArc(
                color = activeColor.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = (clamped / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = (strokeWidth + 4.dp).toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = (clamped / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${clamped.roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = CyberMonoFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SeverityBadge(
    severity: String,
    lang: Lang,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (severity.lowercase()) {
        "critical" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "high" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "medium" -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    StatusPill(
        label = AppStrings.severityLabel(lang, severity).uppercase(),
        containerColor = bg,
        contentColor = fg,
        modifier = modifier,
    )
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(accentColor.copy(alpha = 0.85f), CyberCyan.copy(alpha = 0.45f), Color.Transparent),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = CyberMonoFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                        )
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun FindingsDonutChart(
    critical: Int,
    high: Int,
    medium: Int,
    modifier: Modifier = Modifier,
) {
    val total = (critical + high + medium).coerceAtLeast(1)
    val segments = listOf(
        Triple("Critical", critical, CrimsonError),
        Triple("High", high, AmberAccent),
        Triple("Medium", medium, YellowMedium),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                var start = -90f
                val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt)
                if (critical + high + medium == 0) {
                    drawArc(EmeraldPrimary, 0f, 360f, false, style = stroke)
                } else {
                    for ((_, count, color) in segments) {
                        if (count <= 0) continue
                        val sweep = (count.toFloat() / total) * 360f
                        drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = false, style = stroke)
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${critical + high + medium}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            for ((label, count, color) in segments) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PillarRadarChart(
    scores: Map<String, Double>,
    lang: Lang,
    modifier: Modifier = Modifier,
) {
    if (scores.isEmpty()) return
    val entries = scores.entries.toList()
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.size(190.dp).padding(12.dp)) {
            val n = entries.size.coerceAtLeast(3)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = minOf(cx, cy) * 0.9f

            // Concentric polygons
            for (level in 1..4) {
                val r = maxR * (level / 4f)
                val poly = Path()
                for (i in 0 until n) {
                    val angle = (-PI / 2.0) + (2.0 * PI * i / n)
                    val px = cx + (r * cos(angle)).toFloat()
                    val py = cy + (r * sin(angle)).toFloat()
                    if (i == 0) poly.moveTo(px, py) else poly.lineTo(px, py)
                }
                poly.close()
                drawPath(poly, color = gridColor, style = Stroke(width = 1.dp.toPx()))
            }

            // Spokes
            for (i in 0 until n) {
                val angle = (-PI / 2.0) + (2.0 * PI * i / n)
                val px = cx + (maxR * cos(angle)).toFloat()
                val py = cy + (maxR * sin(angle)).toFloat()
                drawLine(gridColor, Offset(cx, cy), Offset(px, py), strokeWidth = 1.dp.toPx())
            }

            // Data polygon
            val dataPath = Path()
            entries.forEachIndexed { idx, (_, v) ->
                val ratio = (v.toFloat() / 100f).coerceIn(0f, 1f)
                val angle = (-PI / 2.0) + (2.0 * PI * idx / n)
                val px = cx + (maxR * ratio * cos(angle)).toFloat()
                val py = cy + (maxR * ratio * sin(angle)).toFloat()
                if (idx == 0) dataPath.moveTo(px, py) else dataPath.lineTo(px, py)
            }
            dataPath.close()
            drawPath(dataPath, color = primary.copy(alpha = 0.25f))
            drawPath(dataPath, color = primary, style = Stroke(width = 2.5.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            entries.forEach { (k, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.pillarLabel(lang, k), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    LinearProgressIndicator(
                        progress = { (v.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${v.roundToInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
fun HorizontalBarList(
    items: List<Pair<String, Int>>,
    maxVal: Int? = null,
    barColor: Color = MaterialTheme.colorScheme.error,
    suffix: String = "",
) {
    val peak = (maxVal ?: items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for ((label, count) in items) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((count.toFloat() / peak).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("$count$suffix", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun GainHistogramChart(
    treatmentBuckets: List<AwarenessEngine.HistBucket>,
    controlBuckets: List<AwarenessEngine.HistBucket>,
    modifier: Modifier = Modifier,
) {
    val allLabels = (treatmentBuckets.map { it.label } + controlBuckets.map { it.label }).distinct()
    val tMap = treatmentBuckets.associate { it.label to it.count }
    val cMap = controlBuckets.associate { it.label to it.count }
    val maxCount = maxOf(
        1,
        treatmentBuckets.maxOfOrNull { it.count } ?: 1,
        controlBuckets.maxOfOrNull { it.count } ?: 1,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(EmeraldPrimary, CircleShape))
                Text("Treatment", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(TealSecondary.copy(alpha = 0.5f), CircleShape))
                Text("Control", style = MaterialTheme.typography.labelSmall)
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            if (allLabels.isEmpty()) return@Canvas
            val groupW = size.width / allLabels.size
            val barW = (groupW * 0.34f).coerceAtLeast(4f)
            allLabels.forEachIndexed { idx, label ->
                val tc = tMap[label] ?: 0
                val cc = cMap[label] ?: 0
                val th = (tc.toFloat() / maxCount) * (size.height - 8.dp.toPx())
                val ch = (cc.toFloat() / maxCount) * (size.height - 8.dp.toPx())
                val xBase = idx * groupW + groupW * 0.14f
                drawRoundRect(
                    color = EmeraldPrimary,
                    topLeft = Offset(xBase, size.height - th),
                    size = Size(barW, th),
                    cornerRadius = CornerRadius(4f, 4f),
                )
                drawRoundRect(
                    color = TealSecondary.copy(alpha = 0.55f),
                    topLeft = Offset(xBase + barW + 2.dp.toPx(), size.height - ch),
                    size = Size(barW, ch),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            allLabels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
fun CaptchaBox(
    code: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(48.dp)
                .width(126.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(EmeraldPrimary.copy(alpha = 0.25f), Offset(0f, size.height * 0.3f), Offset(size.width, size.height * 0.7f), 2f)
                drawLine(AmberAccent.copy(alpha = 0.25f), Offset(0f, size.height * 0.75f), Offset(size.width, size.height * 0.25f), 2f)
            }
            Text(
                text = code.toCharArray().joinToString(" "),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.heightIn(min = 48.dp).testTag("captcha_refresh_button"),
        ) {
            Text("Refresh")
        }
    }
}
