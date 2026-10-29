/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.queues

import androidx.media3.common.MediaItem
import com.beatwave.music.extensions.metadata
import com.beatwave.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?
    val playlistId: String?
        get() = null

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true): Status {
            if (!enabled) return this
            val targetMediaId = items.getOrNull(mediaItemIndex)?.mediaId
            val filteredItems = items.filterExplicit()
            val newIndex = if (targetMediaId != null) {
                val idx = filteredItems.indexOfFirst { it.mediaId == targetMediaId }
                if (idx != -1) idx else mediaItemIndex.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
            } else {
                mediaItemIndex.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
            }
            return copy(items = filteredItems, mediaItemIndex = newIndex)
        }

        fun filterVideoSongs(disableVideos: Boolean = false): Status {
            if (!disableVideos) return this
            val targetMediaId = items.getOrNull(mediaItemIndex)?.mediaId
            val filteredItems = items.filterVideoSongs(true)
            val newIndex = if (targetMediaId != null) {
                val idx = filteredItems.indexOfFirst { it.mediaId == targetMediaId }
                if (idx != -1) idx else mediaItemIndex.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
            } else {
                mediaItemIndex.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
            }
            return copy(items = filteredItems, mediaItemIndex = newIndex)
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
