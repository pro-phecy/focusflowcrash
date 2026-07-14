package com.example.launcher

import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.Converters
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                imageView.setImageDrawable(icon)
            } catch (e: Exception) {
                imageView.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusLauncher(
    duration: Int,
    timeLeft: Int,
    isTimerRunning: Boolean,
    goal: String,
    allowedAppsJson: String,
    isLockRestrictEnabled: Boolean,
    onEnd: (completed: Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onResetTimer: () -> Unit,
    onToggleStatusBar: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val allowedAppsList = remember(allowedAppsJson) { Converters().toStringList(allowedAppsJson) }

    // Intercept back button to show exit bottom sheet
    var showExitSheet by remember { mutableStateOf(false) }
    var challengeInput by remember { mutableStateOf("") }
    var countdownSec by remember { mutableStateOf(5) }

    // Auto-hiding HUD mechanism states
    var isHudVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tactile continuous hold-to-exit gesture progress
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val durationMs = 1500f // 1.5 seconds hold duration
            while (isHolding) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed / durationMs).coerceIn(0f, 1f)
                if (holdProgress >= 1f) {
                    showExitSheet = true
                    isHolding = false
                    holdProgress = 0f
                    break
                }
                delay(16) // Smooth 60fps refresh
            }
        } else {
            // Smoothly decay the hold progress if released before completion
            while (holdProgress > 0f) {
                holdProgress = (holdProgress - 0.08f).coerceAtLeast(0f)
                delay(16)
            }
        }
    }

    LaunchedEffect(lastInteractionTime) {
        isHudVisible = true
        delay(5000)
        isHudVisible = false
    }

    LaunchedEffect(showExitSheet) {
        if (showExitSheet) {
            isHudVisible = true
        }
        if (showExitSheet && isLockRestrictEnabled) {
            challengeInput = ""
            countdownSec = 5
            while (countdownSec > 0) {
                delay(1000)
                countdownSec -= 1
            }
        }
    }

    BackHandler {
        Toast.makeText(context, "Accidental exit prevention: Press and hold the countdown timer or the bottom text to initiate exit.", Toast.LENGTH_LONG).show()
    }

    // Observe lifecycle events to hide/lock-task on resume (such as after launching and returning from companion apps)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onToggleStatusBar(false)
                try {
                    context.findActivity()?.startLockTask()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Restore status/navigation bars on exiting Focus mode screen completely
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        try {
            activity?.startLockTask()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        onDispose {
            onToggleStatusBar(true)
            try {
                activity?.stopLockTask()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    // Live Clock State
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        while (true) {
            currentTime = sdf.format(Date())
            delay(1000)
        }
    }

    // Calculate elapsed ratio for progress circle arc
    val elapsedRatio = if (duration > 0) timeLeft.toFloat() / duration.toFloat() else 1f
    val animatedProgress by animateFloatAsState(targetValue = elapsedRatio, label = "Progress")

    // Gesture control: swipe-down (PanResponder equivalent) threshold ~80dp opens the exit sheet
    var swipeOffsetY by remember { mutableStateOf(0f) }
    var isBreakState by remember { mutableStateOf(false) }

    // Immersive focus vs break color definitions
    val arcColorTarget = if (isBreakState) Color(0xFF10B981) else FFColors.orange
    val arcColor by animateColorAsState(targetValue = arcColorTarget, animationSpec = tween(800), label = "arcColor")

    val stateTitleColorTarget = if (isBreakState) Color(0xFF10B981) else FFColors.orange
    val stateTitleColor by animateColorAsState(targetValue = stateTitleColorTarget, animationSpec = tween(800), label = "stateTitleColor")

    val isHudCurrentlyVisible = isHudVisible || showExitSheet
    val hudAlpha by animateFloatAsState(
        targetValue = if (isHudCurrentlyVisible) 1f else 0f,
        animationSpec = if (isHudCurrentlyVisible) androidx.compose.animation.core.tween(300) else androidx.compose.animation.core.tween(1000),
        label = "HUD Fade"
    )

    AmbientGlowingBackground(
        isBreak = isBreakState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Instantly wake up HUD on any screen pointer event
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (swipeOffsetY > 150) {
                            Toast.makeText(context, "System Lock active. Press and hold the countdown timer or the bottom text to initiate exit.", Toast.LENGTH_LONG).show()
                        }
                        swipeOffsetY = 0f
                    },
                    onDragCancel = {
                        swipeOffsetY = 0f
                    },
                    onDrag = { _, dragAmount ->
                        // Accumulate swipe down
                        swipeOffsetY += dragAmount.y
                    }
                )
            }
            .testTag("focus_launcher_screen")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val parentWidth = maxWidth
            val parentHeight = maxHeight

            // Device-safe percentage ratios for elegant responsiveness based on parent container dimensions rather than fixed pixel values
            val responsiveHSpacing = (parentWidth * 0.055f).coerceIn(16.dp, 28.dp)
            val responsiveVSpacing = (parentHeight * 0.04f).coerceIn(16.dp, 36.dp)
            val circularRingSize = (parentHeight * 0.35f).coerceIn(180.dp, 260.dp)

            val baseScale = (parentWidth.value / 360f).coerceIn(0.85f, 1.25f)
            val displayTimeFontSize = (58 * baseScale).sp
            val companionIconSize = (14 * baseScale).coerceIn(12f, 18f).dp

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .graphicsLayer(alpha = hudAlpha)
                    .padding(vertical = responsiveVSpacing, horizontal = responsiveHSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            // --- TOP SECTION: Minimalist Goal reminder & State switcher ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (goal.isNotBlank()) {
                    Text(
                        text = if (isBreakState) "REST PERIOD" else goal.uppercase(),
                        fontSize = (11 * baseScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = stateTitleColor, // Beautiful color-shifted text
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = responsiveHSpacing, vertical = (responsiveVSpacing / 3f).coerceAtLeast(4.dp))
                    )
                } else {
                    Spacer(modifier = Modifier.height(responsiveVSpacing))
                }

                Spacer(modifier = Modifier.height((8 * baseScale).dp))

                // Custom immersive glassmorphic Focus / Break segmented switcher
                Row(
                    modifier = Modifier
                        .glassmorphic(shape = RoundedCornerShape(24.dp), borderWidth = 1.dp, hasShadow = false)
                        .background(GlassmorphismStyle.getGlassBackgroundColor(), RoundedCornerShape(24.dp))
                        .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Focus Option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (!isBreakState) FFColors.orange.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { isBreakState = false }
                            .padding(horizontal = (16 * baseScale).dp, vertical = (8 * baseScale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(FFColors.orange, CircleShape)
                            )
                            Text(
                                text = "FOCUS",
                                color = if (!isBreakState) FFColors.orange else FFColors.textSecondary,
                                fontSize = (10 * baseScale).sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Break Option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isBreakState) Color(0xFF10B981).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { isBreakState = true }
                            .padding(horizontal = (16 * baseScale).dp, vertical = (8 * baseScale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Text(
                                text = "BREAK",
                                color = if (isBreakState) Color(0xFF10B981) else FFColors.textSecondary,
                                fontSize = (10 * baseScale).sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // --- CENTER SECTION: Beautifully Centered Elegant Countdown Timer ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Circular Arc Countdown Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(circularRingSize)
                        .padding((12 * baseScale).dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        isHolding = false
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 3.dp.toPx() // Thinner and more elegant stroke
                        // Secondary track
                        drawCircle(
                            color = FFColors.borderSubtle.copy(alpha = 0.5f),
                            style = Stroke(width = strokeWidth)
                        )
                        // Active Arc
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Tactile continuous hold-to-exit indicator
                        if (holdProgress > 0f) {
                            drawArc(
                                color = FFColors.red,
                                startAngle = -90f,
                                sweepAngle = holdProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Large Digital Text
                    val m = (timeLeft / 60)
                    val s = (timeLeft % 60)
                    val displayTime = String.format(Locale.getDefault(), "%02d:%02d", m, s)

                    Text(
                        text = displayTime,
                        fontSize = displayTimeFontSize,
                        fontWeight = FontWeight.ExtraLight,
                        color = FFColors.textPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1).sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Live Clock underneath
                Text(
                    text = currentTime,
                    fontSize = 13.sp,
                    color = FFColors.textSecondary, // Elegant soft slate muted color
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Timer Controls Row containing Reset, Pause, and Start buttons styled as ultra-minimalist circles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. Reset Button
                    FilledIconButton(
                        onClick = onResetTimer,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = FFColors.surfaceAlt,
                            contentColor = FFColors.textSecondary
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, FFColors.border, CircleShape)
                            .testTag("timer_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Timer",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 2. Pause Button (Active when timer is running)
                    FilledIconButton(
                        onClick = onPause,
                        enabled = isTimerRunning,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = FFColors.surfaceAlt,
                            contentColor = FFColors.textPrimary,
                            disabledContainerColor = FFColors.bg.copy(alpha = 0.5f),
                            disabledContentColor = FFColors.textDisabled
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .border(
                                1.dp,
                                if (isTimerRunning) FFColors.border else FFColors.borderSubtle,
                                CircleShape
                            )
                            .testTag("timer_pause_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause Session",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 3. Start Button (Active when timer is paused/stopped)
                    FilledIconButton(
                        onClick = onResume,
                        enabled = !isTimerRunning,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = arcColor, // Use animated focus/break accent color!
                            contentColor = if (FFColors.isDark) Color.Black else Color.White,
                            disabledContainerColor = FFColors.surfaceAlt,
                            disabledContentColor = FFColors.textDisabled
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .border(
                                1.dp,
                                if (!isTimerRunning) arcColor else FFColors.borderSubtle,
                                CircleShape
                            )
                            .testTag("timer_start_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Session",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // --- BOTTOM SECTION: Companion Apps & Hold-to-Exit ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val matchedApps = remember(allowedAppsList) {
                    val pm = context.packageManager
                    allowedAppsList.mapNotNull { pkg ->
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val name = pm.getApplicationLabel(appInfo).toString()
                            AppInfo(
                                packageName = pkg,
                                activityName = "",
                                label = name,
                                customLabel = "",
                                isHidden = false,
                                isFavorite = false
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                if (matchedApps.isNotEmpty()) {
                    Text(
                        text = "COMPANION APPS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = FFColors.textMuted,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        items(matchedApps) { app ->
                            Box(
                                modifier = Modifier
                                    .glassmorphic(shape = RoundedCornerShape(20.dp), borderWidth = 1.dp, hasShadow = false)
                                    .border(1.dp, GlassmorphismStyle.getGlassBorderBrush(), RoundedCornerShape(20.dp))
                                    .clickable {
                                        try {
                                            val activity = context.findActivity()
                                            try {
                                                activity?.stopLockTask()
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                            
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                Toast.makeText(context, "Cannot open: ${app.label}", Toast.LENGTH_SHORT).show()
                                                try {
                                                    activity?.startLockTask()
                                                } catch (e: Throwable) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error launching app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            try {
                                                context.findActivity()?.startLockTask()
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AppIconImage(
                                        packageName = app.packageName,
                                        modifier = Modifier.size(companionIconSize)
                                    )
                                    Text(
                                        text = app.label,
                                        fontSize = (11 * baseScale).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FFColors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Sleek, ultra-minimalist hold-to-unlock gesture target bar
                val holdBorderBrush = if (holdProgress > 0f) {
                    SolidColor(FFColors.red.copy(alpha = holdProgress))
                } else {
                    GlassmorphismStyle.getGlassBorderBrush()
                }
                val holdBgColor = if (holdProgress > 0f) FFColors.red.copy(alpha = 0.1f * holdProgress) else GlassmorphismStyle.getGlassBackgroundColor()
                val holdTextColor = if (holdProgress > 0f) FFColors.red else FFColors.textSecondary

                Box(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .glassmorphic(shape = RoundedCornerShape(22.dp), borderWidth = 1.dp, hasShadow = false)
                        .background(holdBgColor, RoundedCornerShape(22.dp))
                        .border(1.dp, holdBorderBrush, RoundedCornerShape(22.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        isHolding = false
                                    }
                                }
                            )
                        }
                        .testTag("hold_to_exit_button"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Progress fill
                    if (holdProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(holdProgress)
                                .background(FFColors.red.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock icon",
                            tint = if (holdProgress > 0f) FFColors.red else FFColors.textDisabled,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (holdProgress > 0f) {
                                "RELEASING... ${(holdProgress * 100).toInt()}%"
                            } else {
                                "HOLD TO EXIT"
                            },
                            color = holdTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }
        } // End of BoxWithConstraints

        // --- Bottom Sheet Mock for Swipe/Back press ---
        if (showExitSheet) {
            AlertDialog(
                onDismissRequest = { showExitSheet = false },
                modifier = Modifier.testTag("exit_alert_dialog"),
                containerColor = FFColors.surface, // Colors.surface
                title = {
                    Text(
                        text = "Pause Session?",
                        color = FFColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Ending the focus session early will log this session as incomplete.",
                            color = FFColors.textSecondary,
                            fontSize = 14.sp
                        )
                        if (isLockRestrictEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "LOCK RESTRICT MODE ACTIVE\nTo exit, type \"UNPLUG\" below to confirm intentionality.",
                                color = FFColors.orange, // Orange
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = challengeInput,
                                onValueChange = { challengeInput = it },
                                singleLine = true,
                                placeholder = { Text("Type UNPLUG here", color = FFColors.textDisabled) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = FFColors.textPrimary,
                                    unfocusedTextColor = FFColors.textPrimary,
                                    focusedBorderColor = FFColors.orange,
                                    unfocusedBorderColor = FFColors.border
                                )
                            )
                            if (countdownSec > 0) {
                                Text(
                                    text = "Lock restriction active for another $countdownSec second(s)...",
                                    color = FFColors.red, // Red
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    val isChallengePassed = !isLockRestrictEnabled || (challengeInput.trim().equals("UNPLUG", ignoreCase = true) && countdownSec == 0)
                    Button(
                        onClick = {
                            if (isChallengePassed) {
                                showExitSheet = false
                                onToggleStatusBar(true)
                                onEnd(false) // Trigger premature finish
                            }
                        },
                        enabled = isChallengePassed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FFColors.red,
                            disabledContainerColor = FFColors.red.copy(alpha = 0.2f)
                        )
                    ) {
                        val btnText = when {
                            !isLockRestrictEnabled -> "End Session"
                            countdownSec > 0 -> "Locks Hold ($countdownSec s)"
                            !challengeInput.trim().equals("UNPLUG", ignoreCase = true) -> "Type UNPLUG"
                            else -> "End Session"
                        }
                        Text(btnText, color = if (isChallengePassed) Color.White else FFColors.textDisabled)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitSheet = false }
                    ) {
                        Text("Keep Going", color = FFColors.textPrimary)
                    }
                }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

