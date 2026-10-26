/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.beatwave.music.R

import androidx.compose.material3.LocalContentColor
import com.beatwave.music.ui.theme.AppleTokens
import com.beatwave.music.ui.theme.LocalAccentTextColor
import androidx.compose.ui.graphics.Color

@Composable
fun NavigationTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onPlayAllClick: (() -> Unit)? = null,
) {
    val titleColor = color ?: MaterialTheme.colorScheme.onBackground
    val actionColor = color ?: MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(horizontal = AppleTokens.Gutter, vertical = 14.dp)
    ) {
        thumbnail?.invoke()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                label?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = titleColor.copy(alpha = 0.65f),
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = title,
                    fontSize = AppleTokens.SectionHeader,
                    lineHeight = AppleTokens.SectionHeaderLineHeight,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                    color = titleColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }

        val action = onPlayAllClick ?: onClick
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(actionColor.copy(alpha = 0.12f))
                    .border(0.5.dp, actionColor.copy(alpha = 0.28f), androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = action)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                if (onPlayAllClick != null) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = stringResource(
                        if (onPlayAllClick != null) R.string.play_all else R.string.see_all
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = actionColor,
                    maxLines = 1,
                )
            }
        }
    }
}
