package com.example.launcher

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay

class GuidedTourState {
    var activeStep by mutableStateOf<Int?>(null)
    val elementBounds = mutableStateMapOf<Int, Rect>()
}

fun Modifier.tourElement(
    step: Int,
    tourState: GuidedTourState
): Modifier = this.composed {
    this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            tourState.elementBounds[step] = Rect(position, size.toSize())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourHighlight(
    step: Int,
    tourState: GuidedTourState?,
    isActive: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp)
): Modifier = this.composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isActive) {
        if (isActive) {
            delay(350)
            try {
                bringIntoViewRequester.bringIntoView()
            } catch (e: Exception) {
                // Ignore if not attached or failed
            }
        }
    }

    var modifier = this.bringIntoViewRequester(bringIntoViewRequester)
    if (tourState != null) {
        modifier = modifier.tourElement(step, tourState)
    }
    if (isActive) {
        modifier = modifier.tutorialHighlight(isActive = true, shape = shape)
    }
    modifier
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GuidedTourOverlay(
    tourState: GuidedTourState,
    onStepChanged: (Int?) -> Unit,
    onDismiss: () -> Unit,
    currentTab: FocusTab,
    onTabChange: (FocusTab) -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenGoalSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val activeStep = tourState.activeStep ?: return
    val density = LocalDensity.current

    val selectedSections = remember {
        mutableStateMapOf(
            1 to true,
            2 to true,
            3 to true,
            4 to true
        )
    }

    // Setup steps detail
    val title = when (activeStep) {
        0 -> "Focus Launcher"
        1 -> "Focus Meter"
        2 -> "Focus Timer"
        3 -> "Apps & Tools"
        4 -> "Focus Shield"
        else -> "You're All Set"
    }

    val description = when (activeStep) {
        0 -> "A simple, interactive guide to master your new distraction-free workspace."
        1 -> "Tracks your completed focus minutes against your customized daily target."
        2 -> "Start deep-focus sprints that shield you from distracting notifications."
        3 -> "Access your essential apps. Non-whitelisted apps are hidden during active focus."
        4 -> "Customize extreme protection modes and lock down bypass settings."
        else -> "You're ready to enter high-productivity mode and eliminate digital noise."
    }

    val buttonText = when (activeStep) {
        0 -> "Start Tour"
        5 -> "Get Started"
        else -> "Continue"
    }

    // Ensure we are on the correct tab for each highlighted step
    LaunchedEffect(activeStep) {
        when (activeStep) {
            1, 3 -> {
                if (currentTab != FocusTab.HOME) {
                    onTabChange(FocusTab.HOME)
                    delay(250)
                }
            }
            2 -> {
                if (currentTab != FocusTab.TIMER) {
                    onTabChange(FocusTab.TIMER)
                    delay(250)
                }
            }
            4 -> {
                if (currentTab != FocusTab.PROFILE) {
                    onTabChange(FocusTab.PROFILE)
                    delay(250)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true, onClick = { /* Consumes clicks to background */ })
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val bounds = tourState.elementBounds[activeStep]

        // Keep track of the last non-null bounds to smooth out transition from null state
        var lastNonNullBounds by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(bounds) {
            if (bounds != null) {
                lastNonNullBounds = bounds
            }
        }

        val currentBounds = bounds ?: lastNonNullBounds
        val hasBounds = currentBounds != null && activeStep in 1..4

        val targetLeft = if (hasBounds) currentBounds!!.left else (screenWidthPx / 2f) - 100f
        val targetTop = if (hasBounds) currentBounds!!.top else (screenHeightPx / 2f) - 100f
        val targetWidth = if (hasBounds) currentBounds!!.width else 200f
        val targetHeight = if (hasBounds) currentBounds!!.height else 200f
        val targetAlpha = if (bounds != null && activeStep in 1..4) 1f else 0f

        // Smooth animations for the highlighted bounds to prevent any glitching, jumping, or popping
        val animLeft by animateFloatAsState(
            targetValue = targetLeft,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "animLeft"
        )
        val animTop by animateFloatAsState(
            targetValue = targetTop,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "animTop"
        )
        val animWidth by animateFloatAsState(
            targetValue = targetWidth,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "animWidth"
        )
        val animHeight by animateFloatAsState(
            targetValue = targetHeight,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "animHeight"
        )
        val animAlpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(350, easing = LinearOutSlowInEasing),
            label = "animAlpha"
        )

        // 1. Semi-transparent black background with clear cutout for highlighted element
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // Required for BlendMode.Clear
                .pointerInput(Unit) {
                    detectTapGestures {
                        // Consumes taps so they don't fall through, but does not advance the step
                    }
                }
        ) {
            // Dark elegant overlay
            drawRect(color = Color.Black.copy(alpha = 0.65f))

            if (animAlpha > 0.01f) {
                // Clear the transparent cutout area over the component smoothly
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(animLeft, animTop),
                    size = androidx.compose.ui.geometry.Size(animWidth, animHeight),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        // 2. Beautiful glowing animated outline border around the cutout
        if (animAlpha > 0.01f) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            val leftDp = with(density) { animLeft.toDp() }
            val topDp = with(density) { animTop.toDp() }
            val widthDp = with(density) { animWidth.toDp() }
            val heightDp = with(density) { animHeight.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .graphicsLayer(alpha = animAlpha)
                    .border(
                        width = 1.5.dp,
                        color = FFColors.orange.copy(alpha = pulseAlpha),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }

        // 3. Intelligently positioned tooltip card with a smooth sliding offset transition
        val isBottomHalf = if (currentBounds != null) {
            currentBounds.center.y > screenHeightPx / 2f
        } else {
            false
        }

        val targetCardYOffset = if (activeStep in 1..4) {
            if (isBottomHalf) {
                // Highlight is in the bottom half, place the card upwards
                -130.dp
            } else {
                // Highlight is in the top half, place the card downwards
                130.dp
            }
        } else {
            0.dp
        }

        val animatedYOffset by animateDpAsState(
            targetValue = targetCardYOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "cardYOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = animatedYOffset),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF121214).copy(alpha = 0.95f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF27272A)),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .graphicsLayer {
                            shadowElevation = 8.dp.toPx()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeStep in 1..4) {
                                val totalSelected = selectedSections.values.count { it }
                                val currentSelectedNumber = selectedSections.keys.filter { selectedSections[it] == true }.sorted().indexOf(activeStep) + 1
                                Text(
                                    text = "0$currentSelectedNumber  ·  0$totalSelected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FFColors.orange,
                                    letterSpacing = 1.sp
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = FFColors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Crossfade animation makes transition between steps super premium and smooth
                        Crossfade(
                            targetState = activeStep,
                            animationSpec = tween(300),
                            label = "tour_content"
                        ) { step ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when (step) {
                                        0 -> "Focus Launcher"
                                        1 -> "Focus Meter"
                                        2 -> "Focus Timer"
                                        3 -> "Apps & Tools"
                                        4 -> "Focus Shield"
                                        else -> "You're All Set"
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FFColors.textPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = when (step) {
                                        0 -> "A simple, interactive guide to master your new distraction-free workspace."
                                        1 -> "Tracks your completed focus minutes against your customized daily target."
                                        2 -> "Start deep-focus sprints that shield you from distracting notifications."
                                        3 -> "Access your essential apps. Non-whitelisted apps are hidden during active focus."
                                        4 -> "Customize extreme protection modes and lock down bypass settings."
                                        else -> "You're ready to enter high-productivity mode and eliminate digital noise."
                                    },
                                    fontSize = 13.sp,
                                    color = FFColors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                if (step == 0) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "CHOOSE SECTIONS TO TOUR:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FFColors.orange,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val tourSections = listOf(
                                        1 to "Focus Meter (Target Tracking)",
                                        2 to "Focus Timer (Deep Work Sprints)",
                                        3 to "Apps & Tools (App Whitelist)",
                                        4 to "Focus Shield (Extreme Protection)"
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        tourSections.forEach { (index, label) ->
                                            val isSelected = selectedSections[index] ?: true
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = if (isSelected) Color(0xFF1E1E22) else Color.Transparent,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) FFColors.orange.copy(alpha = 0.5f) else Color(0xFF27272A),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        selectedSections[index] = !isSelected
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { selectedSections[index] = it },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = FFColors.orange,
                                                        uncheckedColor = FFColors.textMuted,
                                                        checkmarkColor = Color.White
                                                    ),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) FFColors.textPrimary else FFColors.textMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Bottom row containing progress indicator and navigation buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeStep in 1..4) {
                                // Monospace-like minimalist dots indicator for steps
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    (1..4).forEach { stepIndex ->
                                        if (selectedSections[stepIndex] == true) {
                                            val isCurrent = stepIndex == activeStep
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 2.dp)
                                                    .size(width = if (isCurrent) 12.dp else 6.dp, height = 6.dp)
                                                    .background(
                                                        color = if (isCurrent) FFColors.orange else FFColors.border,
                                                        shape = RoundedCornerShape(3.dp)
                                                    )
                                                    .clickable {
                                                        tourState.activeStep = stepIndex
                                                        onStepChanged(stepIndex)
                                                    }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (activeStep > 0) {
                                    TextButton(
                                        onClick = {
                                            var prevStep = activeStep - 1
                                            while (prevStep in 1..4 && selectedSections[prevStep] != true) {
                                                prevStep--
                                            }
                                            tourState.activeStep = prevStep
                                            onStepChanged(prevStep)
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Back", color = FFColors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                } else {
                                    TextButton(
                                        onClick = onDismiss,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Skip", color = FFColors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Button(
                                    onClick = {
                                        var nextStep = activeStep + 1
                                        while (nextStep in 1..4 && selectedSections[nextStep] != true) {
                                            nextStep++
                                        }
                                        if (nextStep <= 5) {
                                            tourState.activeStep = nextStep
                                            onStepChanged(nextStep)
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FFColors.orange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(100.dp), // Premium pill button
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
