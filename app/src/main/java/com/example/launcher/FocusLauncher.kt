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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    goal: String,
    allowedAppsJson: String,
    isLockRestrictEnabled: Boolean,
    onEnd: (completed: Boolean) -> Unit,
    onToggleStatusBar: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val allowedAppsList = remember(allowedAppsJson) { Converters().toStringList(allowedAppsJson) }

    // Intercept back button to show exit bottom sheet
    var showExitSheet by remember { mutableStateOf(false) }
    var challengeInput by remember { mutableStateOf("") }
    var countdownSec by remember { mutableStateOf(5) }

    LaunchedEffect(showExitSheet) {
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
        showExitSheet = true
    }

    // Observe lifecycle events to hide/lock-task on resume (such as after launching and returning from companion apps)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onToggleStatusBar(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Restore status/navigation bars on exiting Focus mode screen completely
    DisposableEffect(Unit) {
        onDispose {
            onToggleStatusBar(true)
        }
    }

    // Live Clock State
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            currentTime = sdf.format(Date())
            delay(1000)
        }
    }

    // Calculate elapsed ratio for progress circle arc
    val elapsedRatio = if (duration > 0) timeLeft.toFloat() / duration.toFloat() else 1f
    val animatedProgress by animateFloatAsState(targetValue = elapsedRatio, label = "Progress")

    // Gesture control: swipe-down (PanResponder equivalent) threshold ~80dp opens the exit sheet
    var swipeOffsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)) // Colors.bg
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (swipeOffsetY > 150) {
                            showExitSheet = true
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
                .padding(vertical = 48.dp, horizontal = 24.dp),
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
                        .size(240.dp)
                        .padding(24.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()
                        // Secondary track
                        drawCircle(
                            color = Color(0xFF27272A), // Colors.surfaceAlt
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
                    }

                    // Large Digital Text
                    val m = (timeLeft / 60)
                    val s = (timeLeft % 60)
                    val displayTime = String.format(Locale.getDefault(), "%02d:%02d", m, s)

                    Text(
                        text = displayTime,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFFFAFAFA),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Clock underneath
                Text(
                    text = currentTime,
                    fontSize = 16.sp,
                    color = Color(0xFF71717A), // Colors.textMuted
                    fontWeight = FontWeight.Medium
                )
            }

            // --- MIDDLE PANEL: Intention ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Text(
                    text = "INTENTION",
                    fontSize = 11.sp,
                    color = Color(0xFFF97316), // Orange accent
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = goal,
                    fontSize = 24.sp,
                    color = Color(0xFFFAFAFA),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // --- BOTTOM PANEL: Allowed Apps row ---
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
                        text = "ALLOWED COMPANIONS",
                        fontSize = 10.sp,
                        color = Color(0xFF71717A), // Colors.textMuted
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(matchedApps) { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .width(72.dp)
                                    .clickable {
                                        try {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                Toast.makeText(context, "Cannot open: ${app.label}", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error launching app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color(0xFF18181B), CircleShape), // Colors.surface
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIconImage(
                                        packageName = app.packageName,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = app.label,
                                    fontSize = 11.sp,
                                    color = Color(0xFFA1A1AA), // Colors.textSecondary
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No apps allowed. Stay fully focused.",
                        fontSize = 13.sp,
                        color = Color(0xFF71717A)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // Exit Hint Indicator
                Text(
                    text = "Swipe down or press back to exit",
                    fontSize = 11.sp,
                    color = Color(0xFF52525B), // Colors.textDisabled
                    textAlign = TextAlign.Center
                )
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

