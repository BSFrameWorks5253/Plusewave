/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.utils

import com.beatwave.music.db.entities.ArtistEntity
import com.beatwave.music.db.entities.SongEntity
import com.beatwave.music.db.entities.Song
import com.music.spotify.Spotify
import com.music.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.Duration
import java.util.concurrent.TimeUnit

data class UniversalPlaylist(
    val title: String,
    val songs: List<Song>,
    val coverUrl: String?,
    val source: String,
)

object UniversalPlaylistImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun unescapeHtml(text: String): String {
        if (!text.contains('&')) return text.trim()
        var result = text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8216;", "‘")
            .replace("&#8217;", "’")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")

        if (result.contains("&#")) {
            result = Regex("""&#(\d+);""").replace(result) { mr ->
                val code = mr.groupValues[1].toIntOrNull()
                if (code != null && code in 32..65535) code.toChar().toString() else mr.value
            }
            result = Regex("""&#x([0-9a-fA-F]+);""").replace(result) { mr ->
                val code = mr.groupValues[1].toIntOrNull(16)
                if (code != null && code in 32..65535) code.toChar().toString() else mr.value
            }
        }
        return result.trim()
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch page (HTTP ${response.code})")
            }
            response.body?.string().orEmpty()
        }
    }

    suspend fun parse(link: String): UniversalPlaylist = withContext(Dispatchers.IO) {
        val cleanLink = link.trim()
        when {
            cleanLink.contains("apple.com") -> parseAppleMusic(cleanLink)
            cleanLink.contains("amazon.") -> parseAmazonMusic(cleanLink)
            cleanLink.contains("spotify.com") || cleanLink.startsWith("spotify:") || cleanLink.matches(Regex("^[a-zA-Z0-9]{22}$")) -> parseSpotify(cleanLink)
            else -> {
                // If unrecognized URL, try Spotify first, then throw
                try {
                    parseSpotify(cleanLink)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Unsupported playlist link. Please enter a valid Spotify, Apple Music, or Amazon Music URL.")
                }
            }
        }
    }

    private suspend fun parseSpotify(link: String): UniversalPlaylist {
        val isAlbum = link.contains("/album/") || link.startsWith("spotify:album:")
        val type = if (isAlbum) "album" else "playlist"
        val id = when {
            link.contains("/album/") -> link.substringAfter("/album/").substringBefore("?").substringBefore("/")
            link.contains("/playlist/") -> link.substringAfter("/playlist/").substringBefore("?").substringBefore("/")
            link.startsWith("spotify:album:") -> link.substringAfter("spotify:album:").substringBefore("?")
            link.startsWith("spotify:playlist:") -> link.substringAfter("spotify:playlist:").substringBefore("?")
            else -> link.trim()
        }

        val html = fetchHtml("https://open.spotify.com/embed/$type/$id")
        val regex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""")
        val matchResult = regex.find(html) ?: throw Exception("Could not parse Spotify page data")
        val jsonStr = matchResult.groupValues[1]

        val jsonElement = json.parseToJsonElement(jsonStr)
        val props = jsonElement.jsonObject["props"]?.jsonObject
        val pageProps = props?.get("pageProps")?.jsonObject
        val state = pageProps?.get("state")?.jsonObject
        val data = state?.get("data")?.jsonObject
        val entity = data?.get("entity")?.jsonObject

        val rawName = entity?.get("name")?.jsonPrimitive?.contentOrNull ?: if (isAlbum) "Spotify Album" else "Spotify Playlist"
        val playlistName = unescapeHtml(rawName)
        val coverUrl = entity?.get("coverArt")?.jsonObject
            ?.get("sources")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull

        val trackList = entity?.get("trackList")?.jsonArray ?: emptyList()
        val fallbackSongs = trackList.mapNotNull { trackElement ->
            val trackObj = trackElement.jsonObject
            val title = trackObj["title"]?.jsonPrimitive?.contentOrNull?.let { unescapeHtml(it) } ?: return@mapNotNull null
            val artist = trackObj["subtitle"]?.jsonPrimitive?.contentOrNull?.let { unescapeHtml(it) } ?: "Unknown Artist"
            val durationMs = trackObj["duration"]?.jsonPrimitive?.longOrNull ?: 0L

            Song(
                song = SongEntity(
                    id = "",
                    title = title,
                    duration = (durationMs / 1000).toInt(),
                    thumbnailUrl = coverUrl,
                ),
                artists = listOf(ArtistEntity(id = "", name = artist)),
            )
        }

        val fullSongs = mutableListOf<Song>()
        if (type == "playlist") {
            try {
                var offset = 0
                val limit = 100
                while (true) {
                    val pageResult = Spotify.playlistTracks(id, limit = limit, offset = offset)
                    val page = pageResult.getOrNull() ?: break
                    if (page.items.isEmpty()) break
                    val pageSongs = page.items.mapNotNull { item ->
                        val track = item.track ?: return@mapNotNull null
                        Song(
                            song = SongEntity(
                                id = "",
                                title = track.name,
                                duration = track.durationMs / 1000,
                                thumbnailUrl = SpotifyMapper.getTrackThumbnail(track) ?: coverUrl,
                            ),
                            artists = track.artists.map { ArtistEntity(id = "", name = it.name) },
                        )
                    }
                    if (pageSongs.isEmpty()) break
                    fullSongs.addAll(pageSongs)
                    offset += limit
                    if (page.total > 0) {
                        if (offset >= page.total) break
                    } else {
                        if (page.items.size < limit) break
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching full Spotify playlist tracks via API")
            }
        }

        val songs = if (fullSongs.isNotEmpty()) fullSongs else fallbackSongs

        if (songs.isEmpty()) {
            throw Exception("No tracks found in the Spotify $type. Make sure it is public.")
        }

        return UniversalPlaylist(
            title = playlistName,
            songs = songs,
            coverUrl = coverUrl,
            source = "Spotify"
        )
    }

    private suspend fun parseAppleMusic(link: String): UniversalPlaylist {
        val html = fetchHtml(link)

        // Try extracting metadata from OpenGraph
        val ogTitle = Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)
        val ogImage = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)
        val rawTitle = ogTitle?.substringBefore(" by ")?.substringBefore(" - ") ?: "Apple Music Playlist"
        val playlistTitle = unescapeHtml(rawTitle)

        val songs = mutableListOf<Song>()

        // 1. Look for <script type="application/ld+json">
        val ldJsonRegex = Regex("""<script type="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val matches = ldJsonRegex.findAll(html)

        for (match in matches) {
            val jsonText = match.groupValues[1].trim()
            try {
                val element = json.parseToJsonElement(jsonText)
                extractAppleSongsFromElement(element, songs, ogImage)
            } catch (e: Exception) {
                Timber.d("Failed to parse Apple Music JSON-LD: ${e.message}")
            }
        }

        // 2. Fallback: Parse server-rendered HTML track items if JSON-LD didn't have tracks
        if (songs.isEmpty()) {
            val trackRowRegex = Regex(
                """class="[^"]*songs-list-row__song-name[^"]*"[^>]*>([^<]+)</div>.*?class="[^"]*songs-list-row__by-line[^"]*"[^>]*>.*?<a[^>]*>([^<]+)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )
            trackRowRegex.findAll(html).forEach { match ->
                val title = unescapeHtml(match.groupValues[1].trim())
                val artist = unescapeHtml(match.groupValues[2].trim())
                if (title.isNotBlank()) {
                    songs.add(
                        Song(
                            song = SongEntity(
                                id = "",
                                title = title,
                                duration = 0,
                                thumbnailUrl = ogImage,
                            ),
                            artists = listOf(ArtistEntity(id = "", name = artist)),
                        )
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            throw Exception("Could not find any tracks in the Apple Music playlist or album. Please verify the URL is public.")
        }

        return UniversalPlaylist(
            title = playlistTitle,
            songs = songs,
            coverUrl = ogImage,
            source = "Apple Music"
        )
    }

    private fun extractAppleSongsFromElement(element: JsonElement, outSongs: MutableList<Song>, fallbackCover: String?) {
        when (element) {
            is JsonObject -> {
                val type = element["@type"]?.jsonPrimitive?.contentOrNull
                if (type == "MusicPlaylist" || type == "MusicAlbum") {
                    val tracks = element["track"]?.jsonArray
                        ?: element["tracks"]?.jsonArray
                    tracks?.forEach { trackElem ->
                        val trackObj = trackElem.jsonObject
                        val rawTitle = trackObj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val title = unescapeHtml(rawTitle)
                        val rawArtist = extractArtistName(trackObj["byArtist"]) ?: "Unknown Artist"
                        val artist = unescapeHtml(rawArtist)
                        val durationStr = trackObj["duration"]?.jsonPrimitive?.contentOrNull
                        val durationSec = parseIsoDuration(durationStr)

                        outSongs.add(
                            Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                    duration = durationSec,
                                    thumbnailUrl = fallbackCover,
                                ),
                                artists = listOf(ArtistEntity(id = "", name = artist)),
                            )
                        )
                    }
                } else if (element.containsKey("@graph")) {
                    element["@graph"]?.jsonArray?.forEach {
                        extractAppleSongsFromElement(it, outSongs, fallbackCover)
                    }
                }
            }
            is JsonArray -> {
                element.forEach { extractAppleSongsFromElement(it, outSongs, fallbackCover) }
            }
            else -> {}
        }
    }

    private fun extractArtistName(artistElem: JsonElement?): String? {
        if (artistElem == null) return null
        return when (artistElem) {
            is JsonObject -> artistElem["name"]?.jsonPrimitive?.contentOrNull
            is JsonArray -> artistElem.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            else -> artistElem.jsonPrimitive.contentOrNull
        }
    }

    private suspend fun parseAmazonMusic(link: String): UniversalPlaylist {
        val html = fetchHtml(link)

        val ogTitle = Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)
        val ogImage = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)
        val rawTitle = ogTitle?.substringBefore(" on Amazon")?.substringBefore(" - ") ?: "Amazon Music Playlist"
        val playlistTitle = unescapeHtml(rawTitle)

        val songs = mutableListOf<Song>()

        // 1. Check JSON-LD
        val ldJsonRegex = Regex("""<script type="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        for (match in ldJsonRegex.findAll(html)) {
            val jsonText = match.groupValues[1].trim()
            try {
                val element = json.parseToJsonElement(jsonText)
                extractAppleSongsFromElement(element, songs, ogImage)
            } catch (_: Exception) {}
        }

        // 2. Fallback: Parse Amazon Music React props or meta tags
        if (songs.isEmpty()) {
            val trackRegex = Regex("""data-item-title="([^"]+)".*?data-item-artist="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            trackRegex.findAll(html).forEach { match ->
                val title = unescapeHtml(match.groupValues[1].trim())
                val artist = unescapeHtml(match.groupValues[2].trim())
                if (title.isNotBlank()) {
                    songs.add(
                        Song(
                            song = SongEntity(
                                id = "",
                                title = title,
                                duration = 0,
                                thumbnailUrl = ogImage,
                            ),
                            artists = listOf(ArtistEntity(id = "", name = artist)),
                        )
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            throw Exception("Could not find any tracks in the Amazon Music playlist. Please verify the URL is public.")
        }

        return UniversalPlaylist(
            title = playlistTitle,
            songs = songs,
            coverUrl = ogImage,
            source = "Amazon Music"
        )
    }

    private fun parseIsoDuration(durationStr: String?): Int {
        if (durationStr.isNullOrBlank()) return 0
        return try {
            Duration.parse(durationStr).seconds.toInt()
        } catch (_: Exception) {
            0
        }
    }
}
