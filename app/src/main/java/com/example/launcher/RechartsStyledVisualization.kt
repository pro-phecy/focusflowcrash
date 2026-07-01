package com.example.launcher

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import com.example.data.DailyStatModel
import com.example.data.StatsResponse
import kotlin.math.roundToInt

@OptIn(ExperimentalTextApi::class)
@Composable
fun RechartsStyledVisualization(
    stats: StatsResponse,
    baseScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var chartType by remember { mutableStateOf("trend") } // "trend" (Area) or "bar" (Columns)
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .glassmorphic(shape = RoundedCornerShape(28.dp))
            .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(
            containerColor = GlassmorphismStyle.getGlassBackgroundColor()
        )
    ) {
        Column(
            modifier = Modifier.padding((18 * baseScale).dp)
        ) {
        // Chart Header with beautiful title, Legend, and Toggle Tabs (like Recharts dashboards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PRODUCTIVITY INDEX",
                    fontSize = (11 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textSecondary,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                val totalMin = stats.daily.sumOf { it.minutes }
                Text(
                    text = "${totalMin}m Focused",
                    fontSize = (18 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = FFColors.textPrimary
                )
            }

            // Tabs / Toggle Button Group
            Row(
                modifier = Modifier
                    .background(FFColors.surfaceAlt, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Trend Line / Area Button
                val trendSelected = chartType == "trend"
                Box(
                    modifier = Modifier
                        .background(
                            if (trendSelected) FFColors.surface else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { chartType = "trend"; selectedPointIndex = null }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Trend",
                            tint = if (trendSelected) FFColors.orange else FFColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Trend",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (trendSelected) FFColors.textPrimary else FFColors.textSecondary
                        )
                    }
                }

                // Bar Chart Button
                val barSelected = chartType == "bar"
                Box(
                    modifier = Modifier
                        .background(
                            if (barSelected) FFColors.surface else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { chartType = "bar"; selectedPointIndex = null }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Bars",
                            tint = if (barSelected) FFColors.orange else FFColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Bars",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (barSelected) FFColors.textPrimary else FFColors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (stats.daily.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((180 * baseScale).dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No focus logs recorded yet.", color = FFColors.textDisabled, fontSize = 14.sp)
            }
        } else {
            // Interactive Chart Canvas Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((180 * baseScale).dp)
            ) {
                val list = stats.daily
                val maxVal = list.maxOfOrNull { it.minutes }?.coerceAtLeast(30) ?: 30
                val gridYCount = 4

                // For drawing beautiful text labels natively on the Canvas
                val textMeasurer = rememberTextMeasurer()
                val labelStyle = TextStyle(
                    color = FFColors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )

                // Render Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(list, maxVal) {
                            detectTapGestures { offset ->
                                val usableWidth = size.width - 110f // leave horizontal margins
                                val stepX = if (list.size > 1) usableWidth / (list.size - 1) else usableWidth
                                val clickedIdx = ((offset.x - 70f) / stepX).roundToInt().coerceIn(0, list.size - 1)
                                selectedPointIndex = if (selectedPointIndex == clickedIdx) null else clickedIdx
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    val leftMargin = 70f
                    val rightMargin = 40f
                    val topMargin = 30f
                    val bottomMargin = 40f

                    val chartWidth = width - leftMargin - rightMargin
                    val chartHeight = height - topMargin - bottomMargin

                    // Draw Horizontal Cartesian Grid lines (like CartesianGrid in Recharts)
                    for (i in 0..gridYCount) {
                        val fraction = i.toFloat() / gridYCount.toFloat()
                        val y = topMargin + chartHeight * (1f - fraction)
                        
                        // Grid Line
                        drawLine(
                            color = FFColors.borderSubtle.copy(alpha = 0.5f),
                            start = Offset(leftMargin, y),
                            end = Offset(width - rightMargin, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Y-Axis Tick Text
                        val minValLabel = (fraction * maxVal).roundToInt()
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString("${minValLabel}m"),
                            style = labelStyle
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = leftMargin - textLayoutResult.size.width - 15f,
                                y = y - textLayoutResult.size.height / 2f
                            )
                        )
                    }

                    // Precalculate points
                    val points = list.mapIndexed { idx, bar ->
                        val x = if (list.size > 1) {
                            leftMargin + (idx.toFloat() / (list.size - 1).toFloat()) * chartWidth
                        } else {
                            leftMargin + chartWidth / 2f
                        }
                        val y = topMargin + chartHeight * (1f - (bar.minutes.toFloat() / maxVal.toFloat()))
                        Offset(x, y)
                    }

                    // DRAW AREA/LINE CHART (Trend Mode)
                    if (chartType == "trend") {
                        if (points.isNotEmpty()) {
                            // Create filled Area Path (Translucent Gradient fading down, signature of Recharts)
                            val areaPath = Path().apply {
                                moveTo(points.first().x, topMargin + chartHeight)
                                points.forEach { offset ->
                                    lineTo(offset.x, offset.y)
                                }
                                lineTo(points.last().x, topMargin + chartHeight)
                                close()
                            }

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        FFColors.orange.copy(alpha = 0.25f),
                                        FFColors.orange.copy(alpha = 0.0f)
                                    ),
                                    startY = topMargin,
                                    endY = topMargin + chartHeight
                                )
                            )

                            // Create smooth glowing Line Path
                            val linePath = Path().apply {
                                if (points.isNotEmpty()) {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        // Draw beautiful curved spline using Cubic Beziers
                                        val p0 = points[i - 1]
                                        val p1 = points[i]
                                        val cp1X = p0.x + (p1.x - p0.x) / 3f
                                        val cp2X = p0.x + 2f * (p1.x - p0.x) / 3f
                                        cubicTo(cp1X, p0.y, cp2X, p1.y, p1.x, p1.y)
                                    }
                                }
                            }

                            drawPath(
                                path = linePath,
                                color = FFColors.orange,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw point circles (dots)
                            points.forEachIndexed { index, point ->
                                val isSelected = selectedPointIndex == index
                                // Outer glow
                                drawCircle(
                                    color = if (isSelected) FFColors.orange.copy(alpha = 0.3f) else FFColors.surfaceAlt,
                                    radius = if (isSelected) 8.dp.toPx() else 4.dp.toPx(),
                                    center = point
                                )
                                // Inner point
                                drawCircle(
                                    color = if (isSelected) FFColors.orange else FFColors.orange.copy(alpha = 0.8f),
                                    radius = if (isSelected) 5.dp.toPx() else 2.5.dp.toPx(),
                                    center = point
                                )
                            }
                        }
                    } else {
                        // DRAW BAR CHART (Bar Mode)
                        val barWidth = (chartWidth / list.size * 0.45f).coerceIn(16f, 40f)
                        points.forEachIndexed { index, point ->
                            val isSelected = selectedPointIndex == index
                            val barHeight = topMargin + chartHeight - point.y
                            val rectLeft = point.x - barWidth / 2f
                            val rectTop = point.y
                            val rectRight = point.x + barWidth / 2f
                            val rectBottom = topMargin + chartHeight

                            // Draw rounded column bar
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = androidx.compose.ui.geometry.Rect(rectLeft, rectTop, rectRight, rectBottom),
                                        topLeft = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                        topRight = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                )
                            }
                            drawPath(
                                path = path,
                                color = if (isSelected) FFColors.orange else (if (index == list.size - 1) FFColors.orange.copy(alpha = 0.85f) else FFColors.border)
                            )
                        }
                    }

                    // DRAW X-AXIS LABELS
                    list.forEachIndexed { index, bar ->
                        val point = points[index]
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(bar.dayLabel),
                            style = TextStyle(
                                color = if (selectedPointIndex == index) FFColors.textPrimary else FFColors.textDisabled,
                                fontSize = 11.sp,
                                fontWeight = if (selectedPointIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = point.x - textLayoutResult.size.width / 2f,
                                y = topMargin + chartHeight + 12f
                            )
                        )
                    }

                    // Draw Interactive vertical dashed marker line
                    selectedPointIndex?.let { idx ->
                        if (idx in points.indices) {
                            val point = points[idx]
                            drawLine(
                                color = FFColors.orange.copy(alpha = 0.5f),
                                start = Offset(point.x, topMargin),
                                end = Offset(point.x, topMargin + chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                        }
                    }
                }

                // Interactive popover tooltip card (exactly like Recharts responsive tooltip)
                selectedPointIndex?.let { idx ->
                    if (idx in list.indices) {
                        val bar = list[idx]
                        Box(
                            modifier = Modifier
                                .align(if (idx < list.size / 2) Alignment.TopEnd else Alignment.TopStart)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = FFColors.surfaceAlt
                                ),
                                border = BorderStroke(1.dp, FFColors.orange.copy(alpha = 0.4f)),
                                modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = bar.dayDate,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FFColors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(FFColors.orange, RoundedCornerShape(99.dp))
                                        )
                                        Text(
                                            text = "Focus: ${bar.minutes} min",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FFColors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (stats.taskBreakdown.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = (18 * baseScale).dp),
                color = FFColors.borderSubtle.copy(alpha = 0.4f)
            )

            Text(
                text = "TASK DURATION BREAKDOWN",
                fontSize = (11 * baseScale).sp,
                fontWeight = FontWeight.Bold,
                color = FFColors.textSecondary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = (10 * baseScale).dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy((12 * baseScale).dp)
            ) {
                val totalMinutes = stats.taskBreakdown.sumOf { it.minutes }.coerceAtLeast(1)
                stats.taskBreakdown.take(5).forEach { task ->
                    val percentage = (task.minutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
                    
                    // Animate the progress bar width
                    val animatedProgress by animateFloatAsState(
                        targetValue = percentage,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "ProgressBarAnimation"
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(android.graphics.Color.parseColor(task.colorHex)), RoundedCornerShape(99.dp))
                                )
                                Text(
                                    text = task.taskName,
                                    fontSize = (13 * baseScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FFColors.textPrimary
                                )
                            }
                            Text(
                                text = "${task.minutes}m (${(percentage * 100).roundToInt()}%)",
                                fontSize = (12 * baseScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = FFColors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Progress bar container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(FFColors.surfaceAlt, RoundedCornerShape(99.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .background(
                                        Color(android.graphics.Color.parseColor(task.colorHex)),
                                        RoundedCornerShape(99.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
}
