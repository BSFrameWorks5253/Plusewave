/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic Reactive 3-Stop Mesh Gradient:
 * Renders an organic, continuously breathing animated multi-point mesh gradient
 * derived from the current album artwork palette.
 *
 * When audio is playing, the mesh subtly pulses and ripples in sync with the rhythm,
 * simulating acoustic air displacement behind frosted liquid glass surfaces.
 */
@Composable
fun ReactiveMeshGradient(
    primaryColor: Color,
    secondaryColor: Color = primaryColor,
    tertiaryColor: Color = Color.Unspecified,
    isPlaying: Boolean = false,
    bassPulse: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_gradient_anim")

    // Slow ambient orbits for 3 gradient focal nodes
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Breathing rhythm when playing
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 2200 else 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // Base background color
    val baseDark = MaterialTheme.colorScheme.surface
    val c1 = if (primaryColor != Color.Unspecified) primaryColor else MaterialTheme.colorScheme.primary
    val c2 = if (secondaryColor != Color.Unspecified && secondaryColor != primaryColor) {
        secondaryColor
    } else {
        c1.copy(alpha = 0.7f)
    }
    val c3 = if (tertiaryColor != Color.Unspecified) tertiaryColor else c2.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseDark)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.9f }
        ) {
            val w = size.width
            val h = size.height

            // Calculate animated node positions
            val rad1 = Math.toRadians(phase1.toDouble())
            val rad2 = Math.toRadians(phase2.toDouble())
            val rad3 = Math.toRadians(phase3.toDouble())

            val dynamicPulse = if (isPlaying) (breathScale + bassPulse * 0.15f) else 1f

            // Node 1: Primary top-center / upper quadrant orbit
            val n1X = (w * 0.45f) + (cos(rad1).toFloat() * w * 0.25f)
            val n1Y = (h * 0.35f) + (sin(rad1).toFloat() * h * 0.15f)
            val n1Radius = (w * 0.75f) * dynamicPulse

            // Node 2: Secondary right / lower quadrant orbit
            val n2X = (w * 0.65f) + (cos(rad2).toFloat() * w * 0.20f)
            val n2Y = (h * 0.65f) + (sin(rad2).toFloat() * h * 0.18f)
            val n2Radius = (w * 0.85f) * dynamicPulse

            // Node 3: Tertiary left / lower quadrant orbit
            val n3X = (w * 0.25f) + (cos(rad3).toFloat() * w * 0.18f)
            val n3Y = (h * 0.50f) + (sin(rad3).toFloat() * h * 0.20f)
            val n3Radius = (w * 0.65f) * dynamicPulse

            // Draw Node 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = 0.75f), c1.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(n1X, n1Y),
                    radius = n1Radius
                ),
                radius = n1Radius,
                center = Offset(n1X, n1Y),
                blendMode = BlendMode.Screen
            )

            // Draw Node 2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c2.copy(alpha = 0.65f), c2.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(n2X, n2Y),
                    radius = n2Radius
                ),
                radius = n2Radius,
                center = Offset(n2X, n2Y),
                blendMode = BlendMode.Screen
            )

            // Draw Node 3
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c3.copy(alpha = 0.55f), c3.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(n3X, n3Y),
                    radius = n3Radius
                ),
                radius = n3Radius,
                center = Offset(n3X, n3Y),
                blendMode = BlendMode.Screen
            )

            // Dark vignette overlay ensuring accessibility and crisp contrast for lyrics & controls
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.35f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
        }
    }
}
