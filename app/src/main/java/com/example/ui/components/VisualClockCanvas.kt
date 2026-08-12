package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutineTask
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class TaskArcSegment(
    val task: RoutineTask,
    val isPm: Boolean,
    val startMinuteInHalfDay: Int,
    val durationMinutesInHalfDay: Int,
    val layer: Int = 0
)

fun getTaskSegments(task: RoutineTask, startMinute: Int, is12Hour: Boolean): List<TaskArcSegment> {
    if (!is12Hour) {
        return listOf(
            TaskArcSegment(
                task = task,
                isPm = false,
                startMinuteInHalfDay = (startMinute % 1440 + 1440) % 1440,
                durationMinutesInHalfDay = task.durationMinutes
            )
        )
    }

    val segments = mutableListOf<TaskArcSegment>()
    var currStart = (startMinute % 1440 + 1440) % 1440
    var remaining = task.durationMinutes

    while (remaining > 0) {
        val halfDayIndex = (currStart / 720) % 2 // 0 = AM, 1 = PM
        val isPm = (halfDayIndex == 1)
        val startInHalf = currStart % 720
        val maxInThisHalf = 720 - startInHalf
        val durInThisHalf = minOf(remaining, maxInThisHalf)

        segments.add(
            TaskArcSegment(
                task = task,
                isPm = isPm,
                startMinuteInHalfDay = startInHalf,
                durationMinutesInHalfDay = durInThisHalf
            )
        )

        currStart = (currStart + durInThisHalf) % 1440
        remaining -= durInThisHalf
    }
    return segments
}

fun getAllTaskSegmentsWithLayers(
    tasks: List<RoutineTask>,
    is12Hour: Boolean,
    draggedTaskId: Int?,
    draggedStartMinute: Int?
): List<TaskArcSegment> {
    val enabledTasks = tasks.filter { it.isEnabled }
    val rawSegments = enabledTasks.flatMap { task ->
        val start = if (task.id == draggedTaskId && draggedStartMinute != null) {
            draggedStartMinute
        } else {
            task.startMinute
        }
        getTaskSegments(task, start, is12Hour)
    }

    return if (!is12Hour) {
        assignLayersToSegments(rawSegments, maxMinutes = 1440)
    } else {
        val amSegments = rawSegments.filter { !it.isPm }
        val pmSegments = rawSegments.filter { it.isPm }

        val layeredAm = assignLayersToSegments(amSegments, maxMinutes = 720)
        val layeredPm = assignLayersToSegments(pmSegments, maxMinutes = 720)

        layeredAm + layeredPm
    }
}

private fun assignLayersToSegments(
    segments: List<TaskArcSegment>,
    maxMinutes: Int
): List<TaskArcSegment> {
    val sorted = segments.sortedWith(
        compareBy({ it.startMinuteInHalfDay }, { it.durationMinutesInHalfDay })
    )

    val result = mutableListOf<TaskArcSegment>()
    for (seg in sorted) {
        var layer = 0
        while (true) {
            val hasOverlapInLayer = result.any { existing ->
                existing.layer == layer && segmentsOverlap(seg, existing, maxMinutes)
            }
            if (!hasOverlapInLayer) {
                break
            }
            layer++
        }
        result.add(seg.copy(layer = layer))
    }
    return result
}

private fun segmentsOverlap(a: TaskArcSegment, b: TaskArcSegment, maxMinutes: Int): Boolean {
    val rangesA = getRanges(a.startMinuteInHalfDay, a.durationMinutesInHalfDay, maxMinutes)
    val rangesB = getRanges(b.startMinuteInHalfDay, b.durationMinutesInHalfDay, maxMinutes)

    for ((r1Start, r1End) in rangesA) {
        for ((r2Start, r2End) in rangesB) {
            if (maxOf(r1Start, r2Start) < minOf(r1End, r2End)) {
                return true
            }
        }
    }
    return false
}

private fun getRanges(start: Int, duration: Int, maxMinutes: Int): List<Pair<Int, Int>> {
    val normStart = (start % maxMinutes + maxMinutes) % maxMinutes
    val normEnd = normStart + duration
    return if (normEnd <= maxMinutes) {
        listOf(Pair(normStart, normEnd))
    } else {
        listOf(
            Pair(normStart, maxMinutes),
            Pair(0, normEnd - maxMinutes)
        )
    }
}

