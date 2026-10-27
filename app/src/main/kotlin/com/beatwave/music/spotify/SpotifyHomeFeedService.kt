/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.spotify

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.spotify.Spotify
import com.music.spotify.SpotifyMapper
import com.music.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyHomeTrack(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationMs: Int,
    val spotifyUri: String?,
)

data class SpotifyHomeSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<SpotifyHomeTrack>,
)

@Singleton
class SpotifyHomeFeedService @Inject constructor() {
    private var cachedSections: List<SpotifyHomeSection>? = null
    private var lastFetchTime = 0L
    private val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

    suspend fun getHomeSections(forceRefresh: Boolean = false): List<SpotifyHomeSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSections != null && (now - lastFetchTime) < CACHE_EXPIRY_MS) {
            return@withContext cachedSections!!
        }

        val sections = mutableListOf<SpotifyHomeSection>()

        // 1. Fetch Today's Top Hits (37i9dQZF1DXcBWIGoYBM5M)
        runCatching {
            Spotify.fetchPlaylistEmbedTracks("37i9dQZF1DXcBWIGoYBM5M").getOrNull()?.let { tracks ->
                if (tracks.isNotEmpty()) {
                    sections.add(
                        SpotifyHomeSection(
                            id = "spotify_top_hits",
                            title = "Today's Top Hits",
                            subtitle = "Trending worldwide on Spotify",
                            tracks = tracks.take(15).map { it.toHomeTrack() }
                        )
                    )
                }
            }
        }

        // 2. Fetch Top 50 - Global (37i9dQZEVXbMDoHDwVN2tF)
        runCatching {
            Spotify.fetchPlaylistEmbedTracks("37i9dQZEVXbMDoHDwVN2tF").getOrNull()?.let { tracks ->
                if (tracks.isNotEmpty()) {
                    sections.add(
                        SpotifyHomeSection(
                            id = "spotify_global_50",
                            title = "Spotify Top 50 Global",
                            subtitle = "The most played tracks right now",
                            tracks = tracks.take(15).map { it.toHomeTrack() }
                        )
                    )
                }
            }
        }

        // 3. Fetch Spotify Recommendations (personalized or algorithmic curated)
        runCatching {
            Spotify.getRecommendations(limit = 20).getOrNull()?.let { tracks ->
                if (tracks.isNotEmpty()) {
                    sections.add(
                        SpotifyHomeSection(
                            id = "spotify_recommendations",
                            title = "Spotify Discover",
                            subtitle = "Recommended for your vibe",
                            tracks = tracks.map { it.toHomeTrack() }
                        )
                    )
                }
            }
        }

        // 4. Chill & Lo-Fi Hits (37i9dQZF1DWWQRwui0ExPn)
        runCatching {
            Spotify.fetchPlaylistEmbedTracks("37i9dQZF1DWWQRwui0ExPn").getOrNull()?.let { tracks ->
                if (tracks.isNotEmpty()) {
                    sections.add(
                        SpotifyHomeSection(
                            id = "spotify_chill_lofi",
                            title = "Spotify Chill & Lofi",
                            subtitle = "Beats to relax, study, and unwind",
                            tracks = tracks.take(15).map { it.toHomeTrack() }
                        )
                    )
                }
            }
        }

        if (sections.isNotEmpty()) {
            cachedSections = sections
            lastFetchTime = now
        }
        sections
    }

    suspend fun resolveToSongItem(track: SpotifyHomeTrack): SongItem? = withContext(Dispatchers.IO) {
        try {
            val query = if (track.artist.isNotBlank()) "${track.artist} ${track.title}" else track.title
            val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun SpotifyTrack.toHomeTrack(): SpotifyHomeTrack = SpotifyHomeTrack(
        spotifyId = id,
        title = name,
        artist = artists.joinToString(", ") { it.name }.ifEmpty { "Unknown Artist" },
        thumbnailUrl = SpotifyMapper.getTrackThumbnail(this),
        durationMs = durationMs,
        spotifyUri = uri,
    )
}
