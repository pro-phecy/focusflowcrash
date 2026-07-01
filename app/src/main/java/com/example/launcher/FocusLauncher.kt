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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
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

    val isHudCurrentlyVisible = isHudVisible || showExitSheet
    val hudAlpha by animateFloatAsState(
        targetValue = if (isHudCurrentlyVisible) 1f else 0f,
        animationSpec = if (isHudCurrentlyVisible) androidx.compose.animation.core.tween(300) else androidx.compose.animation.core.tween(1000),
        label = "HUD Fade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)) // Colors.bg
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
            .testTag("focus_launcher_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .graphicsLayer(alpha = hudAlpha)
                .padding(vertical = 24.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP PANEL: Rings & Counter ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Circular Arc Countdown Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(280.dp)
                        .padding(16.dp)
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
                        val strokeWidth = 10.dp.toPx()
                        // Secondary track
                        drawCircle(
                            color = Color(0xFF18181B), // Colors.surface
                            style = Stroke(width = strokeWidth)
                        )
                        // Active Arc
                        drawArc(
                            color = Color(0xFFFAFAFA), // Colors.textPrimary
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Tactile continuous hold-to-exit indicator
                        if (holdProgress > 0f) {
                            drawArc(
                                color = Color(0xFFEF4444), // Crimson focus unlock indicator
                                startAngle = -90f,
                                sweepAngle = holdProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth * 1.5f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Large Digital Text
                    val m = (timeLeft / 60)
                    val s = (timeLeft % 60)
                    val displayTime = String.format(Locale.getDefault(), "%02d:%02d", m, s)

                    Text(
                        text = displayTime,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = Color(0xFFFAFAFA),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Clock underneath
                Text(
                    text = currentTime,
                    fontSize = 14.sp,
                    color = Color(0xFF71717A), // Colors.textMuted
                    fontWeight = FontWeight.Light
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Timer Controls Row containing distinct Reset, Pause, and Start buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                ) {
                    // 1. Reset Button
                    Button(
                        onClick = onResetTimer,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF18181B),
                            contentColor = Color(0xFFFAFAFA)
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .weight(1f)
                            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(12.dp))
                            .testTag("timer_reset_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Timer",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 2. Pause Button (Active when timer is running)
                    Button(
                        onClick = onPause,
                        enabled = isTimerRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF18181B),
                            contentColor = Color(0xFFFAFAFA),
                            disabledContainerColor = Color(0xFF0C0C0D),
                            disabledContentColor = Color(0xFF3F3F46)
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .weight(1f)
                            .border(
                                1.dp,
                                if (isTimerRunning) Color(0xFF3F3F46) else Color(0xFF18181B),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("timer_pause_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause Session",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3. Start Button (Active when timer is paused/stopped)
                    Button(
                        onClick = onResume,
                        enabled = !isTimerRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFAFAFA),
                            contentColor = Color(0xFF09090B),
                            disabledContainerColor = Color(0xFF18181B),
                            disabledContentColor = Color(0xFF52525B)
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .weight(1f)
                            .testTag("timer_start_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Session",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- MIDDLE PANEL: Intention (Subtle & Beautifully Centered Goal, No "INTENTION" Header Label) ---
            if (goal.isNotBlank()) {
                Text(
                    text = goal,
                    fontSize = 26.sp,
                    color = Color(0xFFFAFAFA), // Crisp white text
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
                )
            }

            // --- BOTTOM PANEL: Allowed Companion Apps Icon Row (No Header Label, Empty if none) ---
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        matchedApps.forEach { app ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
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
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = app.label,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = Color(0xFFE4E4E7),
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // A beautiful, highly conspicuous, dedicated Hold-to-Exit bar/button!
                val holdBorderColor = if (holdProgress > 0f) Color(0xFFEF4444) else Color(0xFF27272A)
                val holdBgColor = if (holdProgress > 0f) Color(0x1AEF4444) else Color(0xFF18181B)
                val holdTextColor = if (holdProgress > 0f) Color(0xFFEF4444) else Color(0xFFA1A1AA)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(holdBgColor, RoundedCornerShape(16.dp))
                        .border(1.dp, holdBorderColor, RoundedCornerShape(16.dp))
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
                                .background(Color(0xFFEF4444).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock icon",
                            tint = if (holdProgress > 0f) Color(0xFFEF4444) else Color(0xFF71717A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (holdProgress > 0f) {
                                "RELEASING LOCK... ${(holdProgress * 100).toInt()}%"
                            } else {
                                "PRESS & HOLD TO EXIT SESSION"
                            },
                            color = holdTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // --- Bottom Sheet Mock for Swipe/Back press ---
        if (showExitSheet) {
            AlertDialog(
                onDismissRequest = { showExitSheet = false },
                modifier = Modifier.testTag("exit_alert_dialog"),
                containerColor = Color(0xFF18181B), // Colors.surface
                title = {
                    Text(
                        text = "Pause Session?",
                        color = Color(0xFFFAFAFA),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Ending the focus session early will log this session as incomplete.",
                            color = Color(0xFFA1A1AA),
                            fontSize = 14.sp
                        )
                        if (isLockRestrictEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "LOCK RESTRICT MODE ACTIVE\nTo exit, type \"UNPLUG\" below to confirm intentionality.",
                                color = Color(0xFFF97316), // Orange
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = challengeInput,
                                onValueChange = { challengeInput = it },
                                singleLine = true,
                                placeholder = { Text("Type UNPLUG here", color = Color(0xFF71717A)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFFFAFAFA),
                                    unfocusedTextColor = Color(0xFFFAFAFA),
                                    focusedBorderColor = Color(0xFFF97316),
                                    unfocusedBorderColor = Color(0xFF3F3F46)
                                )
                            )
                            if (countdownSec > 0) {
                                Text(
                                    text = "Lock restriction active for another $countdownSec second(s)...",
                                    color = Color(0xFFEF4444), // Red
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
                            containerColor = Color(0xFFEF4444),
                            disabledContainerColor = Color(0x33EF4444)
                        )
                    ) {
                        val btnText = when {
                            !isLockRestrictEnabled -> "End Session"
                            countdownSec > 0 -> "Locks Hold ($countdownSec s)"
                            !challengeInput.trim().equals("UNPLUG", ignoreCase = true) -> "Type UNPLUG"
                            else -> "End Session"
                        }
                        Text(btnText, color = if (isChallengePassed) Color.White else Color(0xFF737373))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitSheet = false }
                    ) {
                        Text("Keep Going", color = Color(0xFFFAFAFA))
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

