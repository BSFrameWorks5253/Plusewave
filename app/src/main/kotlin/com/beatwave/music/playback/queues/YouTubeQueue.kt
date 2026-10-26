/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.beatwave.music.extensions.toMediaItem
import com.beatwave.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            // If playing a single track without an explicit playlist, attach the automix radio playlist ID
            // so YouTube automatically generates a personalized queue of similar genre tracks
            if (endpoint.playlistId == null && endpoint.videoId != null) {
                endpoint = WatchEndpoint(
                    videoId = endpoint.videoId,
                    playlistId = "RDAMVM${endpoint.videoId}"
                )
            }

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0

                    val items = nextResult.items.map { it.toMediaItem() }.toMutableList()

                    // If YouTube only returned the single requested song, supplement with related tracks of the same genre
                    if (items.size <= 1) {
                        nextResult.relatedEndpoint?.let { relatedEndpoint ->
                            try {
                                val relatedPage = YouTube.related(relatedEndpoint).getOrNull()
                                relatedPage?.songs?.map { it.toMediaItem() }?.let { relatedItems ->
                                    val existingIds = items.map { it.mediaId }.toSet()
                                    items.addAll(relatedItems.filter { it.mediaId !in existingIds })
                                }
                            } catch (_: Exception) {
                                // Non-fatal fallback
                            }
                        }
                    }

                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = items,
                        mediaItemIndex = nextResult.currentIndex ?: 0,
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId != "RDAMVM${endpoint.videoId}") {
                        endpoint = WatchEndpoint(
                            videoId = endpoint.videoId,
                            playlistId = "RDAMVM${endpoint.videoId}"
                        )
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null // Stop trying to load more
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        /**
         * Creates a radio queue based on a song.
         * Uses RDAMVM playlistId so YouTube generates an infinite automix of songs of the same type and genre.
         */
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(
                    videoId = song.id,
                    playlistId = "RDAMVM${song.id}"
                ),
                song
            )
        }
    }
}
