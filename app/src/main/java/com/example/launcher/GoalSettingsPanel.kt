package com.example.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSettingsPanel(
    user: UserProfileEntity?,
    stats: StatsResponse,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialGoal = user?.dailyGoal ?: 120
    var currentGoalSelection by remember { mutableStateOf(initialGoal) }

    val todayDateStr = remember(stats) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val focusedMinutesToday = stats.daily.firstOrNull { 
        todayDateStr == it.dayDate
    }?.minutes ?: 0

    // Preview percentage calculations
    val previewProgressVal = if (currentGoalSelection > 0) {
        focusedMinutesToday.toFloat() / currentGoalSelection.toFloat()
    } else {
        0f
    }
    val previewPct = (previewProgressVal * 100).toInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = previewProgressVal.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "PreviewProgressAnim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("goal_settings_panel")
            .glassmorphic(shape = RoundedCornerShape(28.dp))
            .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(
            containerColor = GlassmorphismStyle.getGlassBackgroundColor()
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val parentWidth = maxWidth

            // Percentage-based dynamic measurements of parent container dimensions rather than fixed pixel values
            val relativePadding = (parentWidth * 0.065f).coerceIn(16.dp, 28.dp)
            val headerTitleSize = (parentWidth.value * 0.055f).coerceIn(16f, 22f).sp
            val previewCircleSize = (parentWidth * 0.19f).coerceIn(56.dp, 80.dp)
            val previewPercentFontSize = (parentWidth.value * 0.033f).coerceIn(10f, 13f).sp

            val arrowBtnSize = (parentWidth * 0.10f).coerceIn(32.dp, 44.dp)
            val arrowIconSize = (arrowBtnSize * 0.45f).coerceIn(14.dp, 20.dp)
            val currentGoalFontSize = (parentWidth.value * 0.078f).coerceIn(24f, 32f).sp

            val presetChipVPad = (parentWidth * 0.028f).coerceIn(8.dp, 12.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(relativePadding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FOCUS GOAL SETTINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = FFColors.textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Define Daily Target",
                            fontSize = headerTitleSize,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Live preview and stats block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FFColors.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, FFColors.borderSubtle, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Miniature Live Preview Circle
                    Box(
                        modifier = Modifier
                            .size(previewCircleSize)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val trackColor = if (FFColors.isDark) FFColors.surfaceAlt else FFColors.border
                        val progressColor = if (previewPct >= 100) FFColors.green else FFColors.orange

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidthPx = 6.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }

                        Text(
                            text = "$previewPct%",
                            fontSize = previewPercentFontSize,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.textPrimary
                        )
                    }

                    // Interactive live text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live Progress Impact",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FFColors.orange
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "With a goal of $currentGoalSelection minutes, your logged focus time ($focusedMinutesToday mins) achieves $previewPct% of today's target.",
                            fontSize = 12.sp,
                            color = FFColors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main Slider with - / + buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(arrowBtnSize)
                            .clip(CircleShape)
                            .background(FFColors.surfaceAlt)
                            .clickable {
                                if (currentGoalSelection > 15) currentGoalSelection -= 15
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrement",
                            tint = FFColors.textPrimary,
                            modifier = Modifier.size(arrowIconSize)
                        )
                    }

                    Text(
                        text = "${currentGoalSelection}m",
                        fontSize = currentGoalFontSize,
                        fontWeight = FontWeight.Bold,
                        color = FFColors.textPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(arrowBtnSize)
                            .clip(CircleShape)
                            .background(FFColors.surfaceAlt)
                            .clickable {
                                if (currentGoalSelection < 480) currentGoalSelection += 15
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increment",
                            tint = FFColors.textPrimary,
                            modifier = Modifier.size(arrowIconSize)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Glowing Premium Slider Track
                Slider(
                    value = currentGoalSelection.toFloat(),
                    onValueChange = { currentGoalSelection = (it.toInt() / 15 * 15).coerceIn(15, 480) },
                    valueRange = 15f..480f,
                    steps = (480 - 15) / 15 - 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_settings_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = FFColors.orange,
                        inactiveTrackColor = if (FFColors.isDark) Color(0xFF27272A) else Color(0xFFE4E4E7),
                        thumbColor = FFColors.orange,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )

                // Hour conversions info
                val h = currentGoalSelection / 60
                val m = currentGoalSelection % 60
                val readableStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                Text(
                    text = "Equivalent to $readableStr per day",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = FFColors.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Preset Quick Select Chips
                Text(
                    text = "PRESET QUICK SELECTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = FFColors.textMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(30, 60, 120, 240, 360)
                    presets.forEach { preset ->
                        val isSelected = currentGoalSelection == preset
                        val label = when (preset) {
                            30 -> "30m"
                            60 -> "1h"
                            120 -> "2h"
                            240 -> "4h"
                            360 -> "6h"
                            else -> "${preset}m"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) FFColors.orangeBg else FFColors.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) FFColors.orange else FFColors.borderSubtle,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { currentGoalSelection = preset }
                                .padding(vertical = presetChipVPad),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) FFColors.orange else FFColors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, FFColors.border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = FFColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = { onApply(currentGoalSelection) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FFColors.textPrimary,
                            contentColor = if (FFColors.isDark) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Apply Target",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (FFColors.isDark) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}
