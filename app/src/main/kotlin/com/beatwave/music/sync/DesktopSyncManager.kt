package com.beatwave.music.sync

import android.content.Context
import androidx.room.withTransaction
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.db.entities.PlaylistSongMap
import com.beatwave.music.models.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    data class DesktopStatus(
        val app: String,
        val version: String,
        val device: String,
        val playlistCount: Int,
        val totalSongs: Int,
        val isReady: Boolean,
    )

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val desktopDevice: String = "",
        val syncedPlaylistsCount: Int = 0,
        val syncedSongsCount: Int = 0,
    )

    suspend fun checkDesktopStatus(host: String, port: Int = 5253): Result<DesktopStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
                val url = "http://$cleanHost:$port/api/status"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Desktop responded with HTTP ${response.code}")
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    DesktopStatus(
                        app = json.optString("app", "PulseWave Desktop"),
                        version = json.optString("version", "1.0.0"),
                        device = json.optString("device", "Desktop PC"),
                        playlistCount = json.optInt("playlistCount", 0),
                        totalSongs = json.optInt("totalSongs", 0),
                        isReady = json.optBoolean("syncReady", true),
                    )
                }
            }
        }

    suspend fun syncWithDesktop(host: String, port: Int = 5253): Result<SyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
                val syncUrl = "http://$cleanHost:$port/api/sync"

                // 1. Collect all local playlists from Room Database
                val localPlaylists = database.dao.playlistsByNameAsc().first()
                val payloadJson = JSONObject()
                payloadJson.put("sourceDevice", "Android ${android.os.Build.MODEL}")

                val incomingArray = JSONArray()
                for (pl in localPlaylists) {
                    val plObj = JSONObject()
                    plObj.put("id", pl.id)
                    plObj.put("name", pl.playlist.name)
                    plObj.put("thumbnailUrl", pl.playlist.thumbnailUrl ?: "")
                    plObj.put("author", "Phone")

                    // Get songs for this playlist
                    val songsList = database.dao.playlistSongs(pl.id).first()
                    val songsArray = JSONArray()
                    for (song in songsList) {
                        val sObj = JSONObject()
                        sObj.put("id", song.song.id)
                        sObj.put("title", song.song.title)
                        sObj.put("artistsText", song.artists.joinToString { it.name })
                        sObj.put("duration", song.song.duration)
                        sObj.put("thumbnailUrl", song.song.thumbnailUrl ?: "")
                        songsArray.put(sObj)
                    }
                    plObj.put("songs", songsArray)
                    incomingArray.put(plObj)
                }
                payloadJson.put("incomingPlaylists", incomingArray)

                // 2. Transmit to Desktop Sync Server
                val body = payloadJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(syncUrl).post(body).build()

                val responseString = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Sync failed with server response: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }

                val resJson = JSONObject(responseString)
                val desktopDevice = resJson.optString("desktopDevice", "Desktop PC")
                val desktopPlaylistsArray = resJson.optJSONArray("playlists") ?: JSONArray()

                // 3. Save incoming desktop playlists back to local Room database
                var importedPlaylists = 0
                var importedSongs = 0
                val now = LocalDateTime.now()

                database.withTransaction {
                    for (i in 0 until desktopPlaylistsArray.length()) {
                        val dp = desktopPlaylistsArray.getJSONObject(i)
                        val pId = dp.optString("id", "desktop_${System.currentTimeMillis()}_$i")
                        val pName = dp.optString("name", "Synced Playlist")
                        val pThumb = dp.optString("thumbnailUrl", "").takeIf { it.isNotBlank() }

                        val entity = PlaylistEntity(
                            id = pId,
                            name = pName,
                            bookmarkedAt = now,
                            lastUpdateTime = now,
                            thumbnailUrl = pThumb,
                            isEditable = true,
                        )
                        database.dao.insert(entity)
                        importedPlaylists++

                        val dpSongs = dp.optJSONArray("songs") ?: JSONArray()
                        for (sIdx in 0 until dpSongs.length()) {
                            val sObj = dpSongs.getJSONObject(sIdx)
                            val sId = sObj.optString("id", "s_${pId}_$sIdx")
                            val sTitle = sObj.optString("title", "Untitled")
                            val sArtists = sObj.optString("artistsText", "Unknown Artist")
                            val sDuration = sObj.optInt("duration", 210)
                            val sThumb = sObj.optString("thumbnailUrl", "").takeIf { it.isNotBlank() }

                            // Insert song metadata
                            val metadata = MediaMetadata(
                                id = sId,
                                title = sTitle,
                                artists = listOf(MediaMetadata.Artist(id = null, name = sArtists)),
                                duration = sDuration,
                                thumbnailUrl = sThumb,
                            )
                            database.dao.insert(metadata)

                            // Map to playlist
                            database.dao.insert(
                                PlaylistSongMap(
                                    playlistId = pId,
                                    songId = sId,
                                    position = sIdx,
                                )
                            )
                            importedSongs++
                        }
                    }
                }

                Timber.d("Desktop sync completed successfully: $importedPlaylists playlists, $importedSongs songs")
                SyncResult(
                    success = true,
                    message = "Successfully synchronized with $desktopDevice over local Wi-Fi!",
                    desktopDevice = desktopDevice,
                    syncedPlaylistsCount = desktopPlaylistsArray.length(),
                    syncedSongsCount = importedSongs,
                )
            }
        }
}
