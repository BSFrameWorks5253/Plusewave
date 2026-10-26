/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import com.beatwave.music.extensions.toMediaItem
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.playback.dj.TransitionStyle
import com.beatwave.music.playback.queues.ListQueue
import com.beatwave.music.ui.component.MashupTransitionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controller for the Pro DJ Console.
 * Supports on-demand DJ transitions into the next song (Beat Swap, Vocal Swap, Filter Sweep,
 * Bass Drop, Vinyl Brake, Echo Freeze, Harmonic Blend) as well as continuous multi-deck sets.
 */
object DjConsoleController {
    var isActive by mutableStateOf(false)
        private set

    /** Selected DJ Transition Effect for instant or auto-transitions */
    var selectedEffect by mutableStateOf(TransitionStyle.BEAT_SWAP)

    /** Duration in milliseconds for the DJ transition (e.g. 1500L, 3000L, 5000L) */
    var transitionDurationMs by mutableLongStateOf(3000L)

    /** When enabled, the player automatically uses the selected DJ transition on track end */
    var autoDjOnTrackEnd by mutableStateOf(true)

    /** Interactive crossfader position: -1.0f (Deck A) to 0.0f (Center) to +1.0f (Deck B) */
    var crossfaderPosition by mutableFloatStateOf(0.0f)

    /** Tracks armed in the DJ console decks (Up to 4 decks) */
    var deckTracks by mutableStateOf<List<MediaMetadata>>(emptyList())
        private set

    var currentDeckIndex by mutableIntStateOf(0)
        private set

    var secondsRemaining by mutableIntStateOf(16)
        private set

    var totalSwaps by mutableIntStateOf(0)
        private set

    // Backward-compatibility bridge for old mashup style queries
    var currentStyle: MashupTransitionStyle
        get() = when (selectedEffect) {
            TransitionStyle.VOCAL_SWAP -> MashupTransitionStyle.ACAPELLA_SWAP
            TransitionStyle.BEAT_SWAP -> MashupTransitionStyle.LINE_BY_LINE
            else -> MashupTransitionStyle.VERSE_BY_VERSE
        }
        set(value) {
            selectedEffect = when (value) {
                MashupTransitionStyle.ACAPELLA_SWAP -> TransitionStyle.VOCAL_SWAP
                MashupTransitionStyle.LINE_BY_LINE -> TransitionStyle.BEAT_SWAP
                MashupTransitionStyle.VERSE_BY_VERSE -> TransitionStyle.FILTER_SWEEP
            }
        }

    val tracks: List<MediaMetadata>
        get() = deckTracks

    val activeTrack: MediaMetadata?
        get() = deckTracks.getOrNull(currentDeckIndex)

    val nextTrack: MediaMetadata?
        get() = if (deckTracks.size >= 2) deckTracks.getOrNull((currentDeckIndex + 1) % deckTracks.size) else null

    private val trackPositions = mutableMapOf<Int, Long>()
    private var consoleJob: Job? = null
    private val scope by lazy { CoroutineScope(Dispatchers.Main.immediate) }
    private var playerConnectionRef: PlayerConnection? = null

    fun getIntervalForEffect(effect: TransitionStyle): Int = when (effect) {
        TransitionStyle.BEAT_SWAP -> 12 // 8-16 bars
        TransitionStyle.VOCAL_SWAP -> 16 // Hook/Chorus length
        TransitionStyle.FILTER_SWEEP -> 20 // Full build-up & drop
        TransitionStyle.BASS_DROP -> 16
        TransitionStyle.TAPE_STOP -> 10
        TransitionStyle.ECHO_FREEZE -> 14
        TransitionStyle.TRANSPARENT -> 24
    }

    /**
     * Executes a one-shot live DJ transition into the target track or next queue track.
     */
    fun triggerDjTransition(
        playerConnection: PlayerConnection,
        targetTrack: MediaMetadata? = null,
        effect: TransitionStyle = selectedEffect,
        durationMs: Long = transitionDurationMs,
    ) {
        playerConnectionRef = playerConnection
        val player = playerConnection.player

        // Arm DJ engine
        runCatching {
            playerConnection.service.djEngine.enabled = true
            playerConnection.service.djEngine.creativeEnabled = true
        }

        val queueLength = player.mediaItemCount
        if (queueLength <= 0) return

        val currentIndex = player.currentMediaItemIndex
        val targetIndex = if (targetTrack != null) {
            // Find target track in queue, or if not found, add it next
            var found = -1
            for (i in 0 until queueLength) {
                if (player.getMediaItemAt(i).mediaId == targetTrack.id) {
                    found = i
                    break
                }
            }
            if (found != -1) {
                found
            } else {
                val insertIndex = (currentIndex + 1).coerceAtMost(queueLength)
                player.addMediaItem(insertIndex, targetTrack.toMediaItem())
                insertIndex
            }
        } else {
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) nextIdx else (currentIndex + 1) % queueLength
        }

