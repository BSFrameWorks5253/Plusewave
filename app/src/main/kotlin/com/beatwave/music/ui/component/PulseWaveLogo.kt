package com.beatwave.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatwave.music.ui.theme.AppleTokens
import com.beatwave.music.ui.theme.LocalAccentColor

/**
 * Authentic, flat 2D Adobe Creative Cloud style "Pw" app logo with
 * an integrated precision digital audio waveform baseline.
 * Dynamically adapts and animates with the app's active UI color and Day/Night mode.
 */
@Composable
fun PulseWaveLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    accentColor: Color = LocalAccentColor.current,
    isDark: Boolean = MaterialTheme.colorScheme.background != AppleTokens.LightBg
) {
    val animSpec = spring<Color>(dampingRatio = 0.85f, stiffness = 300f)

    val targetBg = if (isDark) Color(0xFF14141D) else Color(0xFFF1F5F9)
    val targetBorder = if (isDark) accentColor.copy(alpha = 0.85f) else accentColor.copy(alpha = 0.75f)
    val targetText = accentColor
    val targetWave = accentColor.copy(alpha = if (isDark) 0.80f else 0.85f)

    val animatedBg by animateColorAsState(targetBg, animationSpec = animSpec, label = "logoBg")
    val animatedBorder by animateColorAsState(targetBorder, animationSpec = animSpec, label = "logoBorder")
    val animatedText by animateColorAsState(targetText, animationSpec = animSpec, label = "logoText")
    val animatedWave by animateColorAsState(targetWave, animationSpec = animSpec, label = "logoWave")

    val cornerRadius = size * 0.22f
    val borderWidth = (size * 0.035f).coerceAtLeast(1.5.dp)
    val fontSize = (size.value * 0.40f).sp
    val smallFontSize = (size.value * 0.34f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(animatedBg)
            .border(borderWidth, animatedBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = size * 0.12f)
        ) {
            // Authentic Adobe CC Typography "Pw"
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.offset(y = (-size * 0.015f))
            ) {
                Text(
                    text = "P",
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = animatedText,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "w",
                    fontSize = smallFontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = animatedText,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.offset(x = 1.dp, y = (size * 0.015f))
                )
            }

            Spacer(modifier = Modifier.height(size * 0.04f))

            // Precision Audio Waveform Baseline
            val waveStrokeWidth = (size.value * 0.024f).coerceAtLeast(1.2f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(size * 0.12f)
            ) {
                val w = this.size.width
                val h = this.size.height
                val midY = h / 2f

                val path = Path().apply {
                    moveTo(0f, midY)
                    lineTo(w * 0.18f, midY)
                    lineTo(w * 0.26f, midY - h * 0.22f)
                    lineTo(w * 0.34f, midY + h * 0.28f)
                    lineTo(w * 0.42f, midY - h * 0.58f)
                    lineTo(w * 0.50f, midY + h * 0.75f)
                    lineTo(w * 0.58f, midY - h * 0.88f) // Center peak
                    lineTo(w * 0.66f, midY + h * 0.65f)
                    lineTo(w * 0.74f, midY - h * 0.45f)
                    lineTo(w * 0.82f, midY + h * 0.25f)
                    lineTo(w * 0.90f, midY)
                    lineTo(w, midY)
                }

                drawPath(
                    path = path,
                    color = animatedWave,
                    style = Stroke(
                        width = waveStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // End dots
                drawCircle(color = animatedWave, radius = waveStrokeWidth * 1.1f, center = Offset(0f, midY))
                drawCircle(color = animatedWave, radius = waveStrokeWidth * 1.1f, center = Offset(w, midY))
            }
        }
    }
}
