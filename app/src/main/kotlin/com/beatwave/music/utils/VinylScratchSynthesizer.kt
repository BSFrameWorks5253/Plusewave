/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

object VinylScratchSynthesizer {
    private var lastPlayTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Synthesizes and plays a realistic DJ vinyl record scratching sound effect.
     * The pitch, duration, and friction noise scale dynamically with angular scrub velocity.
     *
     * @param velocity Angular velocity in degrees per gesture frame (positive = forward, negative = rewind).
     */
    fun playScratch(velocity: Float) {
        val now = System.currentTimeMillis()
        if (now - lastPlayTime < 35L) return
        lastPlayTime = now

        val absVel = abs(velocity)
        if (absVel < 0.8f) return

        scope.launch {
            try {
                val sampleRate = 22050
                val durationMs = (absVel * 2.2f).toInt().coerceIn(35, 85)
                val numSamples = (sampleRate * durationMs) / 1000
                if (numSamples <= 0) return@launch

                val buffer = ShortArray(numSamples)
                val baseFreq = (240f + absVel * 22f).coerceIn(160f, 2200f)
                val freqSweep = if (velocity > 0) baseFreq * 0.35f else -baseFreq * 0.35f

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val currentFreq = baseFreq + freqSweep * progress
                    val t = i.toFloat() / sampleRate

                    // Sawtooth wave modulated by frequency
                    val phase = (t * currentFreq) % 1.0f
                    val saw = (phase * 2f - 1f) * 0.45f

                    // Analog needle surface friction noise
                    val noise = ((Math.random().toFloat() * 2f - 1f)) * 0.22f

                    // Smooth parabolic amplitude envelope
                    val envelope = sin(progress * Math.PI.toFloat())

                    val sample = ((saw + noise) * envelope * 0.6f * Short.MAX_VALUE).toInt()
                    buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()

                delay(durationMs + 20L)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Silently recover if audio pipeline is temporarily unavailable
            }
        }
    }
}