        if (targetIndex != C.INDEX_UNSET && targetIndex != currentIndex) {
            totalSwaps++
            playerConnection.triggerDjTransitionToTrack(
                targetIndex = targetIndex,
                startPositionMs = 0L,
                durationMs = durationMs,
                style = effect,
            )
        }
    }

    /**
     * Legacy & instant cut trigger for continuous set
     */
    fun triggerImmediateCut() {
        val conn = playerConnectionRef ?: return
        if (deckTracks.size >= 2) {
            swapToNextDeckTrack()
        } else {
            triggerDjTransition(conn)
        }
    }

    /**
     * Starts continuous multi-deck DJ rotation set
     */
    fun startContinuousDjSet(
        selectedTracks: List<MediaMetadata>,
        effect: TransitionStyle,
        playerConnection: PlayerConnection,
    ) {
        if (selectedTracks.size < 2) return
        stopDjConsole()

        runCatching {
            playerConnection.service.djEngine.enabled = true
            playerConnection.service.djEngine.creativeEnabled = true
        }

        playerConnectionRef = playerConnection
        deckTracks = selectedTracks
        selectedEffect = effect
        currentDeckIndex = 0
        totalSwaps = 0
        secondsRemaining = getIntervalForEffect(effect)
        trackPositions.clear()
        selectedTracks.indices.forEach { trackPositions[it] = 0L }
        isActive = true

        val mediaItems = selectedTracks.map { it.toMediaItem() }
        playerConnection.playQueue(
            ListQueue(
                title = "DJ Console Set (${selectedTracks.size} Tracks)",
                items = mediaItems,
            )
        )

        startLoop()
    }

    private fun startLoop() {
        consoleJob?.cancel()
        consoleJob = scope.launch {
            while (isActive && this.isActive) {
                delay(1000L)
                val conn = playerConnectionRef ?: break
                if (conn.player.isPlaying) {
                    if (secondsRemaining > 1) {
                        secondsRemaining--
                    } else {
                        swapToNextDeckTrack()
                    }
                }
            }
        }
    }

    fun swapToNextDeckTrack() {
        val conn = playerConnectionRef ?: return
        if (deckTracks.size < 2) return

        val curPos = conn.player.currentPosition
        if (curPos > 0) {
            trackPositions[currentDeckIndex] = curPos
        }

        val nextIndex = (currentDeckIndex + 1) % deckTracks.size
        val targetPos = trackPositions[nextIndex] ?: 0L

        currentDeckIndex = nextIndex
        totalSwaps++
        secondsRemaining = getIntervalForEffect(selectedEffect)

        conn.service.startManualTrackTransition(
            targetIndex = nextIndex,
            startPositionMs = targetPos,
            transitionDurationMs = transitionDurationMs,
            forcedStyle = selectedEffect,
        )
    }

    fun stopDjConsole() {
        consoleJob?.cancel()
        consoleJob = null
        isActive = false
        deckTracks = emptyList()
        currentDeckIndex = 0
        totalSwaps = 0
        secondsRemaining = 16
        trackPositions.clear()
    }

    // DynamicMashupController backward-compatibility aliases
    fun startMashup(
        selectedTracks: List<MediaMetadata>,
        style: MashupTransitionStyle,
        playerConnection: PlayerConnection,
    ) {
        val eff = when (style) {
            MashupTransitionStyle.ACAPELLA_SWAP -> TransitionStyle.VOCAL_SWAP
            MashupTransitionStyle.LINE_BY_LINE -> TransitionStyle.BEAT_SWAP
            MashupTransitionStyle.VERSE_BY_VERSE -> TransitionStyle.FILTER_SWEEP
        }
        startContinuousDjSet(selectedTracks, eff, playerConnection)
    }

    fun stopMashup() = stopDjConsole()
}
