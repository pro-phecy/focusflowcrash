package com.example.launcher

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Reusable premium glassmorphism styling parameters & modifiers.
 */
object GlassmorphismStyle {
    
    @Composable
    fun getGlassBackgroundColor(): Color {
        return if (FFColors.isDark) {
            Color(0x1F1E1E24) // 12% opacity dark slate
        } else {
            Color(0xBFFFFFFF) // 75% opacity clean white
        }
    }

    @Composable
    fun getGlassBorderBrush(): Brush {
        return if (FFColors.isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f), // specular reflection highlight
                    Color.White.copy(alpha = 0.03f)  // fading edge
                ),
                start = Offset(0f, 0f),
                end = Offset(100f, 300f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.08f),
                    Color.Black.copy(alpha = 0.02f)
                ),
                start = Offset(0f, 0f),
                end = Offset(100f, 300f)
            )
        }
    }
}

/**
 * Custom Modifier for adding premium glassmorphism effects:
 * - Semi-transparent background
 * - Specular gradient border
 * - Crisply renders all child elements without any blur to ensure readability.
 */
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    hasShadow: Boolean = true
): Modifier = this.then(
    Modifier
        // Elegant subtle shadow to give depth and separation
        .let { modifier ->
            if (hasShadow) {
                modifier.shadow(
                    elevation = 10.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
            } else {
                modifier
            }
        }
)

/**
 * A stunning ambient background with soft, glowing, slow-drifting circular gradient blobs.
 * Since they are placed behind glassmorphic components, they create a gorgeous colored glass refraction.
 */
@Composable
fun AmbientGlowingBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = FFColors.isDark
    
    val baseBgColor = if (isDark) Color(0xFF09090B) else Color(0xFFFAFAFA)
    
    // Dynamic soft colors for glowing background blobs
    val glowColor1 = if (isDark) Color(0x33F97316) else Color(0x11F97316) // Soft ambient Orange
    val glowColor2 = if (isDark) Color(0x223B82F6) else Color(0x0C3B82F6) // Soft ambient Blue
    val glowColor3 = if (isDark) Color(0x1FA855F7) else Color(0x08A855F7) // Soft ambient Purple

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
            .drawBehind {
                // Top-right soft orange glowing light
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor1, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.7f
                )

                // Mid-left soft blue glowing light
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor2, Color.Transparent),
                        center = Offset(size.width * -0.1f, size.height * 0.55f),
                        radius = size.width * 0.8f
                    ),
                    center = Offset(size.width * -0.1f, size.height * 0.55f),
                    radius = size.width * 0.8f
                )

                // Bottom-right soft purple glowing light
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor3, Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.85f),
                        radius = size.width * 0.65f
                    ),
                    center = Offset(size.width * 0.9f, size.height * 0.85f),
                    radius = size.width * 0.65f
                )
            }
    ) {
        content()
    }
}

/**
 * A beautiful premium Card styled explicitly with Glassmorphic properties.
 * The background is rendered in a sibling layer, and content is drawn perfectly sharp on top.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    hasShadow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .glassmorphic(shape = shape, borderWidth = borderWidth, hasShadow = hasShadow)
            .border(borderWidth, GlassmorphismStyle.getGlassBorderBrush(), shape)
            .clip(shape),
        colors = CardDefaults.cardColors(
            containerColor = GlassmorphismStyle.getGlassBackgroundColor()
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Enum for defining direction of staggering entrance transitions.
 */
enum class EntranceDirection {
    TOP, BOTTOM, LEFT, RIGHT, RANDOM
}

/**
 * A beautiful premium staggered entrance animator.
 * Fades in and slides in from a specified or randomized direction offset.
 */
@Composable
fun AnimatedEntrance(
    delayMillis: Int,
    durationMillis: Int = 600,
    direction: EntranceDirection = EntranceDirection.RANDOM,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }

    val stableDirection = remember {
        if (direction == EntranceDirection.RANDOM) {
            // Randomly choose from TOP, BOTTOM, LEFT, RIGHT
            val candidates = EntranceDirection.values().filter { it != EntranceDirection.RANDOM }
            candidates.random()
        } else {
            direction
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = EaseOutQuart),
        label = "entrance_alpha"
    )

    // Premium offset distance
    val offsetDistance = 32.dp

    val translationXState by animateDpAsState(
        targetValue = if (visible) 0.dp else {
            when (stableDirection) {
                EntranceDirection.LEFT -> -offsetDistance
                EntranceDirection.RIGHT -> offsetDistance
                else -> 0.dp
            }
        },
        animationSpec = tween(durationMillis = durationMillis, easing = EaseOutQuart),
        label = "entrance_offset_x"
    )

    val translationYState by animateDpAsState(
        targetValue = if (visible) 0.dp else {
            when (stableDirection) {
                EntranceDirection.TOP -> -offsetDistance
                EntranceDirection.BOTTOM -> offsetDistance
                else -> 0.dp
            }
        },
        animationSpec = tween(durationMillis = durationMillis, easing = EaseOutQuart),
        label = "entrance_offset_y"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.translationX = translationXState.toPx()
                this.translationY = translationYState.toPx()
            }
    ) {
        content()
    }
}

