/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Playful, ultra-cute day/night theme toggle with smiling sun & sleeping crescent moon,
 * floating clouds, twinkling stars, and bouncy spring physics.
 */
@Composable
fun CuteThemeSwitch(
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
    height: Dp = 48.dp,
) {
    val haptic = LocalHapticFeedback.current

    // Spring animation for knob travel
    val thumbOffsetProgress by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f, // Cute bouncy overshoot
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "thumbOffset"
    )

    // Squish & stretch on movement
    val knobSquishX by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "knobSquish"
    )

    // Sun spin when switching to light mode
    val sunRotation by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "sunRotation"
    )

    // Continuous twinkling for stars in dark mode
    val infiniteTransition = rememberInfiniteTransition(label = "twinkle")
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starAlpha"
    )
    val cloudBob by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloudBob"
    )

    // Track gradient (Day sky -> Night deep twilight)
    val daySkyColors = listOf(Color(0xFF38BDF8), Color(0xFF60A5FA), Color(0xFF93C5FD))
    val nightSkyColors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF312E81))

    val trackGradient = if (isDark) {
        Brush.horizontalGradient(nightSkyColors)
    } else {
        Brush.horizontalGradient(daySkyColors)
    }

    val borderColor by animateColorAsState(
        targetValue = if (isDark) Color(0x33A5B4FC) else Color(0x40BAE6FD),
        animationSpec = tween(350),
        label = "borderColor"
    )

    val knobSize = height - 8.dp
    val maxTravel = width - knobSize - 8.dp

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackGradient)
            .border(1.5.dp, borderColor, RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isDark)
            }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Background Details (Stars for Night, Clouds for Day)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            if (thumbOffsetProgress > 0.1f) {
                // Twinkling cute stars in Dark Mode
                val starOpacity = starAlpha * thumbOffsetProgress
                val starColor = Color(0xFFFEF08A).copy(alpha = starOpacity)

                // Star 1 (Small cross star)
                drawCircle(color = starColor, radius = 2.5f, center = Offset(canvasW * 0.22f, canvasH * 0.32f))
                // Star 2 (Tiny sparkle)
                drawCircle(color = starColor, radius = 1.8f, center = Offset(canvasW * 0.38f, canvasH * 0.72f))
                // Star 3 (Diamond star)
                drawCircle(color = starColor, radius = 3.2f, center = Offset(canvasW * 0.28f, canvasH * 0.52f))
                // Star 4
                drawCircle(color = starColor, radius = 1.5f, center = Offset(canvasW * 0.14f, canvasH * 0.68f))
            }

            if (thumbOffsetProgress < 0.9f) {
                // Fluffy cute cloud in Light Mode
                val cloudOpacity = (1f - thumbOffsetProgress)
                val cloudColor = Color.White.copy(alpha = cloudOpacity * 0.85f)
                val cloudY = canvasH * 0.68f + cloudBob

                // Cloud puffs
                drawCircle(color = cloudColor, radius = 9f, center = Offset(canvasW * 0.74f, cloudY))
                drawCircle(color = cloudColor, radius = 13f, center = Offset(canvasW * 0.83f, cloudY - 3f))
                drawCircle(color = cloudColor, radius = 9f, center = Offset(canvasW * 0.91f, cloudY))
            }
        }

        // Bouncy Sliding Celestial Knob (Sun <-> Moon)
        val currentTravelPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxTravel * thumbOffsetProgress).toPx()
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(currentTravelPx.roundToInt(), 0) }
                .size(knobSize)
                .scale(scaleX = knobSquishX, scaleY = 1f)
                .shadow(6.dp, CircleShape, spotColor = if (isDark) Color(0xFF818CF8) else Color(0xFFF59E0B))
                .clip(CircleShape)
                .background(
                    if (isDark) {
                        Brush.radialGradient(listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color(0xFFEAB308)))
                    } else {
                        Brush.radialGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFDE047), Color(0xFFF59E0B)))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Cutie Face & Features on Sun/Moon
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(sunRotation)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val r = size.width / 2f

                if (thumbOffsetProgress < 0.5f) {
                    // --- CUTE SMILING SUN (Day) ---
                    // Golden sunbeams around perimeter
                    val beamCount = 8
                    for (i in 0 until beamCount) {
                        val angle = (i * (360f / beamCount)) * (Math.PI / 180f)
                        val startX = (center.x + (r - 2f) * cos(angle)).toFloat()
                        val startY = (center.y + (r - 2f) * sin(angle)).toFloat()
                        val endX = (center.x + (r + 4f) * cos(angle)).toFloat()
                        val endY = (center.y + (r + 4f) * sin(angle)).toFloat()
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Cute smiling eyes (two happy arcs ^ ^)
                    val eyeY = center.y - 3f
                    val leftEyeX = center.x - 6f
                    val rightEyeX = center.x + 6f

                    // Left eye arc
                    drawArc(
                        color = Color(0xFF78350F),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(leftEyeX - 3.5f, eyeY - 3f),
                        size = androidx.compose.ui.geometry.Size(7f, 6f),
                        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
                    )
                    // Right eye arc
                    drawArc(
                        color = Color(0xFF78350F),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(rightEyeX - 3.5f, eyeY - 3f),
                        size = androidx.compose.ui.geometry.Size(7f, 6f),
                        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
                    )

                    // Rosy blushing cheeks (two soft pink dots)
                    drawCircle(color = Color(0xFFF43F5E).copy(alpha = 0.5f), radius = 2.5f, center = Offset(leftEyeX - 2f, eyeY + 5f))
                    drawCircle(color = Color(0xFFF43F5E).copy(alpha = 0.5f), radius = 2.5f, center = Offset(rightEyeX + 2f, eyeY + 5f))

                    // Cute happy smile :D
                    drawArc(
                        color = Color(0xFF78350F),
                        startAngle = 10f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(center.x - 4f, center.y + 1f),
                        size = androidx.compose.ui.geometry.Size(8f, 6f),
                        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
                    )
                } else {
                    // --- CUTE SLEEPING CRESCENT MOON (Night) ---
                    // Crescent mask shadow (indents the circle into a cute moon)
                    val shadowCenter = Offset(center.x - 7f, center.y - 4f)
                    drawCircle(
                        color = Color(0xFF1E1B4B).copy(alpha = 0.45f),
                        radius = r * 0.85f,
                        center = shadowCenter
                    )

                    // Sleepy closed eye (gentle smiling curve U)
                    val eyeCenter = Offset(center.x + 3.5f, center.y - 2f)
                    drawArc(
                        color = Color(0xFF713F12),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(eyeCenter.x - 3.5f, eyeCenter.y - 2f),
                        size = androidx.compose.ui.geometry.Size(7f, 5f),
                        style = Stroke(width = 2f, cap = StrokeCap.Round)
                    )

                    // Cute blush on the sleepy moon cheek
                    drawCircle(color = Color(0xFFFB7185).copy(alpha = 0.5f), radius = 2.8f, center = Offset(eyeCenter.x + 1f, eyeCenter.y + 6f))
                }
            }
        }
    }
}
