/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.spotify

import android.content.Context
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.db.entities.PlaylistSongMap
import com.beatwave.music.db.entities.Song
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.models.toMediaMetadata
import com.beatwave.music.utils.UniversalPlaylistImporter
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.spotify.SpotifyMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyPlaylistSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchCache = ConcurrentHashMap<String, MediaMetadata>()
    private var periodicSyncJob: Job? = null

    init {
        startPeriodicSync()
    }

    /**
     * Starts a background loop that checks all auto-synced Spotify playlists
     * every 15 minutes while the app is active.
     */
    fun startPeriodicSync() {
        if (periodicSyncJob?.isActive == true) return
        periodicSyncJob = syncScope.launch {
            while (isActive) {
                delay(15 * 60 * 1000L) // 15 minutes
                try {
                    syncAllAutoSyncPlaylists()
                } catch (e: Exception) {
                    Timber.e(e, "Periodic Spotify auto-sync encountered an error")
                }
            }
        }
    }

    /**
     * Automatically syncs all Spotify playlists marked with isAutoSync = true.
     * Returns the total count of newly added tracks across all playlists.
     */
    suspend fun syncAllAutoSyncPlaylists(): Int = withContext(Dispatchers.IO) {
        var totalNewTracks = 0
        try {
            val allPlaylists = database.playlistsByNameAsc().first()
            val spotifyPlaylists = allPlaylists.filter { playlistWithSongs ->
                val pl = playlistWithSongs.playlist
                pl.isAutoSync && (
                    pl.id.startsWith("SPOTIFY_PLAYLIST_") ||
                    (pl.browseId != null && (pl.browseId.length == 22 || pl.browseId.startsWith("spotify:")))
                )
            }

            for (plWithSongs in spotifyPlaylists) {
                val pl = plWithSongs.playlist
                val spotifyId = pl.browseId?.removePrefix("spotify:playlist:")
                    ?: pl.id.removePrefix("SPOTIFY_PLAYLIST_")

                if (spotifyId.isNotBlank()) {
                    val result = syncPlaylist(pl.id, spotifyId)
                    totalNewTracks += result.getOrDefault(0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync all auto-sync Spotify playlists")
        }
        totalNewTracks
    }

    /**
     * Syncs a single Spotify playlist in real time.
     * Pulls the latest tracks from Spotify, detects any new tracks not yet in the local
     * playlist, matches them to playable streams, and appends them without overwriting or deleting
     * any existing songs.
     */
    suspend fun syncPlaylist(playlistId: String, rawSpotifyId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = rawSpotifyId.trim().removePrefix("spotify:playlist:")
            if (cleanId.isBlank()) return@runCatching 0

            Timber.d("SpotifyPlaylistSync: Checking remote tracks for $cleanId (local=$playlistId)")

            // 1. Fetch live tracks from Spotify
            val remoteUrl = "https://open.spotify.com/playlist/$cleanId"
            val remotePlaylist = UniversalPlaylistImporter.parse(remoteUrl)
            val remoteTracks = remotePlaylist.songs
            if (remoteTracks.isEmpty()) {
                Timber.d("SpotifyPlaylistSync: No remote tracks found on Spotify for $cleanId")
                return@runCatching 0
            }

            // 2. Fetch current local songs to compute diff
            val currentPlaylistSongs = database.playlistSongs(playlistId).first()
            val existingSongIds = currentPlaylistSongs.map { it.map.songId }.toSet()
            val existingSignatures = currentPlaylistSongs.map {
                normalizeSignature(it.song.title, it.song.artists.firstOrNull()?.name)
            }.toSet()

            // 3. Find only new tracks that are not yet in the playlist
            val newTracks = remoteTracks.filter { track ->
                val sig = normalizeSignature(track.title, track.artists.firstOrNull()?.name)
                sig !in existingSignatures
            }

            if (newTracks.isEmpty()) {
                Timber.d("SpotifyPlaylistSync: Playlist $playlistId is already up to date (0 new songs)")
                return@runCatching 0
            }

            Timber.i("SpotifyPlaylistSync: Found ${newTracks.size} new track(s) to add to $playlistId")

            // 4. Concurrently match new tracks to playable streams
            val semaphore = Semaphore(4)
            val matchedMedia = newTracks.map { song ->
                async {
                    semaphore.withPermit {
                        matchSongToStream(song)
                    }
                }
            }.awaitAll().filterNotNull()

            if (matchedMedia.isEmpty()) {
                Timber.w("SpotifyPlaylistSync: Could not match any of the ${newTracks.size} new tracks")
                return@runCatching 0
            }

            // 5. Append new songs to database in transaction (never overwriting existing songs!)
            database.withTransaction {
                val existing = database.playlist(playlistId).first()
                val now = LocalDateTime.now()

                val entity = existing?.playlist?.copy(
                    name = existing.playlist.name.ifBlank { remotePlaylist.title },
                    browseId = cleanId,
                    lastUpdateTime = now,
                    thumbnailUrl = existing.playlist.thumbnailUrl ?: remotePlaylist.coverUrl,
                    isEditable = true,
                    isAutoSync = true,
                ) ?: PlaylistEntity(
                    id = playlistId,
                    name = remotePlaylist.title,
                    browseId = cleanId,
                    bookmarkedAt = now,
                    lastUpdateTime = now,
                    thumbnailUrl = remotePlaylist.coverUrl,
                    isEditable = true,
                    isAutoSync = true,
                )

                if (existing == null) {
                    database.insert(entity)
                } else {
                    database.update(entity)
                }

                // Insert into song entity cache
                matchedMedia.forEach { metadata ->
                    database.insert(metadata)
                }

                // Determine position offset so we append after the last song
                val currentMaxPosition = currentPlaylistSongs.maxOfOrNull { it.map.position } ?: -1
                val filteredNewMedia = matchedMedia.filter { it.id !in existingSongIds }

                filteredNewMedia.forEachIndexed { index, metadata ->
                    database.insert(
                        PlaylistSongMap(
                            playlistId = playlistId,
                            songId = metadata.id,
                            position = currentMaxPosition + 1 + index,
                            setVideoId = metadata.setVideoId,
                        )
                    )
                }

                database.update(entity.copy(lastUpdateTime = now))
            }

            Timber.i("SpotifyPlaylistSync: Successfully appended ${matchedMedia.size} new song(s) to $playlistId")
            matchedMedia.size
        }
    }

    private suspend fun matchSongToStream(song: Song): MediaMetadata? {
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val songTitle = song.title
        val query = if (artist.isEmpty()) songTitle else "$artist $songTitle"
        val cacheKey = query.trim().lowercase()

        searchCache[cacheKey]?.let { return it }

        try {
            val searchResult = YouTube.search(
                query = query,
                filter = YouTube.SearchFilter.FILTER_SONG,
            ).getOrNull()

            val candidates = searchResult?.items
                ?.filterIsInstance<SongItem>()
                ?.distinctBy { it.id }
                .orEmpty()

            val best = candidates.maxByOrNull { candidate ->
                SpotifyMapper.matchScore(
                    spotifyTitle = song.title,
                    spotifyArtist = song.artists.joinToString(" ") { it.name },
                    spotifyDurationMs = song.song.duration * 1000,
                    candidateTitle = candidate.title,
                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                    candidateDurationSec = candidate.duration,
                )
            }

            if (best != null) {
                val media = best.toMediaMetadata()
                searchCache[cacheKey] = media
                return media
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to match Spotify track '$query'")
        }
        return null
    }

    /**
     * Pushes any local songs added in Pulse Wave up to the remote Spotify playlist.
     * Searches for matching Spotify track IDs and calls Spotify.addTracksToPlaylist.
     */
    suspend fun pushLocalSongsToSpotify(playlistId: String, rawSpotifyId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = rawSpotifyId.trim().removePrefix("spotify:playlist:")
            if (cleanId.isBlank()) return@runCatching 0

            // 1. Fetch remote tracks to find what is already on Spotify
            val remoteUrl = "https://open.spotify.com/playlist/$cleanId"
            val remotePlaylist = UniversalPlaylistImporter.parse(remoteUrl)
            val remoteSignatures = remotePlaylist.songs.map {
                normalizeSignature(it.title, it.artists.firstOrNull()?.name)
            }.toSet()

            // 2. Fetch local playlist songs
            val localSongs = database.playlistSongs(playlistId).first()
            val songsToPush = localSongs.filter { songWithMap ->
                val sig = normalizeSignature(songWithMap.song.title, songWithMap.song.artists.firstOrNull()?.name)
                sig !in remoteSignatures
            }

            if (songsToPush.isEmpty()) {
                Timber.d("SpotifyPlaylistSync: No local songs to push to Spotify ($cleanId)")
                return@runCatching 0
            }

            val trackUris = mutableListOf<String>()
            for (songWithMap in songsToPush) {
                val query = "${songWithMap.song.title} ${songWithMap.song.artists.firstOrNull()?.name.orEmpty()}".trim()
                val spotifyTrack = com.music.spotify.Spotify.searchTrack(query).getOrNull()
                if (spotifyTrack?.uri != null) {
                    trackUris.add(spotifyTrack.uri!!)
                } else if (spotifyTrack?.id != null) {
                    trackUris.add("spotify:track:${spotifyTrack.id}")
                }
            }

            if (trackUris.isNotEmpty()) {
                val success = com.music.spotify.Spotify.addTracksToPlaylist(cleanId, trackUris).getOrDefault(false)
                if (success) {
                    Timber.i("SpotifyPlaylistSync: Pushed ${trackUris.size} song(s) to Spotify ($cleanId)")
                    return@runCatching trackUris.size
                }
            }
            0
        }
    }

    /**
     * Executes two-way synchronization:
     * 1. Pulls newly added Spotify tracks down into Pulse Wave.
     * 2. Pushes newly added Pulse Wave tracks up into Spotify.
     */
    suspend fun syncTwoWay(playlistId: String, rawSpotifyId: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val pulled = syncPlaylist(playlistId, rawSpotifyId).getOrDefault(0)
            val pushed = pushLocalSongsToSpotify(playlistId, rawSpotifyId).getOrDefault(0)
            pulled to pushed
        }
    }

    private fun normalizeSignature(title: String, artist: String?): String {
        val cleanTitle = title.trim().lowercase()
            .replace(Regex("""\s*\(.*?\)\s*"""), " ")
            .replace(Regex("""\s*\[.*?\]\s*"""), " ")
            .trim()
        val cleanArtist = (artist ?: "").trim().lowercase()
        return "$cleanTitle###$cleanArtist"
    }
}
