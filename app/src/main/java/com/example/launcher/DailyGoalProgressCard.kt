package com.example.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StatsResponse
import com.example.data.UserProfileEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyGoalProgressCard(
    user: UserProfileEntity?,
    stats: StatsResponse,
    onUpdateGoal: (Int) -> Unit,
    baseScale: Float = 1.0f,
    responsiveHSpacing: androidx.compose.ui.unit.Dp = 16.dp,
    responsiveVSpacing: androidx.compose.ui.unit.Dp = 16.dp,
    smallSpacer: androidx.compose.ui.unit.Dp = 8.dp,
    mediumSpacer: androidx.compose.ui.unit.Dp = 16.dp,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dailyGoalMinutes = user?.dailyGoal ?: 120
    val todayDateStr = remember(stats) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val focusedMinutesToday = stats.daily.firstOrNull { 
        todayDateStr == it.dayDate
    }?.minutes ?: 0

    val progressVal = if (dailyGoalMinutes > 0) focusedMinutesToday.toFloat() / dailyGoalMinutes.toFloat() else 0f
    val displayPct = (progressVal * 100).toInt().coerceIn(0, 100)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progressVal.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "GoalProgressAnim"
    )

    // Dynamic encouraging message based on completion state
    val message = when {
        displayPct >= 100 -> "Daily goal achieved! Sensational focus today! 🎉"
        displayPct >= 75 -> "Almost there! One final push to crush your goal!"
        displayPct >= 50 -> "Halfway to victory! Keep riding that productivity wave."
        displayPct >= 25 -> "Great momentum! You're deeply locked into your zone."
        focusedMinutesToday > 0 -> "You're off to a fantastic start. Keep going!"
        else -> "Kickstart your day with a high-fidelity focus session."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_goal_progress_card")
            .glassmorphic(shape = RoundedCornerShape(28.dp))
            .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(
            containerColor = GlassmorphismStyle.getGlassBackgroundColor()
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = responsiveHSpacing, vertical = (responsiveVSpacing.value * 1.1f).coerceIn(20f, 36f).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DAILY FOCUS PROGRESS",
                        fontSize = (11 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = FFColors.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val todayH = focusedMinutesToday / 60
                    val todayM = focusedMinutesToday % 60
                    val goalH = dailyGoalMinutes / 60
                    val goalM = dailyGoalMinutes % 60

                    val todayDisplay = if (todayH > 0) "${todayH}h ${todayM}m" else "${todayM}m"
                    val goalDisplay = if (goalH > 0) "${goalH}h ${goalM}m" else "${goalM}m"

                    Text(
                        text = "$todayDisplay / $goalDisplay",
                        fontSize = (20 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary
                    )
                }

                // Elegant circular progress track
                Box(
                    modifier = Modifier
                        .size((80 * baseScale).dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = if (FFColors.isDark) FFColors.surfaceAlt else FFColors.border
                    val progressColor = if (displayPct >= 100) FFColors.green else FFColors.orange

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 8.dp.toPx()
                        
                        // Track Arc
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                        
                        // Active Progress Arc
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                    
                    Text(
                        text = "$displayPct%",
                        fontSize = (13 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(mediumSpacer))

            // Beautiful linear horizontal progress bar
            DailyLinearProgressBar(
                progress = progressVal,
                displayPct = displayPct,
                baseScale = baseScale,
                modifier = Modifier.padding(bottom = smallSpacer)
            )

            Spacer(modifier = Modifier.height(smallSpacer))

            // Text message
            Text(
                text = message,
                fontSize = (12 * baseScale).sp,
                color = FFColors.textSecondary,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(mediumSpacer))

            // Subdued divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FFColors.borderSubtle)
            )

            Spacer(modifier = Modifier.height(mediumSpacer))

            // Interactive target daily goal adjust bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .then(
                            if (onOpenSettings != null) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenSettings() }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            } else Modifier
                        )
                ) {
                    Text(
                        text = "Adjust Daily Goal",
                        fontSize = (12 * baseScale).sp,
                        color = FFColors.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (onOpenSettings != null) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Open goal slider settings",
                            tint = FFColors.orange,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Minus Button
                    IconButton(
                        onClick = {
                            if (dailyGoalMinutes > 15) {
                                onUpdateGoal(dailyGoalMinutes - 15)
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FFColors.surfaceAlt)
                            .testTag("decrement_goal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease daily goal",
                            tint = FFColors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Value label
                    Text(
                        text = "${dailyGoalMinutes}m",
                        fontSize = (13 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary,
                        modifier = Modifier.widthIn(min = 40.dp),
                        textAlign = TextAlign.Center
                    )

                    // Plus Button
                    IconButton(
                        onClick = {
                            if (dailyGoalMinutes < 480) {
                                onUpdateGoal(dailyGoalMinutes + 15)
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FFColors.surfaceAlt)
                            .testTag("increment_goal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase daily goal",
                            tint = FFColors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyLinearProgressBar(
    progress: Float,
    displayPct: Int,
    baseScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "LinearProgressAnim"
    )

    val trackColor = if (FFColors.isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)
    val progressColor = if (displayPct >= 100) FFColors.green else FFColors.orange

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progress to Daily Goal",
                fontSize = (12 * baseScale).sp,
                fontWeight = FontWeight.Medium,
                color = FFColors.textSecondary
            )
            Text(
                text = "$displayPct%",
                fontSize = (12 * baseScale).sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
        }

        // Custom stylized progress track with milestone overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((12 * baseScale).dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
                .testTag("daily_linear_progress_bar"),
            contentAlignment = Alignment.CenterStart
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    progressColor.copy(alpha = 0.8f),
                                    progressColor
                                )
                            )
                        )
                )
            }

            // Draw elegant milestone dots at 25%, 50%, and 75%
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val radius = 3.dp.toPx()

                val milestones = listOf(0.25f, 0.50f, 0.75f)
                milestones.forEach { fraction ->
                    val x = width * fraction
                    val isReached = progress >= fraction
                    val dotColor = if (isReached) {
                        Color.White.copy(alpha = 0.9f)
                    } else {
                        if (FFColors.isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f)
                    }
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x, height / 2)
                    )
                }
            }
        }

        // Milestone scale labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelColor = FFColors.textMuted
            val labelSize = (10 * baseScale).sp
            val labelWeight = FontWeight.Medium

            Text("0%", color = labelColor, fontSize = labelSize, fontWeight = labelWeight)
            Text("25%", color = labelColor, fontSize = labelSize, fontWeight = labelWeight)
            Text("50%", color = labelColor, fontSize = labelSize, fontWeight = labelWeight)
            Text("75%", color = labelColor, fontSize = labelSize, fontWeight = labelWeight)
            Text("100%", color = labelColor, fontSize = labelSize, fontWeight = labelWeight)
        }
    }
}