fun calculateSegmentRadius(
    segment: TaskArcSegment,
    is12HourDial: Boolean,
    arcRadius: Float,
    innerArcRadius: Float,
    outerArcRadius: Float,
    layerStepPx: Float
): Float {
    val L = segment.layer
    return if (!is12HourDial) {
        // 24H mode: alternating outward (+1, +2...) and inward (-1, -2...) around base track
        val offsetMultiplier = if (L == 0) 0f else if (L % 2 == 1) ((L + 1) / 2f) else -(L / 2f)
        arcRadius + (offsetMultiplier * layerStepPx)
    } else {
        if (!segment.isPm) {
            // 12H AM mode: alternating inward (-1, -2...) towards center and outward (+1, +2...) towards gap
            val offsetMultiplier = if (L == 0) 0f else if (L % 2 == 1) -((L + 1) / 2f) else (L / 2f)
            innerArcRadius + (offsetMultiplier * layerStepPx)
        } else {
            // 12H PM mode: alternating outward (+1, +2...) away from dial and inward (-1, -2...) towards gap
            val offsetMultiplier = if (L == 0) 0f else if (L % 2 == 1) ((L + 1) / 2f) else -(L / 2f)
            outerArcRadius + (offsetMultiplier * layerStepPx)
        }
    }
}

