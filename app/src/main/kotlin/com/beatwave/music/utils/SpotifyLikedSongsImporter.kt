package com.beatwave.music.utils

import android.content.Context
import com.beatwave.music.constants.SpotifySessionKey
import com.beatwave.music.db.InternalDatabase
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.db.entities.PlaylistSongMap
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.models.toMediaMetadata
import com.beatwave.music.viewmodels.SpotifySession
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.spotify.Spotify
import com.music.spotify.SpotifyAuth
import com.music.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

object SpotifyLikedSongsImporter {

    suspend fun importLikedSongs(
        context: Context,
        database: MusicDatabase = InternalDatabase.newInstance(context),
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val raw = context.dataStore.data.first()[SpotifySessionKey]
            if (raw.isNullOrBlank()) {
                throw IllegalStateException("Spotify session not found. Please connect to Spotify first.")
            }

            val session = json.decodeFromString<SpotifySession>(raw)
            if (Spotify.accessToken.isNullOrBlank() || System.currentTimeMillis() >= session.expiresAt) {
                val token = SpotifyAuth.fetchAccessToken(session.spDc, session.spKey.orEmpty()).getOrThrow()
                Spotify.accessToken = token.accessToken
            }

            val allTracks = mutableListOf<SpotifyTrack>()
            var offset = 0
            val limit = 50
            while (true) {
                val page = Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                if (page.items.isEmpty()) break
                allTracks.addAll(page.items.map { it.track })
                offset += limit
                if (page.total > 0) {
                    if (offset >= page.total) break
                } else {
                    if (page.items.size < limit) break
                }
            }

            val totalSongs = allTracks.size
            if (totalSongs == 0) return@runCatching 0

            val completedCount = AtomicInteger(0)
            val semaphore = Semaphore(4)
            val matchedMedia = allTracks.map { track ->
                async {
                    semaphore.withPermit {
                        var resultMedia: MediaMetadata? = null
                        try {
                            val artist = track.artists.firstOrNull()?.name.orEmpty()
                            val query = if (artist.isEmpty()) track.name else "$artist ${track.name}"
                            val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            val match = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                            if (match != null) {
                                resultMedia = match.toMediaMetadata()
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        } finally {
                            val done = completedCount.incrementAndGet()
                            onProgress?.invoke(done, totalSongs)
                        }
                        resultMedia
                    }
                }
            }.awaitAll().filterNotNull()

            database.withTransaction {
                val playlistId = "SPOTIFY_LIKED_SONGS"
                val existing = playlist(playlistId).first()
                val now = LocalDateTime.now()
                val entity = existing?.playlist?.copy(
                    name = "Liked Songs (Spotify)",
                    bookmarkedAt = existing.playlist.bookmarkedAt ?: now,
                    lastUpdateTime = now,
                    isEditable = true,
                ) ?: PlaylistEntity(
                    id = playlistId,
                    name = "Liked Songs (Spotify)",
                    bookmarkedAt = now,
                    lastUpdateTime = now,
                    isEditable = true,
                )

                if (existing == null) insert(entity) else update(entity)

                matchedMedia.forEach { metadata ->
                    insert(metadata) { song ->
                        song.copy(
                            liked = true,
                            likedDate = song.likedDate ?: now,
                            inLibrary = song.inLibrary ?: now,
                        )
                    }
                    val dbSong = song(metadata.id).first()
                    if (dbSong != null) {
                        update(
                            dbSong.song.copy(
                                liked = true,
                                likedDate = dbSong.song.likedDate ?: now,
                                inLibrary = dbSong.song.inLibrary ?: now,
                            ),
                        )
                    }
                }

                val currentSongs = playlistSongs(playlistId).first()
                val existingSongIds = currentSongs.map { it.map.songId }.toSet()
                val existingSignatures = currentSongs.map {
                    val cleanTitle = it.song.title.trim().lowercase()
                        .replace(Regex("""\s*\(.*?\)\s*"""), " ")
                        .replace(Regex("""\s*\[.*?\]\s*"""), " ")
                        .trim()
                    val cleanArtist = it.song.artists.firstOrNull()?.name?.trim()?.lowercase().orEmpty()
                    "$cleanTitle###$cleanArtist"
                }.toSet()

                val newMedia = matchedMedia.filter { media ->
                    val cleanTitle = media.title.trim().lowercase()
                        .replace(Regex("""\s*\(.*?\)\s*"""), " ")
                        .replace(Regex("""\s*\[.*?\]\s*"""), " ")
                        .trim()
                    val cleanArtist = media.artists.firstOrNull()?.name?.trim()?.lowercase().orEmpty()
                    val sig = "$cleanTitle###$cleanArtist"
                    media.id !in existingSongIds && sig !in existingSignatures
                }

                val maxPos = currentSongs.maxOfOrNull { it.map.position } ?: -1
                newMedia.forEachIndexed { index, metadata ->
                    insert(
                        PlaylistSongMap(
                            playlistId = playlistId,
                            songId = metadata.id,
                            position = maxPos + 1 + index,
                            setVideoId = metadata.setVideoId,
                        ),
                    )
                }
                update(entity.copy(lastUpdateTime = now))
            }

            matchedMedia.size
        }
    }
}
