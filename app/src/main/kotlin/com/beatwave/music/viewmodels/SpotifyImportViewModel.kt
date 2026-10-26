package com.beatwave.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotify.Spotify
import com.music.spotify.SpotifyAuth
import com.music.spotify.SpotifyMapper
import com.music.spotify.models.SpotifyPlaylist
import com.music.spotify.models.SpotifyTrack
import com.beatwave.music.constants.SpotifySessionKey
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.ArtistEntity
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.db.entities.PlaylistSongMap
import com.beatwave.music.db.entities.Song
import com.beatwave.music.db.entities.SongEntity
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.models.toMediaMetadata
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.beatwave.music.utils.dataStore
import com.beatwave.music.utils.reportException
import com.beatwave.music.utils.UniversalPlaylistImporter
import com.beatwave.music.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@Serializable
data class SpotifySession(
    val spDc: String,
    val spKey: String? = null,
    val accessToken: String? = null,
    val expiresAt: Long = 0,
    val accountName: String? = null,
    val accountAvatarUrl: String? = null,
)

data class SpotifyImportUiState(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val likedSongsCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class SpotifyImportProgress(
    val playlistName: String,
    val currentSongIndex: Int,
    val totalSongs: Int,
    val percent: Float,
    val isFinished: Boolean = false
)

private data class PlaylistImportData(
    val title: String,
    val songs: List<Song>,
    val localPlaylistId: String,
    val thumbnailUrl: String?
)

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyImportUiState(isLoading = true))
    val uiState: StateFlow<SpotifyImportUiState> = _uiState.asStateFlow()

    private val _importProgress = MutableStateFlow<SpotifyImportProgress?>(null)
    val importProgress: StateFlow<SpotifyImportProgress?> = _importProgress.asStateFlow()

    private var importJob: Job? = null
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, MediaMetadata>()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    init {
        restoreSession()
    }

    private suspend fun getSession(): SpotifySession? {
        val prefs = context.dataStore.data.first()
        val sessionJson = prefs[SpotifySessionKey]
        return if (!sessionJson.isNullOrBlank()) {
            json.decodeFromString<SpotifySession>(sessionJson)
        } else {
            null
        }
    }

    private suspend fun saveSession(session: SpotifySession) {
        context.dataStore.edit { prefs ->
            prefs[SpotifySessionKey] = json.encodeToString(session)
        }
    }

    private suspend fun ensureAuthenticated(): SpotifySession {
        val session = getSession() ?: throw IllegalStateException("Not connected to Spotify")
        if (session.accessToken != null && session.expiresAt > System.currentTimeMillis() + 60_000L) {
            Spotify.accessToken = session.accessToken
            return session
        }
        return refreshWithCookies(session.spDc, session.spKey.orEmpty())
    }

    private suspend fun refreshWithCookies(spDc: String, spKey: String): SpotifySession =
        withContext(Dispatchers.IO) {
            val token = SpotifyAuth.fetchAccessToken(spDc, spKey).getOrThrow()
            Spotify.accessToken = token.accessToken
            val profile = Spotify.me().getOrNull()

            val newSession = SpotifySession(
                spDc = spDc,
                spKey = spKey,
                accessToken = token.accessToken,
                expiresAt = token.accessTokenExpirationTimestampMs,
                accountName = profile?.displayName,
                accountAvatarUrl = profile?.images?.firstOrNull()?.url
            )
            saveSession(newSession)
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    accountName = newSession.accountName.orEmpty(),
                    accountAvatarUrl = newSession.accountAvatarUrl,
                    isLoading = false
                )
            }
            newSession
        }

    fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = getSession()
                if (session == null) {
                    _uiState.update { it.copy(isAuthenticated = false, isLoading = false) }
                    return@launch
                }
                if (session.accessToken != null && session.expiresAt > System.currentTimeMillis() + 60_000L) {
                    Spotify.accessToken = session.accessToken
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            accountName = session.accountName.orEmpty(),
                            accountAvatarUrl = session.accountAvatarUrl,
                            isLoading = false
                        )
                    }
                    loadSources()
                } else {
                    runCatching { refreshWithCookies(session.spDc, session.spKey.orEmpty()) }
                        .onSuccess { loadSources() }
                        .onFailure { error ->
                            logout()
                            _uiState.update {
                                it.copy(
                                    isAuthenticated = false,
                                    isLoading = false,
                                    errorMessage = "Session expired. Please log in again."
                                )
                            }
                        }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to restore session"
                    )
                }
            }
        }
    }

    fun connectWithCookies(spDc: String, spKey: String) {
        if (spDc.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { refreshWithCookies(spDc, spKey) }
                .onSuccess { loadSources() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to connect to Spotify"
                        )
                    }
                }
        }
    }

    fun loadSources() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                ensureAuthenticated()
                val meResult = Spotify.me().getOrThrow()
                val likedSongsResult = runCatching {
                    spotifyCallWithTokenRetry {
                        Spotify.likedSongs(limit = 1, offset = 0).getOrThrow()
                    }
                }.getOrNull()

                val playlistsList = mutableListOf<SpotifyPlaylist>()
                var offset = 0
                val limit = 50
                while (true) {
                    val page = spotifyCallWithTokenRetry {
                        Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                    }
                    if (page.items.isEmpty()) break
                    playlistsList.addAll(page.items)
                    offset += limit
                    if (page.total > 0) {
                        if (offset >= page.total) break
                    } else {
                        if (page.items.size < limit) break
                    }
                }

                _uiState.update {
                    it.copy(
                        playlists = playlistsList,
                        likedSongsCount = likedSongsResult?.total ?: 0,
                        isLoading = false,
                        accountName = meResult.displayName.orEmpty(),
                        accountAvatarUrl = meResult.images.firstOrNull()?.url
                    )
                }
            } catch (e: Exception) {
                reportException(e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to fetch playlists"
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs.remove(SpotifySessionKey)
            }
            Spotify.accessToken = null
            _uiState.update {
                SpotifyImportUiState(
                    isAuthenticated = false,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T =
        runCatching { block() }
            .getOrElse { error ->
                if ((error as? Spotify.SpotifyException)?.statusCode != 401) {
                    throw error
                }
                ensureAuthenticated()
                block()
            }

    suspend fun fetchPlaylistTracks(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = spotifyCallWithTokenRetry {
                Spotify.playlistTracks(playlistId, limit = limit, offset = offset).getOrThrow()
            }
            if (page.items.isEmpty()) break
            tracks.addAll(page.items.mapNotNull { it.track })
            offset += limit
            if (page.total > 0) {
                if (offset >= page.total) break
            } else {
                if (page.items.size < limit) break
            }
        }

        return@withContext tracks.map { track ->
            Song(
                song = SongEntity(
                    id = "",
                    title = track.name,
                    duration = track.durationMs / 1000,
                    thumbnailUrl = SpotifyMapper.getTrackThumbnail(track),
                ),
                artists = track.artists.map { ArtistEntity(id = "", name = it.name) },
            )
        }
    }

    suspend fun fetchLikedSongsTracks(): List<Song> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 50
        ensureAuthenticated()
        while (true) {
            val page = spotifyCallWithTokenRetry {
                Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
            }
            if (page.items.isEmpty()) break
            tracks.addAll(page.items.map { it.track })
            offset += limit
            if (page.total > 0) {
                if (offset >= page.total) break
            } else {
                if (page.items.size < limit) break
            }
        }

        return@withContext tracks.map { track ->
            Song(
                song = SongEntity(
                    id = "",
                    title = track.name,
                    duration = track.durationMs / 1000,
                    thumbnailUrl = SpotifyMapper.getTrackThumbnail(track),
                ),
                artists = track.artists.map { ArtistEntity(id = "", name = it.name) },
            )
        }
    }

    fun startImport(selectedIds: List<String>) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            val isLiked = selectedIds.contains("liked_songs")
            val initialTitle = if (isLiked) {
                context.getString(R.string.spotify_liked_songs)
            } else {
                context.getString(R.string.spotify_import_in_progress)
            }
            _importProgress.value = SpotifyImportProgress(
                playlistName = initialTitle,
                currentSongIndex = 0,
                totalSongs = 0,
                percent = 0.0f,
                isFinished = false
            )

            try {
                selectedIds.forEachIndexed { playlistIndex, id ->
                    val importData = if (id == "liked_songs") {
                        val songs = fetchLikedSongsTracks()
                        PlaylistImportData(
                            title = context.getString(R.string.spotify_liked_songs),
                            songs = songs,
                            localPlaylistId = "SPOTIFY_LIKED_SONGS",
                            thumbnailUrl = null
                        )
                    } else {
                        val playlist = _uiState.value.playlists.firstOrNull { it.id == id }
                        if (playlist != null) {
                            val songs = fetchPlaylistTracks(playlist.id)
                            PlaylistImportData(
                                title = playlist.name,
                                songs = songs,
                                localPlaylistId = "SPOTIFY_PLAYLIST_${playlist.id}",
                                thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist)
                            )
                        } else {
                            null
                        }
                    } ?: return@forEachIndexed

                    val totalSongs = importData.songs.size
                    if (totalSongs == 0) {
                        database.withTransaction {
                            val existing = playlist(importData.localPlaylistId).first()
                            val now = LocalDateTime.now()
                            val entity = existing?.playlist?.copy(
                                name = importData.title,
                                bookmarkedAt = existing.playlist.bookmarkedAt ?: now,
                                lastUpdateTime = now,
                                thumbnailUrl = importData.thumbnailUrl,
                                isEditable = true,
                            ) ?: PlaylistEntity(
                                id = importData.localPlaylistId,
                                name = importData.title,
                                bookmarkedAt = now,
                                lastUpdateTime = now,
                                thumbnailUrl = importData.thumbnailUrl,
                                isEditable = true,
                            )
                            if (existing == null) insert(entity) else update(entity)
                            clearPlaylist(importData.localPlaylistId)
                        }

                        _importProgress.update {
                            SpotifyImportProgress(
                                playlistName = importData.title,
                                currentSongIndex = 0,
                                totalSongs = 0,
                                percent = 1.0f
                            )
                        }
                        return@forEachIndexed
                    }

                    _importProgress.update {
                        SpotifyImportProgress(
                            playlistName = importData.title,
                            currentSongIndex = 0,
                            totalSongs = totalSongs,
                            percent = 0.0f
                        )
                    }

                    val completedCount = AtomicInteger(0)
                    val semaphore = Semaphore(4)

                    val matchedMedia = importData.songs.mapIndexed { _, song ->
                        async {
                            semaphore.withPermit {
                                var resultMedia: MediaMetadata? = null
                                try {
                                    resultMedia = matchSongToYouTube(song)
                                } finally {
                                    val done = completedCount.incrementAndGet()
                                    _importProgress.update {
                                        SpotifyImportProgress(
                                            playlistName = importData.title,
                                            currentSongIndex = done,
                                            totalSongs = totalSongs,
                                            percent = done.toFloat() / totalSongs
                                        )
                                    }
                                }
                                resultMedia
                            }
                        }
                    }.awaitAll().filterNotNull()

                    saveImportedPlaylist(importData, matchedMedia)
                }
                _importProgress.update { old ->
                    old?.copy(isFinished = true) ?: SpotifyImportProgress(
                        playlistName = "",
                        currentSongIndex = 0,
                        totalSongs = 0,
                        percent = 1.0f,
                        isFinished = true
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    reportException(e)
                    val errorMsg = e.localizedMessage ?: "Failed to import playlists"
                    _uiState.update { it.copy(errorMessage = errorMsg) }
                    _importProgress.update { old ->
                        old?.copy(
                            isFinished = true,
                            playlistName = "Import Failed: $errorMsg"
                        ) ?: SpotifyImportProgress(
                            playlistName = "Import Failed: $errorMsg",
                            currentSongIndex = 0,
                            totalSongs = 0,
                            percent = 1.0f,
                            isFinished = true
                        )
                    }
                }
            } finally {
                if (_importProgress.value?.isFinished != true && _importProgress.value?.totalSongs == 0) {
                    _importProgress.update { null }
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importProgress.update { null }
    }

    fun dismissImportProgress() {
        _importProgress.update { null }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun importPlaylistByLink(link: String) {
        val cleanLink = link.trim()
        if (cleanLink.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a playlist link") }
            return
        }

        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            _importProgress.value = SpotifyImportProgress(
                playlistName = "Analyzing playlist link...",
                currentSongIndex = 0,
                totalSongs = 0,
                percent = 0.0f,
                isFinished = false
            )
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val parsed = UniversalPlaylistImporter.parse(cleanLink)
                val totalSongs = parsed.songs.size
                if (totalSongs == 0) {
                    throw Exception("No songs found in playlist. Please make sure the link is public.")
                }

                val safeId = Math.abs(cleanLink.hashCode()).toString()
                val importData = PlaylistImportData(
                    title = parsed.title,
                    songs = parsed.songs,
                    localPlaylistId = "PLAYLIST_${parsed.source.uppercase()}_$safeId",
                    thumbnailUrl = parsed.coverUrl
                )

                _uiState.update { it.copy(isLoading = false) }

                _importProgress.update {
                    SpotifyImportProgress(
                        playlistName = importData.title,
                        currentSongIndex = 0,
                        totalSongs = totalSongs,
                        percent = 0.0f
                    )
                }

                val completedCount = AtomicInteger(0)
                val semaphore = Semaphore(4)

                val matchedMedia = importData.songs.mapIndexed { _, song ->
                    async {
                        semaphore.withPermit {
                            var resultMedia: MediaMetadata? = null
                            try {
                                resultMedia = matchSongToYouTube(song)
                            } finally {
                                val done = completedCount.incrementAndGet()
                                _importProgress.update {
                                    SpotifyImportProgress(
                                        playlistName = importData.title,
                                        currentSongIndex = done,
                                        totalSongs = totalSongs,
                                        percent = done.toFloat() / totalSongs
                                    )
                                }
                            }
                            resultMedia
                        }
                    }
                }.awaitAll().filterNotNull()

                saveImportedPlaylist(importData, matchedMedia)

                _importProgress.update { old ->
                    old?.copy(isFinished = true) ?: SpotifyImportProgress(
                        playlistName = importData.title,
                        currentSongIndex = totalSongs,
                        totalSongs = totalSongs,
                        percent = 1.0f,
                        isFinished = true
                    )
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    reportException(e)
                    val errorMsg = e.localizedMessage ?: "Failed to import playlist"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMsg
                        )
                    }
                    _importProgress.update {
                        SpotifyImportProgress(
                            playlistName = "Import Failed: $errorMsg",
                            currentSongIndex = 0,
                            totalSongs = 0,
                            percent = 1.0f,
                            isFinished = true
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun matchSongToYouTube(song: Song): MediaMetadata? {
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
            reportException(e)
        }
        return null
    }

    private suspend fun saveImportedPlaylist(
        importData: PlaylistImportData,
        matchedMedia: List<MediaMetadata>
    ) {
        database.withTransaction {
            val existing = playlist(importData.localPlaylistId).first()
            val now = LocalDateTime.now()
            val entity = existing?.playlist?.copy(
                name = importData.title,
                bookmarkedAt = existing.playlist.bookmarkedAt ?: now,
                lastUpdateTime = now,
                thumbnailUrl = importData.thumbnailUrl,
                isEditable = true,
            ) ?: PlaylistEntity(
                id = importData.localPlaylistId,
                name = importData.title,
                bookmarkedAt = now,
                lastUpdateTime = now,
                thumbnailUrl = importData.thumbnailUrl,
                isEditable = true,
            )

            if (existing == null) {
                insert(entity)
            } else {
                update(entity)
            }

            matchedMedia.chunked(25).forEach { chunk ->
                chunk.forEach { metadata ->
                    insert(metadata)
                    if (importData.localPlaylistId == "SPOTIFY_LIKED_SONGS") {
                        val dbSong = song(metadata.id).first()
                        if (dbSong != null) {
                            update(
                                dbSong.song.copy(
                                    liked = true,
                                    likedDate = dbSong.song.likedDate ?: now,
                                    inLibrary = dbSong.song.inLibrary ?: now
                                )
                            )
                        }
                    }
                }
            }

            clearPlaylist(importData.localPlaylistId)
            matchedMedia.forEachIndexed { index, metadata ->
                insert(
                    PlaylistSongMap(
                        playlistId = importData.localPlaylistId,
                        songId = metadata.id,
                        position = index,
                        setVideoId = metadata.setVideoId,
                    )
                )
            }
            update(entity.copy(lastUpdateTime = now))
        }
    }

    private fun extractPlaylistId(link: String): String? {
        val trimmed = link.trim()
        if (trimmed.length == 22 && trimmed.all { it.isLetterOrDigit() }) {
            return trimmed
        }
        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.substringAfter("spotify:playlist:").substringBefore("?").substringBefore(":")
            if (id.length == 22) return id
        }
        val httpMarker = "playlist/"
        if (trimmed.contains(httpMarker)) {
            val id = trimmed.substringAfter(httpMarker).substringBefore("?").substringBefore("/")
            if (id.length == 22) return id
        }
        return null
    }
}
