/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.audio

import android.content.Context
import android.media.audiofx.Visualizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Provides real-time audio FFT bass energy (20Hz - 120Hz) to drive
 * audio-reactive liquid glass, shaders, and UI micro-animations.
 */
object AudioReactiveManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _bassEnergy = MutableStateFlow(0f)
    val bassEnergy: StateFlow<Float> = _bassEnergy.asStateFlow()

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = -1
    private var isPlaying: Boolean = false
    private var smoothedEnergy = 0f

    /**
     * Attaches or updates the audio session to sample.
     */
    fun updateSession(sessionId: Int, playing: Boolean) {
        isPlaying = playing
        if (sessionId <= 0) {
            releaseVisualizer()
            return
        }

        if (sessionId == currentSessionId && visualizer != null) {
            return
        }

        currentSessionId = sessionId
        initVisualizer(sessionId)
    }

    private fun initVisualizer(sessionId: Int) {
        releaseVisualizer()
        runCatching {
            val v = Visualizer(sessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                captureSize = range[0].coerceAtLeast(128)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        if (fft == null || fft.size < 8 || !isPlaying) {
                            decayEnergy()
                            return
                        }

                        // Low-frequency bins (sub-bass / bass, 20Hz - 120Hz)
                        // Bins 1..4: k=1,2,3
                        var sumMag = 0f
                        val maxBin = (fft.size / 4).coerceAtMost(5).coerceAtLeast(2)
                        for (k in 1 until maxBin) {
                            val r = fft[2 * k].toFloat()
                            val i = fft[2 * k + 1].toFloat()
                            sumMag += hypot(r, i)
                        }

                        val rawEnergy = (sumMag / (maxBin * 90f)).coerceIn(0f, 1f)
                        // Fast attack, smooth decay
                        smoothedEnergy = if (rawEnergy > smoothedEnergy) {
                            smoothedEnergy * 0.3f + rawEnergy * 0.7f
                        } else {
                            smoothedEnergy * 0.82f + rawEnergy * 0.18f
                        }
                        _bassEnergy.value = smoothedEnergy
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            visualizer = v
            Timber.d("AudioReactiveManager: Successfully attached Visualizer to session $sessionId")
        }.onFailure { e ->
            Timber.w(e, "AudioReactiveManager: Visualizer init failed for session $sessionId; using fallback simulation")
            visualizer = null
        }
    }

    private fun decayEnergy() {
        smoothedEnergy *= 0.85f
        if (smoothedEnergy < 0.01f) smoothedEnergy = 0f
        _bassEnergy.value = smoothedEnergy
    }

    fun releaseVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        currentSessionId = -1
        decayEnergy()
    }

    /**
     * Fallback energy pulse when audio session visualizer cannot be bound.
     */
    fun reportSyntheticPulse(progressFraction: Float) {
        if (visualizer == null && isPlaying) {
            val pulse = (sin(progressFraction * 20.0 * Math.PI).toFloat().coerceAtLeast(0f)) * 0.45f
            _bassEnergy.value = pulse
        }
    }
}

val LocalBassEnergy = staticCompositionLocalOf { 0f }
