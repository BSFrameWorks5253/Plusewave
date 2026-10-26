package com.beatwave.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatwave.music.ui.theme.AppleTokens
import com.beatwave.music.ui.theme.LocalAccentColor

/**
 * Authentic, flat 2D Adobe Creative Cloud style "Pw" squircle app logo.
 * Dynamically changes and animates with the app's active UI color and Day/Night mode.
 */
@Composable
fun PulseWaveLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    accentColor: Color = LocalAccentColor.current,
    isDark: Boolean = MaterialTheme.colorScheme.background != AppleTokens.LightBg
) {
    val animSpec = spring<Color>(dampingRatio = 0.85f, stiffness = 300f)

    val targetBg = if (isDark) Color(0xFF181825) else Color(0xFFF1F5F9)
    val targetBorder = if (isDark) accentColor.copy(alpha = 0.85f) else accentColor.copy(alpha = 0.75f)
    val targetText = accentColor

    val animatedBg by animateColorAsState(targetBg, animationSpec = animSpec, label = "logoBg")
    val animatedBorder by animateColorAsState(targetBorder, animationSpec = animSpec, label = "logoBorder")
    val animatedText by animateColorAsState(targetText, animationSpec = animSpec, label = "logoText")

    val cornerRadius = size * 0.22f
    val borderWidth = (size * 0.045f).coerceAtLeast(1.5.dp)
    val fontSize = (size.value * 0.44f).sp
    val smallFontSize = (size.value * 0.38f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(animatedBg)
            .border(borderWidth, animatedBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.offset(y = (-size * 0.02f))
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
    }
}
