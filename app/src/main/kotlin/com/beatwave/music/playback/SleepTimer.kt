/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
) : Player.Listener {
    private var sleepTimerJob: Job? = null
    var triggerTime by mutableLongStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    val isActive: Boolean
        get() = triggerTime != -1L || pauseWhenSongEnd

    fun start(minute: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minute == -1) {
            pauseWhenSongEnd = true
            triggerTime = -1L
        } else {
            pauseWhenSongEnd = false
            triggerTime = System.currentTimeMillis() + minute.minutes.inWholeMilliseconds
            sleepTimerJob =
                scope.launch {
                    val totalDurationMs = minute.minutes.inWholeMilliseconds
                    val fadeDurationMs = 60_000L.coerceAtMost(totalDurationMs / 2)
                    val preFadeMs = totalDurationMs - fadeDurationMs

                    if (preFadeMs > 0) {
                        delay(preFadeMs)
                    }

                    // Smart Gentle Volume Fade-Out: smoothly dims down volume over the final 60 seconds
                    val steps = 30
                    val stepDelayMs = fadeDurationMs / steps
                    val originalVolume = player.volume
                    for (step in 1..steps) {
                        val fraction = 1.0f - (step.toFloat() / steps.toFloat())
                        player.volume = (originalVolume * fraction).coerceIn(0f, 1f)
                        delay(stepDelayMs)
                    }

                    player.pause()
                    player.volume = originalVolume // Restore volume for next session
                    triggerTime = -1L
                }
        }
    }

    /**
     * Notify the sleep timer that a song transition has occurred outside of normal
     * player callbacks (e.g. during crossfade player swap). If "end of song" mode
     * is active, this will pause the player and deactivate the timer.
     */
    fun notifySongTransition() {
        if (pauseWhenSongEnd) {
            pauseWhenSongEnd = false
            player.pause()
        }
    }

    fun clear() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenSongEnd = false
        triggerTime = -1L
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (pauseWhenSongEnd) {
            pauseWhenSongEnd = false
            player.pause()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == Player.STATE_ENDED && pauseWhenSongEnd) {
            pauseWhenSongEnd = false
            player.pause()
        }
    }
}
