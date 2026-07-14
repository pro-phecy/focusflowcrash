package com.example.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        displayPct >= 100 -> "Daily goal achieved!"
        displayPct >= 75 -> "Almost there."
        displayPct >= 50 -> "Halfway mark."
        displayPct >= 25 -> "In the zone."
        focusedMinutesToday > 0 -> "Focusing today."
        else -> "Start focusing."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_goal_progress_card")
            .glassmorphic(shape = RoundedCornerShape(20.dp), borderWidth = 1.dp, hasShadow = false)
            .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = GlassmorphismStyle.getGlassBackgroundColor()
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val parentWidth = maxWidth
            
            // Relative sizes of the parent container dimensions rather than fixed pixel values
            val relativeHPad = (parentWidth * 0.055f).coerceIn(12.dp, 24.dp)
            val relativeVPad = (parentWidth * 0.055f).coerceIn(12.dp, 24.dp)
            val barHeight = (parentWidth * 0.012f).coerceIn(4.dp, 8.dp)
            val barSpacerHeight = (parentWidth * 0.038f).coerceIn(8.dp, 16.dp)
            val footerSpacerHeight = (parentWidth * 0.033f).coerceIn(8.dp, 16.dp)
            
            val controlBtnSize = (parentWidth * 0.078f).coerceIn(24.dp, 36.dp)
            val controlIconSize = (parentWidth * 0.039f).coerceIn(12.dp, 18.dp)
            val textMinWidth = (parentWidth * 0.095f).coerceIn(30.dp, 44.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = relativeHPad, vertical = relativeVPad)
            ) {
                // Header Row: Progress readout on left, big percentage on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DAILY PROGRESS",
                            fontSize = (10 * baseScale).sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = FFColors.textMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        val todayH = focusedMinutesToday / 60
                        val todayM = focusedMinutesToday % 60
                        val goalH = dailyGoalMinutes / 60
                        val goalM = dailyGoalMinutes % 60

                        val todayDisplay = if (todayH > 0) "${todayH}h ${todayM}m" else "${todayM}m"
                        val goalDisplay = if (goalH > 0) "${goalH}h ${goalM}m" else "${goalM}m"

                        Text(
                            text = "$todayDisplay of $goalDisplay",
                            fontSize = (18 * baseScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary
                        )
                    }

                    Text(
                        text = "$displayPct%",
                        fontSize = (22 * baseScale).sp,
                        fontWeight = FontWeight.Light,
                        color = if (displayPct >= 100) FFColors.green else FFColors.orange
                    )
                }

                Spacer(modifier = Modifier.height(barSpacerHeight))

                // Ultra-minimal single line progress bar
                val trackColor = if (FFColors.isDark) Color(0xFF1E1E20) else Color(0xFFF2F2F5)
                val progressColor = if (displayPct >= 100) FFColors.green else FFColors.orange

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(barHeight / 2f))
                        .background(trackColor)
                ) {
                    if (animatedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(barHeight / 2f))
                                .background(progressColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(footerSpacerHeight))

                // Subdued footer row with message on left, simple compact goal adjuster on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        fontSize = (11 * baseScale).sp,
                        color = FFColors.textSecondary,
                        fontWeight = FontWeight.Normal
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Minus Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(controlBtnSize)
                                .clip(CircleShape)
                                .background(FFColors.surfaceAlt.copy(alpha = 0.5f))
                                .clickable {
                                    if (dailyGoalMinutes > 15) {
                                        onUpdateGoal(dailyGoalMinutes - 15)
                                    }
                                }
                                .testTag("decrement_goal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease daily goal",
                                tint = FFColors.textSecondary,
                                modifier = Modifier.size(controlIconSize)
                            )
                        }

                        Text(
                            text = "${dailyGoalMinutes}m",
                            fontSize = (11 * baseScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary,
                            modifier = Modifier.widthIn(min = textMinWidth),
                            textAlign = TextAlign.Center
                        )

                        // Plus Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(controlBtnSize)
                                .clip(CircleShape)
                                .background(FFColors.surfaceAlt.copy(alpha = 0.5f))
                                .clickable {
                                    if (dailyGoalMinutes < 480) {
                                        onUpdateGoal(dailyGoalMinutes + 15)
                                    }
                                }
                                .testTag("increment_goal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase daily goal",
                                tint = FFColors.textSecondary,
                                modifier = Modifier.size(controlIconSize)
                            )
                        }
                    }
                }
            }
        }
    }
}
