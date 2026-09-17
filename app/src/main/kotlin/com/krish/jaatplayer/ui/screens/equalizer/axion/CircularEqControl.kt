package com.krish.jaatplayer.ui.screens.equalizer.axion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CircularEqControl(
    bass: Float, mid: Float, treble: Float,
    enabled: Boolean,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    val labelStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    val valueStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = primary
    )

    var activeAxis by remember { mutableIntStateOf(-1) }
    val currentBassChange by rememberUpdatedState(onBassChange)
    val currentMidChange by rememberUpdatedState(onMidChange)
    val currentTrebleChange by rememberUpdatedState(onTrebleChange)

    // Base Sector Angles: Mids (-90° / Top), Bass (+30° / Bottom-Right), Treble (+150° / Bottom-Left)
    val baseAngles = remember { doubleArrayOf(-PI / 2, -PI / 2 + 2 * PI / 3, -PI / 2 + 4 * PI / 3) }
    val maxArcSweepRad = (38.0 * PI / 180.0) // Max angular travel along circumference (38°)

    fun calcValueForTouch(pos: Offset, w: Float, h: Float, axisIdx: Int): Float {
        val cx = w / 2f
        val cy = h / 2f
        val touchAngle = atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())
        val baseAngle = baseAngles[axisIdx]
        val diff = atan2(sin(touchAngle - baseAngle), cos(touchAngle - baseAngle))
        return ((diff / maxArcSweepRad) * 10.0).toFloat().coerceIn(-10f, 10f)
    }

    fun findNearestSector(pos: Offset, w: Float, h: Float): Int {
        val cx = w / 2f
        val cy = h / 2f
        val touchAngle = atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())
        var bestIdx = 0
        var bestDiff = Double.MAX_VALUE
        for (i in 0..2) {
            val diff = atan2(sin(touchAngle - baseAngles[i]), cos(touchAngle - baseAngles[i]))
            val absDiff = abs(diff)
            if (absDiff < bestDiff) { bestDiff = absDiff; bestIdx = i }
        }
        val dist = hypot((pos.x - cx).toDouble(), (pos.y - cy).toDouble())
        return if (dist > w * 0.20f && dist < w * 0.52f) bestIdx else -1
    }

    fun dispatchToAxis(axisIdx: Int, value: Float) {
        when (axisIdx) {
            0 -> currentMidChange(value)
            1 -> currentBassChange(value)
            2 -> currentTrebleChange(value)
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val axis = findNearestSector(down.position, size.width.toFloat(), size.height.toFloat())
                    if (axis < 0) return@awaitEachGesture
                    down.consume()

                    activeAxis = axis
                    val v = calcValueForTouch(down.position, size.width.toFloat(), size.height.toFloat(), axis)
                    dispatchToAxis(axis, v)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()

                        val dragV = calcValueForTouch(change.position, size.width.toFloat(), size.height.toFloat(), axis)
                        dispatchToAxis(axis, dragV)
                    }
                    activeAxis = -1
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // PERMANENT FIXED CIRCLE RADIUS - Dots stay 100% on this circumference!
        val baseR = size.width / 2f * 0.48f

        val values = floatArrayOf(mid, bass, treble)
        val labels = arrayOf("Mids", "Bass", "Treble")

        // 1. Concentric Background Guide Rings (Fixed sizes)
        drawCircle(
            color = outline.copy(alpha = 0.08f),
            radius = baseR * 0.48f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )
        drawCircle(
            color = outline.copy(alpha = 0.18f),
            radius = baseR,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2f)
        )
        drawCircle(
            color = outline.copy(alpha = 0.08f),
            radius = baseR * 1.32f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )

        // 2. Center Icon Badge (Equalizer Bars in Center Circular Disc)
        val centerBadgeR = baseR * 0.30f
        drawCircle(
            color = primary.copy(alpha = 0.12f),
            radius = centerBadgeR,
            center = Offset(cx, cy)
        )

        val barWidth = 2.5f.dp.toPx()
        val barGap = 3.5f.dp.toPx()
        val barHeights = floatArrayOf(10f.dp.toPx(), 16f.dp.toPx(), 18f.dp.toPx(), 11f.dp.toPx())
        val totalBarsWidth = barWidth * 4 + barGap * 3
        val startX = cx - totalBarsWidth / 2f

        for (i in 0..3) {
            val bx = startX + i * (barWidth + barGap)
            val bh = barHeights[i]
            val by = cy - bh / 2f
            drawRoundRect(
                color = primary,
                topLeft = Offset(bx, by),
                size = Size(barWidth, bh),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // 3. Curved Dark Arcs on the Circumference for Each Sector
        val arcStrokeWidth = 7f.dp.toPx()
        val arcRect = Size(baseR * 2f, baseR * 2f)
        val arcTopLeft = Offset(cx - baseR, cy - baseR)

        for (i in 0..2) {
            val baseAngleDeg = Math.toDegrees(baseAngles[i]).toFloat()
            val maxSweepDeg = 38f
            val normVal = (values[i] / 10f).coerceIn(-1f, 1f)
            val activeSweepDeg = normVal * maxSweepDeg

            // Background Sector Track on the Circumference
            drawArc(
                color = outline.copy(alpha = 0.20f),
                startAngle = baseAngleDeg - maxSweepDeg,
                sweepAngle = maxSweepDeg * 2f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
            )

            // Active Colored Arc along Circumference
            if (abs(activeSweepDeg) > 0.5f) {
                val start = if (activeSweepDeg >= 0) baseAngleDeg else baseAngleDeg + activeSweepDeg
                val sweep = abs(activeSweepDeg)
                drawArc(
                    color = primary,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRect,
                    style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 4. Calculate Dot Positions STRICTLY ON THE CIRCUMFERENCE
        val dotAngles = FloatArray(3) { i ->
            val normVal = (values[i] / 10f).coerceIn(-1f, 1f)
            (baseAngles[i] + normVal * maxArcSweepRad).toFloat()
        }

        val points = Array(3) { i ->
            Offset(cx + baseR * cos(dotAngles[i]), cy + baseR * sin(dotAngles[i]))
        }

        // 5. Knob Handles (Dots sliding along the Circumference)
        val knobR = 14f.dp.toPx()
        for (i in 0..2) {
            val isActive = (activeAxis == i)
            val point = points[i]

            // Shadow / Elevation
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = if (isActive) knobR * 1.30f else knobR * 1.10f,
                center = point + Offset(0f, 2f.dp.toPx())
            )

            // Outer Light Ring
            drawCircle(
                color = Color.White,
                radius = if (isActive) knobR * 1.15f else knobR,
                center = point
            )

            // Primary Dark Inner Core
            drawCircle(
                color = primary,
                radius = if (isActive) knobR * 0.78f else knobR * 0.68f,
                center = point
            )
        }

        // 6. Labels & Formatted dB Values (+2 dB, +3 dB, +1 dB)
        for (i in 0..2) {
            val vInt = values[i].roundToInt()
            val sign = if (vInt > 0) "+" else ""
            val valText = "${sign}${vInt} dB"

            val labelLayout = textMeasurer.measure(labels[i], labelStyle)
            val valLayout = textMeasurer.measure(valText, valueStyle)

            val point = points[i]

            val lx: Float
            val ly: Float

            when (i) {
                0 -> { // Top (Mids)
                    lx = cx - labelLayout.size.width / 2f
                    ly = point.y - labelLayout.size.height - valLayout.size.height - 12f.dp.toPx()
                }
                1 -> { // Bottom Right (Bass)
                    lx = point.x + 18f.dp.toPx()
                    ly = point.y - labelLayout.size.height / 2f
                }
                else -> { // Bottom Left (Treble)
                    lx = point.x - labelLayout.size.width - 18f.dp.toPx()
                    ly = point.y - labelLayout.size.height / 2f
                }
            }

            val vx = when (i) {
                0 -> cx - valLayout.size.width / 2f
                1 -> lx
                else -> lx + labelLayout.size.width - valLayout.size.width
            }
            val vy = ly + labelLayout.size.height + 2f.dp.toPx()

            drawText(labelLayout, topLeft = Offset(lx, ly))
            drawText(valLayout, topLeft = Offset(vx, vy))
        }
    }
}