@Composable
fun VisualClockCanvas(
    tasks: List<RoutineTask>,
    currentTime: LocalTime,
    is12HourDial: Boolean,
    draggedTaskId: Int?,
    draggedStartMinute: Int?,
    onTaskTap: (RoutineTask) -> Unit,
    onTaskDragStart: (RoutineTask) -> Unit,
    onTaskDragUpdate: (RoutineTask, Int) -> Unit,
    onTaskDragEnd: (RoutineTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Pulsing aura scale animation for current active task
    val pulseAnim = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        pulseAnim.animateTo(
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    val currentMinuteOfDay = currentTime.hour * 60 + currentTime.minute

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val sizePx = constraints.maxWidth.toFloat()
        val center = Offset(sizePx / 2f, sizePx / 2f)

        // Radii configuration - enlarged outer ring to fit thicker task arcs
        val clockRadius = if (is12HourDial) sizePx * 0.25f else sizePx * 0.33f
        val arcRadius = sizePx * 0.41f // Used in 24h mode
        val innerArcRadius = sizePx * 0.32f // AM ring in AM/PM mode
        val outerArcRadius = sizePx * 0.44f // PM ring in AM/PM mode
        // Remember segments calculations so they aren't computed multiple times per frame
        val allSegments = remember(tasks, is12HourDial, draggedTaskId, draggedStartMinute) {
            getAllTaskSegmentsWithLayers(tasks, is12HourDial, draggedTaskId, draggedStartMinute)
        }

        val segmentScaleMap = remember(allSegments, is12HourDial) {
            val totalMinutes = if (is12HourDial) 720 else 1440
            allSegments.associateWith { segment ->
                val localMaxLayer = allSegments.filter { 
                    (!is12HourDial || it.isPm == segment.isPm) && segmentsOverlap(segment, it, totalMinutes) 
                }.maxOfOrNull { it.layer } ?: 0
                if (localMaxLayer > 0) 1f / (localMaxLayer * 0.35f + 1f) else 1f
            }
        }

        // Theme colors
        val dialBg = MaterialTheme.colorScheme.surfaceVariant
        val textColor = MaterialTheme.colorScheme.onSurface
        val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        val accentRed = Color(0xFFFB7185)

        // Pre-calculate DP dimensions for touch checks
        val px34 = with(density) { 34.dp.toPx() }
        val px28 = with(density) { 28.dp.toPx() }

        // Cache parsed task colors
        val taskColorMap = remember(tasks) {
            tasks.associate { it.id to parseHexColor(it.colorHex) }
        }

        // Remember Android Paint objects to avoid object allocation in draw loop
        val indicatorPaint = remember(textColor) {
            android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.45f).toArgb()
                textSize = with(density) { 10.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        val textPaint = remember(textColor, is12HourDial) {
            android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = with(density) { if (is12HourDial) 18.sp.toPx() else 13.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        
        val minuteTextPaint = remember(textColor, is12HourDial) {
            android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.6f).toArgb()
                textSize = with(density) { if (is12HourDial) 12.sp.toPx() else 11.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
        }

        val badgePaint = remember(is12HourDial) {
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(245, 15, 15, 15)
                textSize = with(density) { if (is12HourDial) 12.sp.toPx() else 12.5.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }

        val taskFillPaint = remember {
            android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
            }
        }

        // Detect touch angle helper
        fun getAngleFromOffset(offset: Offset): Float {
            val dx = offset.x - center.x
            val dy = offset.y - center.y
            var radians = atan2(dy, dx)
            // Convert to degrees where top (12 o'clock) is 0°
            var degrees = (Math.toDegrees(radians.toDouble()).toFloat() + 90f) % 360f
            if (degrees < 0) degrees += 360f
            return degrees
        }

        // Touch target matching with exact sub-ring distance
        fun findTaskAtOffset(offset: Offset): RoutineTask? {
            val dist = kotlin.math.hypot(offset.x - center.x, offset.y - center.y)
            val touchAngle = getAngleFromOffset(offset)

            val totalMinutes = if (is12HourDial) 720 else 1440

            val matchingSegment = allSegments
                .filter { segment ->
                    val scaleFactor = segmentScaleMap[segment] ?: 1f
                    val layerStepPx = (if (!is12HourDial) px34 else px28) * scaleFactor
                    val segRadius = calculateSegmentRadius(
                        segment, is12HourDial, arcRadius, innerArcRadius, outerArcRadius, layerStepPx
                    )
                    val strokeWidth = (if (!is12HourDial) px34 else px28) * scaleFactor
                    val halfWidth = (strokeWidth / 2f) + with(density) { 8.dp.toPx() }

                    if (dist in (segRadius - halfWidth)..(segRadius + halfWidth)) {
                        val startAngle = ((segment.startMinuteInHalfDay / totalMinutes.toFloat()) * 360f)
                        val sweepAngle = ((segment.durationMinutesInHalfDay / totalMinutes.toFloat()) * 360f)
                        val endAngle = startAngle + sweepAngle

                        if (endAngle <= 360f) {
                            touchAngle in startAngle..endAngle
                        } else {
                            touchAngle >= startAngle || touchAngle <= (endAngle % 360f)
                        }
                    } else false
                }
                .minByOrNull { segment ->
                    val scaleFactor = segmentScaleMap[segment] ?: 1f
                    val layerStepPx = (if (!is12HourDial) px34 else px28) * scaleFactor
                    val segRadius = calculateSegmentRadius(
                        segment, is12HourDial, arcRadius, innerArcRadius, outerArcRadius, layerStepPx
                    )
                    kotlin.math.abs(dist - segRadius)
                }

            return matchingSegment?.task
        }

        var touchAngleOffset by remember { mutableFloatStateOf(0f) }
        val currentDraggingTask = remember { mutableStateOf<RoutineTask?>(null) }

        // Clock Canvas Background, Dial, Arcs, Hands & Badges
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tasks, is12HourDial) {
                    detectTapGestures { offset ->
                        val clicked = findTaskAtOffset(offset)
                        if (clicked != null) {
                            onTaskTap(clicked)
                        }
                    }
                }
                .pointerInput(tasks, is12HourDial) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val task = findTaskAtOffset(offset)
                            if (task != null) {
                                currentDraggingTask.value = task
                                val touchAngle = getAngleFromOffset(offset)
                                val totalMinutes = if (is12HourDial) 720 else 1440
                                val startMin = if (task.id == draggedTaskId && draggedStartMinute != null) draggedStartMinute else task.startMinute
                                val taskStartAngle = ((startMin % totalMinutes) / totalMinutes.toFloat()) * 360f

                                var angleDiff = (touchAngle - taskStartAngle) % 360f
                                if (angleDiff < 0) angleDiff += 360f
                                touchAngleOffset = angleDiff

                                onTaskDragStart(task)
                            }
                        },
                        onDrag = { change, _ ->
                            val task = currentDraggingTask.value
                            if (task != null) {
                                change.consume()
                                val pos = change.position
                                val touchAngle = getAngleFromOffset(pos)
                                val dist = kotlin.math.hypot(pos.x - center.x, pos.y - center.y)

                                var targetAngle = (touchAngle - touchAngleOffset) % 360f
                                if (targetAngle < 0) targetAngle += 360f

                                val targetMin: Int
                                if (!is12HourDial) {
                                    targetMin = ((targetAngle / 360f) * 1440).roundToInt()
                                } else {
                                    val midThreshold = (innerArcRadius + outerArcRadius) / 2f
                                    val isPm = dist > midThreshold
                                    val baseMin = if (isPm) 720 else 0
                                    targetMin = baseMin + ((targetAngle / 360f) * 720).roundToInt()
                                }

                                onTaskDragUpdate(task, targetMin)
                            }
                        },
                        onDragEnd = {
                            val task = currentDraggingTask.value
                            if (task != null) {
                                onTaskDragEnd(task)
                                currentDraggingTask.value = null
                            }
                        },
                        onDragCancel = {
                            val task = currentDraggingTask.value
                            if (task != null) {
                                onTaskDragEnd(task)
                                currentDraggingTask.value = null
                            }
                        }
                    )
                }
        ) {
            // 1. Draw Outer Dial Background Track Circles
            if (is12HourDial) {
                // AM Ring track (Inner)
                drawCircle(
                    color = textColor.copy(alpha = 0.08f),
                    radius = innerArcRadius,
                    center = center,
                    style = Stroke(width = 30.dp.toPx())
                )
                // PM Ring track (Outer)
                drawCircle(
                    color = textColor.copy(alpha = 0.08f),
                    radius = outerArcRadius,
                    center = center,
                    style = Stroke(width = 30.dp.toPx())
                )

                // Ticks on AM & PM ring tracks matching the clock face style
                val ringHalfWidth = 15.dp.toPx()

                for (ringRadius in listOf(innerArcRadius, outerArcRadius)) {
                    // Minor Ticks (60 subdivisions per ring)
                    for (m in 0 until 60) {
                        if (m % 5 != 0) {
                            val angleDeg = (m * 6f) - 90f
                            val rad = Math.toRadians(angleDeg.toDouble())
                            val cosVal = cos(rad).toFloat()
                            val sinVal = sin(rad).toFloat()

                            val outerPos = ringRadius + ringHalfWidth - 2.5.dp.toPx()
                            val innerPos = ringRadius + ringHalfWidth - 5.5.dp.toPx()

                            drawLine(
                                color = tickColor.copy(alpha = 0.35f),
                                start = Offset(center.x + innerPos * cosVal, center.y + innerPos * sinVal),
                                end = Offset(center.x + outerPos * cosVal, center.y + outerPos * sinVal),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Major Hour Ticks (12 hour marks per ring)
                    for (h in 0 until 12) {
                        val angleDeg = (h * 30f) - 90f
                        val rad = Math.toRadians(angleDeg.toDouble())
                        val cosVal = cos(rad).toFloat()
                        val sinVal = sin(rad).toFloat()

                        val outerPos = ringRadius + ringHalfWidth - 6.dp.toPx()
                        val innerPos = ringRadius - ringHalfWidth + 6.dp.toPx()

                        drawLine(
                            color = tickColor,
                            start = Offset(center.x + innerPos * cosVal, center.y + innerPos * sinVal),
                            end = Offset(center.x + outerPos * cosVal, center.y + outerPos * sinVal),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Draw subtle AM / PM indicators at top
                val fontMetrics = indicatorPaint.fontMetrics
                val textYOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f
                val labelRadiusOffset = 18.dp.toPx()

                drawContext.canvas.nativeCanvas.drawText("AM", center.x, center.y - (innerArcRadius + labelRadiusOffset) + textYOffset, indicatorPaint)
                drawContext.canvas.nativeCanvas.drawText("PM", center.x, center.y - (outerArcRadius + labelRadiusOffset) + textYOffset, indicatorPaint)
            } else {
                drawCircle(
                    color = dialBg.copy(alpha = 0.2f),
                    radius = arcRadius + 18.dp.toPx(),
                    center = center
                )

                // Ticks on 24h ring track matching the clock face style
                val ringHalfWidth = 14.dp.toPx()

                // Minor Ticks (96 subdivisions for 24h ring = every 15 mins)
                for (i in 0 until 96) {
                    if (i % 4 != 0) {
                        val angleDeg = (i * 3.75f) - 90f
                        val rad = Math.toRadians(angleDeg.toDouble())
                        val cosVal = cos(rad).toFloat()
                        val sinVal = sin(rad).toFloat()

                        val outerPos = arcRadius + ringHalfWidth - 2.5.dp.toPx()
                        val innerPos = arcRadius + ringHalfWidth - 5.5.dp.toPx()

                        drawLine(
                            color = tickColor.copy(alpha = 0.35f),
                            start = Offset(center.x + innerPos * cosVal, center.y + innerPos * sinVal),
                            end = Offset(center.x + outerPos * cosVal, center.y + outerPos * sinVal),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Major Hour Ticks (24 hour marks)
                for (h in 0 until 24) {
                    val angleDeg = (h * 15f) - 90f
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val cosVal = cos(rad).toFloat()
                    val sinVal = sin(rad).toFloat()

                    val outerPos = arcRadius + ringHalfWidth - 7.25.dp.toPx()
                    val innerPos = arcRadius - ringHalfWidth + 7.25.dp.toPx()

                    drawLine(
                        color = tickColor,
                        start = Offset(center.x + innerPos * cosVal, center.y + innerPos * sinVal),
                        end = Offset(center.x + outerPos * cosVal, center.y + outerPos * sinVal),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. Draw Task Arcs along clock rim as annular sectors with rounded corners
            val baseCornerRadiusPx = with(density) { 6.dp.toPx() }

            allSegments.forEach { segment ->
                val task = segment.task
                val totalMinutesForRing = if (is12HourDial) 720 else 1440
                val startAngle = ((segment.startMinuteInHalfDay / totalMinutesForRing.toFloat()) * 360f) - 90f
                val sweepAngle = maxOf(0.2f, (segment.durationMinutesInHalfDay / totalMinutesForRing.toFloat()) * 360f)
                val taskColor = taskColorMap[task.id] ?: parseHexColor(task.colorHex)
                val isActive = task.isCurrent(currentMinuteOfDay)

                val scaleFactor = segmentScaleMap[segment] ?: 1f
                val layerStepPx = (if (!is12HourDial) px34 else px28) * scaleFactor

                val ringRadius = calculateSegmentRadius(
                    segment, is12HourDial, arcRadius, innerArcRadius, outerArcRadius, layerStepPx
                )

                val bandWidth = (if (!is12HourDial) {
                    if (isActive) 38.dp.toPx() else 32.dp.toPx()
                } else {
                    if (isActive) 30.dp.toPx() else 26.dp.toPx()
                }) * scaleFactor

                val innerR = maxOf(10f, ringRadius - (bandWidth / 2f))
                val outerR = ringRadius + (bandWidth / 2f)

                // Active glow background
                if (isActive) {
                    val glowOffset = (pulseAnim.value - 1f) * 30.dp.toPx() + 4.dp.toPx()
                    val glowInnerR = maxOf(5f, innerR - glowOffset)
                    val glowOuterR = outerR + glowOffset

                    val glowPath = createRoundedAnnularSectorPath(
                        center = center,
                        innerRadius = glowInnerR,
                        outerRadius = glowOuterR,
                        startAngleDeg = startAngle,
                        sweepAngleDeg = sweepAngle,
                        cornerRadiusPx = baseCornerRadiusPx + glowOffset
                    )

                    drawPath(
                        path = glowPath,
                        color = taskColor.copy(alpha = 0.35f)
                    )
                }

                // Main Task Block as rounded annular sector
                val sectorPath = createRoundedAnnularSectorPath(
                    center = center,
                    innerRadius = innerR,
                    outerRadius = outerR,
                    startAngleDeg = startAngle,
                    sweepAngleDeg = sweepAngle,
                    cornerRadiusPx = baseCornerRadiusPx
                )

                drawPath(
                    path = sectorPath,
                    color = taskColor
                )
            }

            // 3. Main Clock Dial Plate
            drawCircle(
                color = dialBg,
                radius = clockRadius,
                center = center
            )
            drawCircle(
                color = textColor.copy(alpha = 0.15f),
                radius = clockRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Clock Hour Numerals and Tick Marks
            val numHours = if (is12HourDial) 12 else 24

            for (i in 1..numHours) {
                val hourAngle = (i * (360f / numHours)) - 90f
                val rad = Math.toRadians(hourAngle.toDouble())

                // Major Ticks & Numbers
                if (is12HourDial) {
                    val tickStartRadius = clockRadius - 6.dp.toPx()
                    val tickEndRadius = clockRadius - 12.dp.toPx()
                    val numRadius = clockRadius - 28.dp.toPx()

                    val tickStart = Offset(
                        (center.x + tickStartRadius * cos(rad)).toFloat(),
                        (center.y + tickStartRadius * sin(rad)).toFloat()
                    )
                    val tickEnd = Offset(
                        (center.x + tickEndRadius * cos(rad)).toFloat(),
                        (center.y + tickEndRadius * sin(rad)).toFloat()
                    )

                    drawLine(
                        color = tickColor,
                        start = tickStart,
                        end = tickEnd,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Hour Number
                    val numX = (center.x + numRadius * cos(rad)).toFloat()
                    val numY = (center.y + numRadius * sin(rad) + textPaint.textSize / 3f).toFloat()
                    drawContext.canvas.nativeCanvas.drawText(i.toString(), numX, numY, textPaint)
                } else {
                    // 24 Hour Dial Layout
                    val tickOuterRadius = clockRadius - 28.dp.toPx()
                    val tickInnerRadius = clockRadius - 34.dp.toPx()
                    val numRadius = clockRadius - 16.dp.toPx()
                    
                    val tickStart = Offset(
                        (center.x + tickOuterRadius * cos(rad)).toFloat(),
                        (center.y + tickOuterRadius * sin(rad)).toFloat()
                    )
                    val tickEnd = Offset(
                        (center.x + tickInnerRadius * cos(rad)).toFloat(),
                        (center.y + tickInnerRadius * sin(rad)).toFloat()
                    )

                    drawLine(
                        color = tickColor,
                        start = tickStart,
                        end = tickEnd,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Hour Number (00 for 24)
                    val numX = (center.x + numRadius * cos(rad)).toFloat()
                    val numY = (center.y + numRadius * sin(rad) + textPaint.textSize / 3f).toFloat()
                    val hourStr = if (i == 24) "00" else i.toString()
                    drawContext.canvas.nativeCanvas.drawText(hourStr, numX, numY, textPaint)
                }
            }

            // Minor Ticks for minutes
            for (m in 0 until 60) {
                if (m % 5 != 0) {
                    val minorTickStart = if (is12HourDial) clockRadius - 3.dp.toPx() else clockRadius - 30.dp.toPx()
                    val minorTickEnd = if (is12HourDial) clockRadius - 6.dp.toPx() else clockRadius - 34.dp.toPx()
                    
                    val mAngle = (m * 6f) - 90f
                    val rad = Math.toRadians(mAngle.toDouble())
                    drawLine(
                        color = if (is12HourDial) tickColor.copy(alpha = 0.3f) else tickColor.copy(alpha = 0.6f),
                        start = Offset((center.x + minorTickStart * cos(rad)).toFloat(), (center.y + minorTickStart * sin(rad)).toFloat()),
                        end = Offset((center.x + minorTickEnd * cos(rad)).toFloat(), (center.y + minorTickEnd * sin(rad)).toFloat()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                
                // Draw minute numbers for 24h mode
                if (!is12HourDial && m % 5 == 0) {
                    val mAngle = (m * 6f) - 90f
                    val rad = Math.toRadians(mAngle.toDouble())
                    
                    val minNumRadius = clockRadius - 44.dp.toPx()
                    val numX = (center.x + minNumRadius * cos(rad)).toFloat()
                    val numY = (center.y + minNumRadius * sin(rad) + minuteTextPaint.textSize / 3f).toFloat()
                    
                    val minStr = if (m < 10) "0$m" else m.toString()
                    
                    drawContext.canvas.nativeCanvas.drawText(minStr, numX, numY, minuteTextPaint)
                }
            }

            // 5. Clock Hands (Hour, Minute, Second)
            val hours = currentTime.hour
            val minutes = currentTime.minute
            val seconds = currentTime.second

            // Hour Hand
            val hourValue = if (is12HourDial) (hours % 12) + minutes / 60f else hours + minutes / 60f
            val maxHours = if (is12HourDial) 12f else 24f
            val hourAngle = (hourValue * (360f / maxHours)) - 90f
            val hourRad = Math.toRadians(hourAngle.toDouble())
            val hourLength = clockRadius * 0.52f

            drawLine(
                color = textColor,
                start = center,
                end = Offset((center.x + hourLength * cos(hourRad)).toFloat(), (center.y + hourLength * sin(hourRad)).toFloat()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Minute Hand
            val minuteValue = minutes + seconds / 60f
            val minuteAngle = (minuteValue * 6f) - 90f
            val minuteRad = Math.toRadians(minuteAngle.toDouble())
            val minuteLength = clockRadius * 0.75f

            drawLine(
                color = textColor,
                start = center,
                end = Offset((center.x + minuteLength * cos(minuteRad)).toFloat(), (center.y + minuteLength * sin(minuteRad)).toFloat()),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Second Hand
            val secondAngle = (seconds * 6f) - 90f
            val secondRad = Math.toRadians(secondAngle.toDouble())
            val secondLength = clockRadius * 0.85f

            drawLine(
                color = accentRed,
                start = center,
                end = Offset((center.x + secondLength * cos(secondRad)).toFloat(), (center.y + secondLength * sin(secondRad)).toFloat()),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center Pin / Cap
            drawCircle(color = accentRed, radius = 5.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = center)

            // 6. Draw Task Text/Icon directly INSIDE the Arc Bars
            allSegments.forEach { segment ->
                val task = segment.task
                val totalMinutesForRing = if (is12HourDial) 720 else 1440
                val sweepAngle = (segment.durationMinutesInHalfDay / totalMinutesForRing.toFloat()) * 360f
                val midMinute = segment.startMinuteInHalfDay + (segment.durationMinutesInHalfDay / 2)
                val midAngleDeg = ((midMinute % totalMinutesForRing) / totalMinutesForRing.toFloat()) * 360f - 90f
                val rad = Math.toRadians(midAngleDeg.toDouble())

                val scaleFactor = segmentScaleMap[segment] ?: 1f
                val layerStepPx = (if (!is12HourDial) px34 else px28) * scaleFactor

                val ringRadius = calculateSegmentRadius(
                    segment, is12HourDial, arcRadius, innerArcRadius, outerArcRadius, layerStepPx
                )

                val badgeX = (center.x + ringRadius * cos(rad)).toFloat()
                val badgeY = (center.y + ringRadius * sin(rad)).toFloat()
                // Tangent direction along arc curve
                val tangentAngle = midAngleDeg + 90f
                var normAngle = tangentAngle % 360f
                if (normAngle > 180f) normAngle -= 360f
                if (normAngle < -180f) normAngle += 360f

                // Prevent text from ever being upside down
                var textRotation = normAngle
                if (textRotation > 90f || textRotation < -90f) {
                    textRotation += 180f
                }

                val isActive = task.isCurrent(currentMinuteOfDay)
                val strokeWidth = (if (!is12HourDial) {
                    if (isActive) 42.dp.toPx() else 34.dp.toPx()
                } else {
                    if (isActive) 34.dp.toPx() else 28.dp.toPx()
                }) * scaleFactor

                val arcLengthPx = (sweepAngle / 360f) * (2f * kotlin.math.PI.toFloat() * ringRadius)
                val usableWidthForFullText = arcLengthPx + (strokeWidth * 0.3f) - 10.dp.toPx()
                val usableWidthForIcon = arcLengthPx + (strokeWidth * 0.5f) - 4.dp.toPx()

                val fullLabel = if (task.icon.isNotEmpty()) "${task.icon} ${task.title}" else task.title
                val fullTextWidth = badgePaint.measureText(fullLabel)

                val iconLabel = task.icon
                val iconTextWidth = if (iconLabel.isNotEmpty()) badgePaint.measureText(iconLabel) else 0f

                // Select label that fits strictly inside the task capsule background
                val labelToDraw = when {
                    fullLabel.isNotEmpty() && usableWidthForFullText >= fullTextWidth -> fullLabel
                    iconLabel.isNotEmpty() && usableWidthForIcon >= iconTextWidth -> iconLabel
                    else -> null
                }

                if (labelToDraw != null) {
                    val textPath = android.graphics.Path()
                    val rectF = android.graphics.RectF(
                        center.x - ringRadius, center.y - ringRadius,
                        center.x + ringRadius, center.y + ringRadius
                    )
                    
                    val startAngle = ((segment.startMinuteInHalfDay / totalMinutesForRing.toFloat()) * 360f) - 90f
                    val isBottomHalf = midAngleDeg > 0f && midAngleDeg < 180f
                    
                    val capAngle = ((strokeWidth / 2f) / (2f * kotlin.math.PI.toFloat() * ringRadius) * 360f).toFloat()
                    val extStartAngle = startAngle - capAngle
                    val extSweepAngle = sweepAngle + (2f * capAngle)
                    
                    if (isBottomHalf) {
                        textPath.addArc(rectF, extStartAngle + extSweepAngle, -extSweepAngle)
                    } else {
                        textPath.addArc(rectF, extStartAngle, extSweepAngle)
                    }

                    val originalAlign = badgePaint.textAlign
                    badgePaint.textAlign = android.graphics.Paint.Align.LEFT

                    val vOffset = (badgePaint.descent() - badgePaint.ascent()) / 2f - badgePaint.descent()
                    val textWidth = badgePaint.measureText(labelToDraw)
                    
                    val extendedArcLengthPx = arcLengthPx + strokeWidth
                    val hOffset = (extendedArcLengthPx - textWidth) / 2f

                    drawContext.canvas.nativeCanvas.drawTextOnPath(
                        labelToDraw,
                        textPath,
                        hOffset,
                        vOffset,
                        badgePaint
                    )

                    badgePaint.textAlign = originalAlign
                }
            }
        }
    }
}

// Helper to generate an annular sector (ring block) with smooth rounded vertices
private fun createRoundedAnnularSectorPath(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDeg: Float,
    sweepAngleDeg: Float,
    cornerRadiusPx: Float
): Path {
    val rIn = maxOf(1f, innerRadius)
    val rOut = maxOf(rIn + 2f, outerRadius)
    val bandWidth = rOut - rIn
    val sweepAngle = maxOf(0.1f, sweepAngleDeg)

    // Calculate maximum allowable corner radius so corners never overlap or invert
    val maxCrByRadial = (bandWidth / 2f) - 0.5f
    val maxCrByAngleOuter = (sweepAngle / 2.1f) * (kotlin.math.PI.toFloat() / 180f) * rOut
    val maxCrByAngleInner = (sweepAngle / 2.1f) * (kotlin.math.PI.toFloat() / 180f) * rIn
    val cr = maxOf(0.5f, minOf(cornerRadiusPx, maxCrByRadial, maxCrByAngleOuter, maxCrByAngleInner))

    val daOut = (cr / rOut) * (180f / kotlin.math.PI.toFloat())
    val daIn = (cr / rIn) * (180f / kotlin.math.PI.toFloat())

    val a1 = startAngleDeg
    val a2 = startAngleDeg + sweepAngle

    val radA1 = Math.toRadians(a1.toDouble()).toFloat()
    val radA2 = Math.toRadians(a2.toDouble()).toFloat()

    val cosA1 = kotlin.math.cos(radA1)
    val sinA1 = kotlin.math.sin(radA1)
    val cosA2 = kotlin.math.cos(radA2)
    val sinA2 = kotlin.math.sin(radA2)

    // Sharp corner vertices
    val outerStartVertex = Offset(center.x + rOut * cosA1, center.y + rOut * sinA1)
    val outerEndVertex = Offset(center.x + rOut * cosA2, center.y + rOut * sinA2)
    val innerEndVertex = Offset(center.x + rIn * cosA2, center.y + rIn * sinA2)
    val innerStartVertex = Offset(center.x + rIn * cosA1, center.y + rIn * sinA1)

    // Points on radial edges
    val outerEndRadialPoint = Offset(center.x + (rOut - cr) * cosA2, center.y + (rOut - cr) * sinA2)
    val innerEndRadialPoint = Offset(center.x + (rIn + cr) * cosA2, center.y + (rIn + cr) * sinA2)
    val innerStartRadialPoint = Offset(center.x + (rIn + cr) * cosA1, center.y + (rIn + cr) * sinA1)
    val outerStartRadialPoint = Offset(center.x + (rOut - cr) * cosA1, center.y + (rOut - cr) * sinA1)

    // Points on arcs
    val radInnerStartArc = Math.toRadians((a1 + daIn).toDouble()).toFloat()
    val radInnerEndArc = Math.toRadians((a2 - daIn).toDouble()).toFloat()
    val innerStartArcPoint = Offset(center.x + rIn * kotlin.math.cos(radInnerStartArc), center.y + rIn * kotlin.math.sin(radInnerStartArc))
    val innerEndArcPoint = Offset(center.x + rIn * kotlin.math.cos(radInnerEndArc), center.y + rIn * kotlin.math.sin(radInnerEndArc))

    val radOuterStartArc = Math.toRadians((a1 + daOut).toDouble()).toFloat()
    val outerStartArcPoint = Offset(center.x + rOut * kotlin.math.cos(radOuterStartArc), center.y + rOut * kotlin.math.sin(radOuterStartArc))

    val outerRect = Rect(center.x - rOut, center.y - rOut, center.x + rOut, center.y + rOut)
    val innerRect = Rect(center.x - rIn, center.y - rIn, center.x + rIn, center.y + rIn)

    return Path().apply {
        moveTo(outerStartArcPoint.x, outerStartArcPoint.y)

        // Outer arc clockwise
        arcTo(
            rect = outerRect,
            startAngleDegrees = a1 + daOut,
            sweepAngleDegrees = maxOf(0.01f, sweepAngle - 2f * daOut),
            forceMoveTo = false
        )

        // Round Outer End Corner
        quadraticTo(outerEndVertex.x, outerEndVertex.y, outerEndRadialPoint.x, outerEndRadialPoint.y)

        // Radial edge down to inner radius
        lineTo(innerEndRadialPoint.x, innerEndRadialPoint.y)

        // Round Inner End Corner
        quadraticTo(innerEndVertex.x, innerEndVertex.y, innerEndArcPoint.x, innerEndArcPoint.y)

        // Inner arc counter-clockwise back to start angle
        arcTo(
            rect = innerRect,
            startAngleDegrees = a2 - daIn,
            sweepAngleDegrees = -maxOf(0.01f, sweepAngle - 2f * daIn),
            forceMoveTo = false
        )

        // Round Inner Start Corner
        quadraticTo(innerStartVertex.x, innerStartVertex.y, innerStartRadialPoint.x, innerStartRadialPoint.y)

        // Radial edge up to outer radius
        lineTo(outerStartRadialPoint.x, outerStartRadialPoint.y)

        // Round Outer Start Corner
        quadraticTo(outerStartVertex.x, outerStartVertex.y, outerStartArcPoint.x, outerStartArcPoint.y)

        close()
    }
}

// Helper color hex parser
fun parseHexColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = android.graphics.Color.parseColor("#$cleanHex")
        Color(colorInt)
    } catch (e: Exception) {
        Color(0xFFF472B6) // Default soft pink
    }
}
