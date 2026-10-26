/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback

import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.ui.component.MashupTransitionStyle

/**
 * Backward compatibility alias for [DjConsoleController].
 */
object DynamicMashupController {
    val isActive: Boolean
        get() = DjConsoleController.isActive

    val currentStyle: MashupTransitionStyle
        get() = DjConsoleController.currentStyle

    val tracks: List<MediaMetadata>
        get() = DjConsoleController.deckTracks

    val currentTrackIndex: Int
        get() = DjConsoleController.currentDeckIndex

    val secondsRemaining: Int
        get() = DjConsoleController.secondsRemaining

    val totalSwaps: Int
        get() = DjConsoleController.totalSwaps

    val activeTrack: MediaMetadata?
        get() = DjConsoleController.activeTrack

    val nextTrack: MediaMetadata?
        get() = DjConsoleController.nextTrack

    fun triggerImmediateCut() = DjConsoleController.triggerImmediateCut()

    fun swapToNextTrack() = DjConsoleController.swapToNextDeckTrack()

    fun startMashup(
        selectedTracks: List<MediaMetadata>,
        style: MashupTransitionStyle,
        playerConnection: PlayerConnection
    ) = DjConsoleController.startMashup(selectedTracks, style, playerConnection)

    fun stopMashup() = DjConsoleController.stopDjConsole()
}
