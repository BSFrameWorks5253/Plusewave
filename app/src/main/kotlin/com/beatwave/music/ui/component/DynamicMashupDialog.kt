/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.runtime.Composable
import com.beatwave.music.models.MediaMetadata

/**
 * Backward compatibility forwarder. All DJ functionality has been upgraded
 * to the new professional [DjConsoleDialog].
 */
@Composable
fun DynamicMashupDialog(
    initialSong: MediaMetadata,
    onDismiss: () -> Unit,
) {
    DjConsoleDialog(
        initialSong = initialSong,
        onDismiss = onDismiss,
    )
}
