/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import com.beatwave.music.utils.AppIconManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.beatwave.music.MainActivity
import com.beatwave.music.R
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.lyrics.LyricsEntry
import com.beatwave.music.lyrics.LyricsUtils
import com.beatwave.music.ui.component.shapes.ContinuousRoundedRectangle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class WidgetQueueItem(
    val index: Int,
    val title: String,
    val artist: String,
)

@Singleton
class vivimusicWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    // Cache for album art to avoid reloading
    private var cachedArtworkUri: String? = null
    private var cachedAlbumArt: Bitmap? = null
    private var cachedCircularAlbumArt: Bitmap? = null

    // Track last known playback state so widgets remain populated and interactive even when idle
    private var lastTitle: String = "Pulse Wave"
    private var lastArtist: String = context.getString(R.string.tap_to_play)
    private var lastArtworkUri: String? = null
    private var lastIsPlaying: Boolean = false
    private var lastIsLiked: Boolean = false
    private var lastDuration: Long = 0L
    private var lastCurrentPosition: Long = 0L
    private var lastMediaId: String? = null
    private var lastIsShuffle: Boolean = false
    private var lastRepeatMode: Int = 0
    private var lastAudioQuality: String? = null
    private var lastQueueItems: List<WidgetQueueItem> = emptyList()

    suspend fun updateWidgets(
        title: String = lastTitle,
        artist: String = lastArtist,
        artworkUri: String? = lastArtworkUri,
        isPlaying: Boolean = lastIsPlaying,
        isLiked: Boolean = lastIsLiked,
        duration: Long = lastDuration,
        currentPosition: Long = lastCurrentPosition,
        mediaId: String? = lastMediaId,
        isShuffle: Boolean = lastIsShuffle,
        repeatMode: Int = lastRepeatMode,
        audioQuality: String? = lastAudioQuality,
        queueItems: List<WidgetQueueItem> = lastQueueItems,
    ) {
        lastTitle = title
        lastArtist = artist
        lastArtworkUri = artworkUri
        lastIsPlaying = isPlaying
        lastIsLiked = isLiked
        lastDuration = duration
        lastCurrentPosition = currentPosition
        lastMediaId = mediaId
        lastIsShuffle = isShuffle
        lastRepeatMode = repeatMode
        lastAudioQuality = audioQuality
        lastQueueItems = queueItems

        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Use cached album art if URI hasn't changed, otherwise load new one
        val albumArt: Bitmap?
        val circularAlbumArt: Bitmap?

        if (artworkUri != null && artworkUri == cachedArtworkUri && cachedAlbumArt != null) {
            albumArt = cachedAlbumArt
            circularAlbumArt = cachedCircularAlbumArt
        } else {
            albumArt = artworkUri?.let { loadAlbumArt(it, 300) }
            circularAlbumArt = albumArt?.let { getCircularBitmap(it) }
            cachedArtworkUri = artworkUri
            cachedAlbumArt = albumArt
            cachedCircularAlbumArt = circularAlbumArt
        }

        // 1. Update main music player widgets
        val componentName = ComponentName(context, MusicWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isNotEmpty()) {
            widgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val config = WidgetConfig.load(context, widgetId)
                val views = createRemoteViewsForSize(
                    options,
                    config,
                    title,
                    artist,
                    albumArt,
                    isPlaying,
                    isLiked,
                    duration,
                    currentPosition
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        // 2. Update Apple Music styled Now Playing widgets
        val nowPlayingComponent = ComponentName(context, NowPlayingWidgetReceiver::class.java)
        appWidgetManager.getAppWidgetIds(nowPlayingComponent).forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val config = WidgetConfig.load(context, widgetId)
            appWidgetManager.updateAppWidget(
                widgetId,
                createNowPlayingRemoteViews(
                    options,
                    config,
                    title,
                    artist,
                    albumArt,
                    isPlaying,
                    isLiked,
                    duration,
                    currentPosition,
                ),
            )
        }

        // 3. Update lyrics widgets
        val lyricsComponent = ComponentName(context, LyricsWidgetReceiver::class.java)
        val lyricsWidgetIds = appWidgetManager.getAppWidgetIds(lyricsComponent)
        if (lyricsWidgetIds.isNotEmpty()) {
            val lines = loadLyricLines(mediaId)
            lyricsWidgetIds.forEach { widgetId ->
                val config = WidgetConfig.load(context, widgetId)
                appWidgetManager.updateAppWidget(
                    widgetId,
                    createLyricsRemoteViews(
                        appWidgetManager.getAppWidgetOptions(widgetId),
                        config,
                        title,
                        artist,
                        albumArt,
                        isPlaying,
                        lines,
                        currentPosition,
                    ),
                )
            }
        }

        // 4. Update turntable widgets
        val turntableComponentName = ComponentName(context, TurntableWidgetReceiver::class.java)
        val turntableWidgetIds = appWidgetManager.getAppWidgetIds(turntableComponentName)
        if (turntableWidgetIds.isNotEmpty()) {
            turntableWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val config = WidgetConfig.load(context, widgetId)
                val turntableViews = createTurntableRemoteViews(
                    options = options,
                    config = config,
                    title = title,
                    artist = artist,
                    albumArt = albumArt,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = duration,
                    currentPosition = currentPosition,
                    audioQuality = audioQuality
                )
                appWidgetManager.updateAppWidget(widgetId, turntableViews)
            }
        }

        // 5. Update Queue preview widgets
        val queueComponent = ComponentName(context, QueueWidgetReceiver::class.java)
        val queueWidgetIds = appWidgetManager.getAppWidgetIds(queueComponent)
        if (queueWidgetIds.isNotEmpty()) {
            queueWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val config = WidgetConfig.load(context, widgetId)
                appWidgetManager.updateAppWidget(
                    widgetId,
                    createQueuePreviewRemoteViews(
                        options,
                        config,
                        title,
                        artist,
                        albumArt,
                        isPlaying,
                        queueItems,
                    ),
                )
            }
        }

        // 6. Update Pinned Playlist widgets
        val playlistComponent = ComponentName(context, PlaylistWidgetReceiver::class.java)
        val playlistWidgetIds = appWidgetManager.getAppWidgetIds(playlistComponent)
        if (playlistWidgetIds.isNotEmpty()) {
            playlistWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val config = WidgetConfig.load(context, widgetId)
                val targetPlaylistId = config.playlistId ?: "favorites"
                val resolvedName = if (targetPlaylistId == "favorites" || targetPlaylistId == "liked") {
                    context.getString(R.string.liked_songs)
                } else {
                    runCatching { database.playlist(targetPlaylistId).first()?.playlist?.name }.getOrNull()
                        ?: context.getString(R.string.filter_playlists)
                }
                appWidgetManager.updateAppWidget(
                    widgetId,
                    createPlaylistRemoteViews(
                        options,
                        config,
                        albumArt,
                        resolvedName,
                        targetPlaylistId,
                    ),
                )
            }
        }
    }

    /**
     * Binds default/idle state with full pending intent click handlers so widgets
     * are never unresponsive even when MusicService is cold.
     */
    suspend fun renderIdleWidgets(
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
        widgetIdsToUpdate: IntArray? = null,
    ) {
        if (lastArtworkUri == null) {
            val eventWithSong = runCatching { database.events().first().firstOrNull() }.getOrNull()
            val s = eventWithSong?.song
            if (s != null) {
                lastTitle = s.song.title
                lastArtist = s.artists.joinToString(", ") { it.name }.ifEmpty { context.getString(R.string.tap_to_play) }
                lastArtworkUri = s.song.thumbnailUrl
                lastIsLiked = s.song.liked
                lastDuration = if (s.song.duration > 0) s.song.duration * 1000L else 0L
                lastMediaId = s.song.id
            }
        }

        updateWidgets(
            title = lastTitle,
            artist = lastArtist,
            artworkUri = lastArtworkUri,
            isPlaying = lastIsPlaying,
            isLiked = lastIsLiked,
            duration = lastDuration,
            currentPosition = lastCurrentPosition,
            mediaId = lastMediaId,
            isShuffle = lastIsShuffle,
            repeatMode = lastRepeatMode,
            audioQuality = lastAudioQuality,
            queueItems = lastQueueItems,
        )
    }

    private fun createRemoteViewsForSize(
        options: Bundle,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val card = cardSizeFor(options)

        return when {
            minWidth < 180 && minHeight < 100 -> {
                createCompactSquareRemoteViews(card, config, albumArt, isPlaying)
            }
            minWidth >= 180 && minHeight < 100 -> {
                createCompactWideRemoteViews(card, config, title, artist, albumArt, isPlaying, isLiked)
            }
            else -> {
                createRemoteViews(
                    card, config, title, artist, albumArt, isPlaying, isLiked, duration, currentPosition,
                )
            }
        }
    }

    private fun createRemoteViews(
        card: CardSize,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)
        val ink = onCardColorFor(config, albumArt)

        views.setImageViewBitmap(
            R.id.widget_player_bg,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        // Set song info
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        // Set album art with rounded corners
        if (config.showArtwork) {
            views.setViewVisibility(R.id.widget_album_art, android.view.View.VISIBLE)
            if (albumArt != null) {
                val roundedAlbumArt = getRoundedCornerBitmap(albumArt)
                views.setImageViewBitmap(R.id.widget_album_art, roundedAlbumArt)
            } else {
                views.setImageViewBitmap(R.id.widget_album_art, getRoundedDefaultIcon())
            }
        } else {
            views.setViewVisibility(R.id.widget_album_art, android.view.View.GONE)
        }

        // Set previous & next buttons
        val transportVisibility = if (config.showPrevNext) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_prev_button, transportVisibility)
        views.setViewVisibility(R.id.widget_next_button, transportVisibility)
        views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())

        // Set play/pause icon
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)
        views.setTextViewText(
            R.id.widget_play_pause_text,
            context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
        )

        // Set like button
        val likeVisibility = if (config.showLike) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_like_button, likeVisibility)
        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_like_button, likeIcon)
        views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())

        // Set Progress Level
        if (duration > 0) {
            val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
            views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
        } else {
            views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
        }

        // Shuffle & Repeat buttons
        val shuffleRepeatVisibility = if (config.showShuffleRepeat) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_shuffle_button, shuffleRepeatVisibility)
        views.setViewVisibility(R.id.widget_repeat_button, shuffleRepeatVisibility)
        if (config.showShuffleRepeat) {
            views.setOnClickPendingIntent(R.id.widget_shuffle_button, getShuffleIntent())
            views.setOnClickPendingIntent(R.id.widget_repeat_button, getRepeatIntent())
            val shuffleIcon = if (lastIsShuffle) R.drawable.shuffle_on else R.drawable.shuffle
            views.setImageViewResource(R.id.widget_shuffle_button, shuffleIcon)
            tint(views, R.id.widget_shuffle_button, ink)

            val repeatIcon = when (lastRepeatMode) {
                1 -> R.drawable.repeat_one_on
                2 -> R.drawable.repeat_on
                else -> R.drawable.repeat
            }
            views.setImageViewResource(R.id.widget_repeat_button, repeatIcon)
            tint(views, R.id.widget_repeat_button, ink)
        }

        // Lossless Badge
        if (config.showLosslessBadge && !lastAudioQuality.isNullOrBlank()) {
            views.setViewVisibility(R.id.widget_badge_lossless, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_badge_lossless, lastAudioQuality)
            views.setTextColor(R.id.widget_badge_lossless, ink)
        } else {
            views.setViewVisibility(R.id.widget_badge_lossless, android.view.View.GONE)
        }

        applyCardInk(
            views,
            ink,
            texts = listOf(
                R.id.widget_song_title,
                R.id.widget_artist_name,
                R.id.widget_play_pause_text,
            ),
            icons = listOf(
                R.id.widget_prev_button,
                R.id.widget_play_pause,
                R.id.widget_next_button,
                R.id.widget_like_button,
                R.id.widget_progress_track,
                R.id.widget_progress_fill,
            ),
            pillId = R.id.widget_play_pause_container,
        )

        // Set click intents
        views.setOnClickPendingIntent(R.id.widget_player_bg, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_album_art, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_song_title, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_artist_name, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())

        return views
    }

    private fun applyCardInk(
        views: RemoteViews,
        ink: Int,
        texts: List<Int>,
        icons: List<Int>,
        pillId: Int? = null,
    ) {
        texts.forEach { views.setTextColor(it, ink) }
        icons.forEach { tint(views, it, ink) }
        pillId?.let {
            views.setInt(
                it,
                "setBackgroundResource",
                if (ink == Color.WHITE) R.drawable.widget_pill_scrim_light
                else R.drawable.widget_pill_scrim_dark,
            )
        }
    }

    private suspend fun loadAlbumArt(artworkUri: String, size: Int = 200): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(size, size)
                    .allowHardware(false)
                    .crossfade(300)
                    .build()
                val result = imageLoader.execute(request)
                result.image?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getRoundedCornerBitmap(
        bitmap: Bitmap,
        cornerFraction: Float = ART_CORNER_FRACTION,
    ): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        drawContinuousRoundedRect(canvas, size.toFloat(), size.toFloat(), size * cornerFraction, paint)

        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        return output
    }

    private fun createCompactSquareRemoteViews(
        card: CardSize,
        config: WidgetConfig,
        albumArt: Bitmap?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_square)

        views.setImageViewBitmap(
            R.id.widget_compact_bg,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        // Set album art with rounded corners
        if (config.showArtwork) {
            views.setViewVisibility(R.id.widget_compact_album_art, android.view.View.VISIBLE)
            if (albumArt != null) {
                val roundedAlbumArt = getRoundedCornerBitmap(albumArt)
                views.setImageViewBitmap(R.id.widget_compact_album_art, roundedAlbumArt)
            } else {
                views.setImageViewBitmap(R.id.widget_compact_album_art, getRoundedDefaultIcon())
            }
        } else {
            views.setViewVisibility(R.id.widget_compact_album_art, android.view.View.GONE)
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_compact_play_pause, playPauseIcon)

        applyCardInk(
            views,
            Color.WHITE,
            texts = emptyList(),
            icons = listOf(R.id.widget_compact_play_pause),
            pillId = R.id.widget_compact_play_container,
        )

        // Set click intents
        views.setOnClickPendingIntent(R.id.widget_compact_bg, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_compact_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_compact_play_container, getPlayPauseIntent())

        return views
    }

    private fun createCompactWideRemoteViews(
        card: CardSize,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)
        val ink = onCardColorFor(config, albumArt)

        views.setImageViewBitmap(
            R.id.widget_wide_bg,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        // Set song info
        views.setTextViewText(R.id.widget_wide_song_title, title)
        views.setTextViewText(R.id.widget_wide_artist_name, artist)

        if (config.showArtwork) {
            views.setViewVisibility(R.id.widget_wide_album_art, android.view.View.VISIBLE)
            if (albumArt != null) {
                val roundedAlbumArt = getRoundedCornerBitmap(albumArt)
                views.setImageViewBitmap(R.id.widget_wide_album_art, roundedAlbumArt)
            } else {
                views.setImageViewBitmap(R.id.widget_wide_album_art, getRoundedDefaultIcon())
            }
        } else {
            views.setViewVisibility(R.id.widget_wide_album_art, android.view.View.GONE)
        }

        // Prev & Next buttons
        val transportVisibility = if (config.showPrevNext) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_wide_prev_button, transportVisibility)
        views.setViewVisibility(R.id.widget_wide_next_button, transportVisibility)
        views.setOnClickPendingIntent(R.id.widget_wide_prev_button, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_wide_next_button, getNextIntent())

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_wide_play_pause, playPauseIcon)
        views.setOnClickPendingIntent(R.id.widget_wide_play_pause, getPlayPauseIntent())

        // Like button
        val likeVisibility = if (config.showLike) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_wide_like_button, likeVisibility)
        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_wide_like_button, likeIcon)
        views.setOnClickPendingIntent(R.id.widget_wide_like_button, getLikeIntent())

        // Playlist quick-play button
        views.setOnClickPendingIntent(
            R.id.widget_wide_playlist_button,
            getPlayPlaylistIntent(config.playlistId ?: "favorites"),
        )

        applyCardInk(
            views,
            ink,
            texts = listOf(R.id.widget_wide_song_title, R.id.widget_wide_artist_name),
            icons = listOf(
                R.id.widget_wide_prev_button,
                R.id.widget_wide_play_pause,
                R.id.widget_wide_next_button,
                R.id.widget_wide_like_button,
                R.id.widget_wide_playlist_button,
            ),
        )

        // Set click intents
        views.setOnClickPendingIntent(R.id.widget_wide_bg, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_wide_album_art, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_wide_song_title, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_wide_artist_name, getOpenAppIntent(expandPlayer = true))

        return views
    }

    private fun createTurntableVinylRecord(
        card: CardSize,
        source: Bitmap?,
        isPlaying: Boolean,
        duration: Long = 0L,
        currentPosition: Long = 0L
    ): Bitmap {
        val scaleFactor = maxOf(1f, 540f / minOf(card.width, card.height).toFloat())
        val width = (card.width * scaleFactor).toInt().coerceIn(360, 1600)
        val height = (card.height * scaleFactor).toInt().coerceIn(360, 1600)
        val cornerPx = card.cornerPx * scaleFactor

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Dynamic palette extraction for ambient glow
        val palette = source?.let { runCatching { Palette.from(it).clearFilters().generate() }.getOrNull() }
        val vibrantColor = palette?.vibrantSwatch?.rgb
            ?: palette?.dominantSwatch?.rgb
            ?: Color.parseColor("#8A2BE2") // Electric violet
        val secondaryColor = palette?.lightVibrantSwatch?.rgb
            ?: palette?.mutedSwatch?.rgb
            ?: Color.parseColor("#00E5FF") // Cyber cyan

        // 2. Liquid Glass Squircle Card Background
        val squircleRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val outline = ContinuousRoundedRectangle(cornerPx).createOutline(
            size = Size(width.toFloat(), height.toFloat()),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )
        val squirclePath = (outline as? Outline.Generic)?.path?.asAndroidPath()
            ?: android.graphics.Path().apply {
                addRoundRect(squircleRect, cornerPx, cornerPx, android.graphics.Path.Direction.CW)
            }

        canvas.save()
        canvas.clipPath(squirclePath)

        // 2a. Base deep dark glass fill
        val glassBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 12, 13, 19)
        }
        canvas.drawRect(squircleRect, glassBasePaint)

        // Determine vinyl disc placement based on aspect ratio
        val isWide = width.toFloat() / height.toFloat() >= 1.25f
        val vinylCenterX: Float
        val vinylCenterY: Float
        val vinylRadius: Float

        if (isWide) {
            vinylCenterY = height / 2f
            vinylCenterX = width - (height * 0.50f)
            vinylRadius = height * 0.41f
        } else {
            vinylCenterX = width / 2f
            vinylCenterY = height * 0.50f
            vinylRadius = minOf(width, height) * 0.38f
        }

        // 2b. Dynamic Ambient Glow Bloom behind the floating vinyl
        val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                vinylCenterX, vinylCenterY, vinylRadius * 1.65f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(vibrantColor, 180),
                    ColorUtils.setAlphaComponent(secondaryColor, 85),
                    ColorUtils.setAlphaComponent(vibrantColor, 20),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(squircleRect, bloomPaint)

        // 2c. Frosted Glass Top-Down Specular Highlight
        val glassSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.35f,
                intArrayOf(
                    Color.argb(60, 255, 255, 255),
                    Color.argb(18, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(squircleRect, glassSheenPaint)

        // 3. 3D Floating Vinyl Platter
        // 3a. Deep Ambient Elevation Drop Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                vinylCenterX, vinylCenterY + vinylRadius * 0.08f, vinylRadius * 1.25f,
                intArrayOf(
                    Color.argb(165, 0, 0, 0),
                    Color.argb(100, 0, 0, 0),
                    Color.argb(30, 0, 0, 0),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.70f, 0.88f, 0.98f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY + vinylRadius * 0.08f, vinylRadius * 1.25f, shadowPaint)

        // 3b. Floating Ambient Rim Underglow
        val neonUnderglowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = vinylRadius * 0.12f
            shader = RadialGradient(
                vinylCenterX, vinylCenterY, vinylRadius * 1.08f,
                intArrayOf(
                    Color.TRANSPARENT,
                    ColorUtils.setAlphaComponent(vibrantColor, 125),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.85f, 0.98f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius * 1.01f, neonUnderglowPaint)

        // 3c. Deep carbon vinyl disc body
        val vinylBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                vinylCenterX, vinylCenterY, vinylRadius,
                intArrayOf(
                    Color.parseColor("#16161C"),
                    Color.parseColor("#0E0E12"),
                    Color.parseColor("#08080B")
                ),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius, vinylBasePaint)

        // 3d. Outer edge bevel and run-in lip
        val outerBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(60, 255, 255, 255)
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius - 1.25f, outerBevelPaint)

        val runInLipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb(35, 255, 255, 255)
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius - 5f, runInLipPaint)

        // 3e. Concentric Phonograph Grooves with Track Bands
        val labelRadius = vinylRadius * 0.44f
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val totalGrooveWidth = (vinylRadius - 7f) - (labelRadius + 4f)
        val trackBands = 4
        val bandWidth = totalGrooveWidth / trackBands
        for (b in 0 until trackBands) {
            val bandStart = labelRadius + 4f + (b * bandWidth)
            val bandEnd = bandStart + bandWidth - 3f
            var gr = bandStart + 2f
            var alt = 0
            while (gr < bandEnd) {
                groovePaint.color = if (alt % 2 == 0) Color.argb(26, 255, 255, 255) else Color.argb(12, 255, 255, 255)
                canvas.drawCircle(vinylCenterX, vinylCenterY, gr, groovePaint)
                gr += 3.2f
                alt++
            }
            // Distinct shiny gap between tracks
            groovePaint.color = Color.argb(42, 255, 255, 255)
            canvas.drawCircle(vinylCenterX, vinylCenterY, bandEnd + 1.2f, groovePaint)
        }

        // 3f. Anisotropic Specular Butterfly Reflection Sheen
        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = SweepGradient(
                vinylCenterX, vinylCenterY,
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(62, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb(62, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.22f, 0.42f, 0.50f, 0.72f, 1f)
            )
        }
        canvas.save()
        val grooveClip = android.graphics.Path().apply {
            addCircle(vinylCenterX, vinylCenterY, vinylRadius - 3f, android.graphics.Path.Direction.CW)
            addCircle(vinylCenterX, vinylCenterY, labelRadius + 2f, android.graphics.Path.Direction.CCW)
        }
        canvas.clipPath(grooveClip)
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius, sweepPaint)
        canvas.restore()

        // 3g. Center Circular Album Art Label
        val artSource = source ?: getCircularDefaultIcon()
        val squareArt = if (artSource.width != artSource.height) {
            val minDim = minOf(artSource.width, artSource.height)
            Bitmap.createBitmap(artSource, (artSource.width - minDim) / 2, (artSource.height - minDim) / 2, minDim, minDim)
        } else artSource

        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(squareArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val artMatrix = android.graphics.Matrix().apply {
            val s = (labelRadius * 2f) / squareArt.width
            setScale(s, s)
            postTranslate(vinylCenterX - labelRadius, vinylCenterY - labelRadius)
        }
        artPaint.shader.setLocalMatrix(artMatrix)
        canvas.drawCircle(vinylCenterX, vinylCenterY, labelRadius, artPaint)

        // 3h. Label Rim Bevel & Center Spindle Pin
        val labelRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(125, 235, 235, 245)
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, labelRadius, labelRimPaint)

        val spindleHoleRadius = vinylRadius * 0.085f
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#060608")
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, spindleHoleRadius, spindlePaint)

        val spindleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = RadialGradient(
                vinylCenterX, vinylCenterY, spindleHoleRadius,
                intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#8E8E98")),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, spindleHoleRadius, spindleRingPaint)

        // 3i. Outer Rim Glowing Playback Progress Arc
        if (duration > 0 && currentPosition > 0) {
            val progressFraction = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
                color = vibrantColor
            }
            val arcRect = RectF(
                vinylCenterX - vinylRadius - 2.5f,
                vinylCenterY - vinylRadius - 2.5f,
                vinylCenterX + vinylRadius + 2.5f,
                vinylCenterY + vinylRadius + 2.5f
            )
            canvas.drawArc(arcRect, -90f, progressFraction * 360f, false, arcPaint)

            val headAngleRad = Math.toRadians((-90.0 + (progressFraction * 360.0)))
            val headX = (vinylCenterX + (vinylRadius + 2.5f) * Math.cos(headAngleRad)).toFloat()
            val headY = (vinylCenterY + (vinylRadius + 2.5f) * Math.sin(headAngleRad)).toFloat()
            val headDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(headX, headY, 3.5f, headDotPaint)
        }

        // 3j. Subtle neon equalizer bars when actively playing
        if (isPlaying) {
            val eqX = vinylCenterX - vinylRadius * 0.70f
            val eqY = vinylCenterY + vinylRadius * 0.58f
            val barWidth = vinylRadius * 0.028f
            val barSpacing = vinylRadius * 0.045f
            val barHeights = floatArrayOf(vinylRadius * 0.08f, vinylRadius * 0.18f, vinylRadius * 0.12f, vinylRadius * 0.22f)
            val eqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(secondaryColor, 220)
                strokeCap = Paint.Cap.ROUND
                strokeWidth = barWidth
            }
            for (i in barHeights.indices) {
                val bx = eqX + (i * barSpacing)
                val byTop = eqY - barHeights[i]
                canvas.drawLine(bx, eqY, bx, byTop, eqPaint)
            }
        }

        // 4. Precision Hi-Fi Tonearm Stylus
        drawStylusTonearm(canvas, vinylCenterX, vinylCenterY, vinylRadius, isPlaying)

        // 5. Iridescent Specular Glass Border Rim
        val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.argb(105, 255, 255, 255),
                    Color.argb(32, 255, 255, 255),
                    Color.argb(16, 255, 255, 255),
                    Color.argb(65, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.3f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(squirclePath, glassBorderPaint)

        canvas.restore()

        return output
    }

    private fun createTurntableVinylRecord(source: Bitmap, isPlaying: Boolean): Bitmap {
        val defaultCard = CardSize(width = 600, height = 600, cornerPx = 48f)
        return createTurntableVinylRecord(defaultCard, source, isPlaying)
    }

    private fun drawStylusTonearm(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        isPlaying: Boolean
    ) {
        val pivotX = cx + radius * 0.76f
        val pivotY = cy - radius * 0.70f
        val pivotRadius = radius * 0.14f

        // 1. Pivot Base shadow & metallic gimbal base
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX + 3f, pivotY + 4f, pivotRadius, shadowPaint)

        val pivotBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                pivotX, pivotY, pivotRadius,
                intArrayOf(Color.parseColor("#F0F0F5"), Color.parseColor("#35353C")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(pivotX, pivotY, pivotRadius, pivotBasePaint)

        val pivotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1A1A20")
            strokeWidth = 2.5f
        }
        canvas.drawCircle(pivotX, pivotY, pivotRadius, pivotRingPaint)

        // Glowing Status LED on Gimbal Pivot (Neon Cyan when playing, Amber when paused)
        val ledColor = if (isPlaying) Color.parseColor("#00F2FE") else Color.parseColor("#FF9100")
        val ledGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(ledColor, 80)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX, pivotY, pivotRadius * 0.55f, ledGlowPaint)

        val ledCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ledColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX, pivotY, pivotRadius * 0.30f, ledCorePaint)

        // 2. Tonearm Rod Coordinates
        val targetX = if (isPlaying) cx + radius * 0.36f else cx + radius * 0.86f
        val targetY = if (isPlaying) cy - radius * 0.10f else cy - radius * 0.18f

        val elbowX = if (isPlaying) cx + radius * 0.70f else cx + radius * 0.88f
        val elbowY = if (isPlaying) cy - radius * 0.38f else cy - radius * 0.44f

        val armPath = android.graphics.Path().apply {
            moveTo(pivotX, pivotY)
            lineTo(elbowX, elbowY)
            lineTo(targetX, targetY)
        }

        // Arm Shadow
        val armShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.038f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(100, 0, 0, 0)
        }
        val shadowOffsetPath = android.graphics.Path().apply {
            moveTo(pivotX + 4f, pivotY + 6f)
            lineTo(elbowX + 4f, elbowY + 6f)
            lineTo(targetX + 4f, targetY + 6f)
        }
        canvas.drawPath(shadowOffsetPath, armShadowPaint)

        // Arm Rod
        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.038f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#EDEDF4")
        }
        canvas.drawPath(armPath, armPaint)

        // 3. Cartridge Headshell at the tip
        val headAngle = if (isPlaying) 34.0 else 6.0
        val headLength = radius * 0.15f
        val headWidth = radius * 0.06f

        canvas.save()
        canvas.translate(targetX, targetY)
        canvas.rotate(headAngle.toFloat())

        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#181820")
            style = Paint.Style.FILL
        }
        val headRect = RectF(-headWidth / 2f, 0f, headWidth / 2f, headLength)
        canvas.drawRoundRect(headRect, 4f, 4f, headPaint)

        // Cartridge metallic top plate highlight
        val headPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C8C8D2")
            style = Paint.Style.FILL
        }
        val headPlateRect = RectF(-headWidth / 2f + 1f, 1f, headWidth / 2f - 1f, headLength * 0.45f)
        canvas.drawRoundRect(headPlateRect, 2f, 2f, headPlatePaint)

        // Stylus Laser-Red LED Needle Tip
        val stylusTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF2647")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(0f, headLength - 2f, radius * 0.020f, stylusTipPaint)

        canvas.restore()
    }

    private fun createTurntableRemoteViews(
        options: Bundle,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0L,
        currentPosition: Long = 0L,
        audioQuality: String? = null
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)
        val card = cardSizeFor(options)

        val turntableComposite = createTurntableVinylRecord(
            card = card,
            source = albumArt,
            isPlaying = isPlaying,
            duration = duration,
            currentPosition = currentPosition
        )
        views.setImageViewBitmap(R.id.widget_turntable_album_art, turntableComposite)

        views.setTextViewText(R.id.widget_turntable_title, title)
        views.setTextViewText(R.id.widget_turntable_artist, artist)

        // Audio quality / lossless badge
        if (config.showLosslessBadge && !audioQuality.isNullOrBlank()) {
            views.setViewVisibility(R.id.widget_turntable_badge, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_turntable_badge, audioQuality)
        } else {
            views.setViewVisibility(R.id.widget_turntable_badge, android.view.View.GONE)
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_turntable_play_pause, playPauseIcon)

        val transportVisibility =
            if (config.showPrevNext) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_turntable_prev_button, transportVisibility)
        views.setViewVisibility(R.id.widget_turntable_next_button, transportVisibility)

        val likeVisibility =
            if (config.showLike) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_turntable_like_button, likeVisibility)
        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_turntable_like_button, likeIcon)
        views.setInt(
            R.id.widget_turntable_like_button,
            "setColorFilter",
            if (isLiked) Color.parseColor("#FF2D55") else Color.parseColor("#CCFFFFFF")
        )

        // Set click intents
        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_turntable_info_container, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_turntable_play_container, getTurntablePlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_prev_button, getTurntablePreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_next_button, getTurntableNextIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_like_button, getTurntableLikeIntent())

        return views
    }

    private fun getActiveAppIconDrawable(): Drawable {
        val pm = context.packageManager
        for (alias in AppIconManager.ALL_ALIASES) {
            val component = ComponentName(context.packageName, alias)
            if (pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                try {
                    return pm.getActivityIcon(component)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        return ContextCompat.getDrawable(context, R.mipmap.ic_launcher_glass)
            ?: pm.getApplicationIcon(context.packageName)
    }

    private fun getCircularDefaultIcon(): Bitmap {
        val drawable = getActiveAppIconDrawable()
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getCircularBitmap(bitmap)
    }

    private fun getRoundedDefaultIcon(
        cornerFraction: Float = ART_CORNER_FRACTION,
    ): Bitmap {
        val drawable = getActiveAppIconDrawable()
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getRoundedCornerBitmap(bitmap, cornerFraction)
    }

    private fun backgroundColorFor(config: WidgetConfig, albumArt: Bitmap?): Int =
        when (config.background) {
            WidgetBackground.ALBUM_TINT -> tintColorFor(albumArt)
            WidgetBackground.DARK -> Color.parseColor("#141414")
            WidgetBackground.LIGHT -> Color.parseColor("#F2EFED")
            WidgetBackground.IMAGE -> tintColorFor(albumArt)
            WidgetBackground.SYSTEM_DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    if (isNight) {
                        ContextCompat.getColor(context, android.R.color.system_neutral1_900)
                    } else {
                        ContextCompat.getColor(context, android.R.color.system_accent1_100)
                    }
                } else {
                    tintColorFor(albumArt)
                }
            }
        }

    private fun loadConfiguredBackground(config: WidgetConfig, card: CardSize): Bitmap? {
        if (config.background != WidgetBackground.IMAGE) return null
        if (config.opacity <= 0.02f) {
            return Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
        }
        val uri = config.imageUri ?: return null
        val source = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri)).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull() ?: return null

        val output = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
        val scale = maxOf(
            card.width.toFloat() / source.width,
            card.height.toFloat() / source.height,
        )
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (card.width - source.width * scale) / 2f,
                (card.height - source.height * scale) / 2f,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            alpha = (config.opacity.coerceIn(0f, 1f) * 255).toInt()
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .apply { setLocalMatrix(matrix) }
        }
        drawContinuousRoundedRect(
            Canvas(output),
            card.width.toFloat(),
            card.height.toFloat(),
            card.cornerPx,
            paint,
        )
        return output
    }

    private fun tintColorFor(albumArt: Bitmap?): Int {
        val fallback = Color.parseColor("#B03A3A")
        val bitmap = albumArt ?: return fallback
        val swatch = Palette.from(bitmap).clearFilters().generate().let { palette ->
            palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        } ?: return fallback

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        hsl[1] = hsl[1].coerceIn(0.35f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.32f, 0.52f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun drawContinuousRoundedRect(
        canvas: Canvas,
        width: Float,
        height: Float,
        cornerRadius: Float,
        paint: Paint,
    ) {
        val outline = ContinuousRoundedRectangle(cornerRadius).createOutline(
            size = Size(width, height),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f),
        )
        val path = (outline as? Outline.Generic)?.path?.asAndroidPath()
        if (path != null) {
            canvas.drawPath(path, paint)
        } else {
            canvas.drawRoundRect(RectF(0f, 0f, width, height), cornerRadius, cornerRadius, paint)
        }
    }

    private data class CardSize(val width: Int, val height: Int, val cornerPx: Float)

    private fun cardSizeFor(options: Bundle): CardSize {
        val density = context.resources.displayMetrics.density
        val portrait = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_PORTRAIT
        val widthDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
        )
        val heightDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
        )
        val width = ((if (widthDp > 0) widthDp else 250) * density).toInt().coerceIn(1, 1600)
        val height = ((if (heightDp > 0) heightDp else 110) * density).toInt().coerceIn(1, 1600)

        val radiusDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) /
                    density
            }.getOrDefault(CARD_CORNER_DP)
        } else {
            CARD_CORNER_DP
        }
        return CardSize(width, height, radiusDp.coerceIn(8f, 28f) * density)
    }

    private fun gradientColorsFor(config: WidgetConfig, albumArt: Bitmap?): Pair<Int, Int> {
        val base = backgroundColorFor(config, albumArt)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base, hsl)

        val top = ColorUtils.HSLToColor(
            floatArrayOf(hsl[0], hsl[1], (hsl[2] + 0.10f).coerceAtMost(0.62f)),
        )
        val bottom = ColorUtils.HSLToColor(
            floatArrayOf(hsl[0], hsl[1], (hsl[2] - 0.10f).coerceAtLeast(0.16f)),
        )
        return top to bottom
    }

    private fun createCardBackground(
        config: WidgetConfig,
        albumArt: Bitmap?,
        card: CardSize,
    ): Bitmap {
        val width = card.width
        val height = card.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (config.opacity <= 0.02f) {
            return bitmap
        }

        val canvas = Canvas(bitmap)
        val cornerPx = card.cornerPx

        // 1. Palette Extraction for Dynamic Bloom
        val palette = albumArt?.let { art ->
            try {
                androidx.palette.graphics.Palette.from(art).maximumColorCount(16).generate()
            } catch (e: Exception) {
                null
            }
        }
        val vibrant = palette?.vibrantSwatch?.rgb
            ?: palette?.dominantSwatch?.rgb
            ?: palette?.lightVibrantSwatch?.rgb
            ?: Color.parseColor("#00E5FF")
        val secondary = palette?.darkVibrantSwatch?.rgb
            ?: palette?.mutedSwatch?.rgb
            ?: Color.parseColor("#7C4DFF")

        val alphaMultiplier = config.opacity.coerceIn(0f, 1f)

        // Clip to Continuous Rounded Rect (Squircle)
        val outline = ContinuousRoundedRectangle(cornerPx).createOutline(
            size = Size(width.toFloat(), height.toFloat()),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )
        val squirclePath = (outline as? Outline.Generic)?.path?.asAndroidPath()
            ?: android.graphics.Path().apply {
                addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerPx, cornerPx, android.graphics.Path.Direction.CW)
            }

        canvas.save()
        canvas.clipPath(squirclePath)

        // 2a. Deep Frosted Ultra Liquid Glass Base
        val (topBase, bottomBase) = gradientColorsFor(config, albumArt)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width * 0.4f, height.toFloat(),
                ColorUtils.setAlphaComponent(topBase, (alphaMultiplier * 240).toInt()),
                ColorUtils.setAlphaComponent(bottomBase, (alphaMultiplier * 245).toInt()),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)

        // 2b. Dynamic Ambient Bloom (Vibrant Palette Radial Glow)
        val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val bloomCenterX = if (width > height) height * 0.5f else width * 0.5f
            val bloomCenterY = height * 0.5f
            val bloomRadius = maxOf(width, height) * 0.75f
            shader = RadialGradient(
                bloomCenterX, bloomCenterY, bloomRadius,
                intArrayOf(
                    ColorUtils.setAlphaComponent(vibrant, (alphaMultiplier * 140).toInt()),
                    ColorUtils.setAlphaComponent(secondary, (alphaMultiplier * 60).toInt()),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bloomPaint)

        // 2c. 3D Specular Sheen (Diagonal liquid light beam from top-left)
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width * 0.65f, height * 0.75f,
                intArrayOf(
                    Color.argb((alphaMultiplier * 55).toInt(), 255, 255, 255),
                    Color.argb((alphaMultiplier * 10).toInt(), 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.35f, 0.85f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sheenPaint)

        canvas.restore()

        // 2d. Dual-layer Liquid Glass Rim & Specular Bevel Border
        val rimAlpha = (alphaMultiplier * 130).toInt()
        if (rimAlpha > 0) {
            val outerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (height * 0.014f).coerceIn(1.2f, 3.5f)
                shader = LinearGradient(
                    0f, 0f, 0f, height * 0.5f,
                    Color.argb(rimAlpha, 255, 255, 255),
                    Color.argb((rimAlpha * 0.2f).toInt(), 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
            drawContinuousRoundedRect(canvas, width.toFloat(), height.toFloat(), cornerPx, outerRim)
        }

        return bitmap
    }

    private fun onCardColorFor(config: WidgetConfig, albumArt: Bitmap?): Int {
        if (config.opacity <= 0.35f) return Color.WHITE
        if (config.background == WidgetBackground.IMAGE) return Color.WHITE
        val luminance = ColorUtils.calculateLuminance(backgroundColorFor(config, albumArt))
        return if (luminance > 0.5) Color.parseColor("#12100F") else Color.WHITE
    }

    private enum class NowPlayingForm(val minWidthDp: Float, val minHeightDp: Float) {
        ROW(180f, 40f),
        SQUARE(110f, 110f),
        WIDE(250f, 110f),
    }

    private fun createNowPlayingRemoteViews(
        options: Bundle,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
    ): RemoteViews {
        val card = cardSizeFor(options)
        val build = { form: NowPlayingForm ->
            buildNowPlayingForm(
                form, card, config, title, artist, albumArt, isPlaying, isLiked, duration, currentPosition,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                NowPlayingForm.entries.associate { form ->
                    SizeF(form.minWidthDp, form.minHeightDp) to build(form)
                }
            )
        }

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val form = when {
            minHeight < 100 -> NowPlayingForm.ROW
            minWidth < 180 -> NowPlayingForm.SQUARE
            else -> NowPlayingForm.WIDE
        }
        return build(form)
    }

    private fun buildNowPlayingForm(
        form: NowPlayingForm,
        card: CardSize,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
    ): RemoteViews {
        val layout = when (form) {
            NowPlayingForm.ROW -> R.layout.widget_now_playing_row
            NowPlayingForm.SQUARE -> R.layout.widget_now_playing_square
            NowPlayingForm.WIDE -> R.layout.widget_now_playing_wide
        }
        val views = RemoteViews(context.packageName, layout)

        val bgId = when (form) {
            NowPlayingForm.ROW -> R.id.widget_np_row_bg
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_bg
            NowPlayingForm.WIDE -> R.id.widget_np_wide_bg
        }
        val artId = when (form) {
            NowPlayingForm.ROW -> R.id.widget_np_row_album_art
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_album_art
            NowPlayingForm.WIDE -> R.id.widget_np_wide_album_art
        }
        val titleId = when (form) {
            NowPlayingForm.ROW -> R.id.widget_np_row_title
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_title
            NowPlayingForm.WIDE -> R.id.widget_np_wide_title
        }
        val artistId = when (form) {
            NowPlayingForm.ROW -> R.id.widget_np_row_artist
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_artist
            NowPlayingForm.WIDE -> R.id.widget_np_wide_artist
        }
        val iconId = when (form) {
            NowPlayingForm.ROW -> R.id.widget_np_row_play_pause
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_play_pause
            NowPlayingForm.WIDE -> R.id.widget_np_wide_play_pause
        }

        views.setImageViewBitmap(
            bgId,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        if (config.showArtwork) {
            views.setViewVisibility(artId, android.view.View.VISIBLE)
            views.setImageViewBitmap(
                artId,
                albumArt?.let { getRoundedCornerBitmap(it) } ?: getRoundedDefaultIcon(),
            )
            sizeArtwork(views, artId, form, card)
        } else {
            views.setViewVisibility(artId, android.view.View.GONE)
        }

        views.setTextViewText(titleId, title)
        views.setTextViewText(artistId, artist)
        views.setImageViewResource(
            iconId,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        val ink = onCardColorFor(config, albumArt)
        val labelId = if (form == NowPlayingForm.WIDE) R.id.widget_np_wide_label else null
        listOfNotNull(titleId, artistId, labelId).forEach { views.setTextColor(it, ink) }
        val glyphId = when (form) {
            NowPlayingForm.ROW -> null
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_glyph
            NowPlayingForm.WIDE -> R.id.widget_np_wide_glyph
        }
        listOfNotNull(iconId, glyphId).forEach { tint(views, it, ink) }

        // Progress bar for Square and Wide
        val progressFillId = when (form) {
            NowPlayingForm.ROW -> null
            NowPlayingForm.SQUARE -> R.id.widget_np_sq_progress_fill
            NowPlayingForm.WIDE -> R.id.widget_np_wide_progress_fill
        }
        progressFillId?.let { fillId ->
            val level =
                if (duration > 0) {
                    ((currentPosition.toDouble() / duration) * 10000).toInt().coerceIn(0, 10000)
                } else {
                    0
                }
            views.setInt(fillId, "setImageLevel", level)
            tint(views, fillId, ink)
            tint(
                views,
                if (form == NowPlayingForm.WIDE) R.id.widget_np_wide_progress_track
                else R.id.widget_np_sq_progress_track,
                ink,
            )
        }

        val transportVisibility =
            if (config.showPrevNext) android.view.View.VISIBLE else android.view.View.GONE
        val likeVisibility =
            if (config.showLike) android.view.View.VISIBLE else android.view.View.GONE
        val likeIcon =
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav

        when (form) {
            NowPlayingForm.ROW -> {
                views.setOnClickPendingIntent(iconId, getPlayPauseIntent())
                views.setViewVisibility(R.id.widget_np_row_next, transportVisibility)
                views.setViewVisibility(R.id.widget_np_row_prev, transportVisibility)
                views.setOnClickPendingIntent(R.id.widget_np_row_next, getNextIntent())
                views.setOnClickPendingIntent(R.id.widget_np_row_prev, getPreviousIntent())
                tint(views, R.id.widget_np_row_next, ink)
                tint(views, R.id.widget_np_row_prev, ink)

                views.setViewVisibility(R.id.widget_np_row_like, likeVisibility)
                views.setImageViewResource(R.id.widget_np_row_like, likeIcon)
                views.setOnClickPendingIntent(R.id.widget_np_row_like, getLikeIntent())
                tint(views, R.id.widget_np_row_like, ink)
            }
            NowPlayingForm.WIDE -> {
                views.setViewVisibility(R.id.widget_np_wide_prev, transportVisibility)
                views.setViewVisibility(R.id.widget_np_wide_next, transportVisibility)
                views.setOnClickPendingIntent(R.id.widget_np_wide_prev, getPreviousIntent())
                views.setOnClickPendingIntent(R.id.widget_np_wide_next, getNextIntent())
                tint(views, R.id.widget_np_wide_prev, ink)
                tint(views, R.id.widget_np_wide_next, ink)

                views.setViewVisibility(R.id.widget_np_wide_like, likeVisibility)
                views.setImageViewResource(R.id.widget_np_wide_like, likeIcon)
                views.setOnClickPendingIntent(R.id.widget_np_wide_like, getLikeIntent())
                tint(views, R.id.widget_np_wide_like, ink)

                // Playlist quick-play button
                views.setOnClickPendingIntent(
                    R.id.widget_np_wide_playlist,
                    getPlayPlaylistIntent(config.playlistId ?: "favorites"),
                )
                tint(views, R.id.widget_np_wide_playlist, ink)

                // Shuffle & Repeat buttons
                val shuffleRepeatVisibility = if (config.showShuffleRepeat) android.view.View.VISIBLE else android.view.View.GONE
                views.setViewVisibility(R.id.widget_np_wide_shuffle, shuffleRepeatVisibility)
                views.setViewVisibility(R.id.widget_np_wide_repeat, shuffleRepeatVisibility)
                if (config.showShuffleRepeat) {
                    views.setOnClickPendingIntent(R.id.widget_np_wide_shuffle, getShuffleIntent())
                    views.setOnClickPendingIntent(R.id.widget_np_wide_repeat, getRepeatIntent())
                    val shuffleIcon = if (lastIsShuffle) R.drawable.shuffle_on else R.drawable.shuffle
                    views.setImageViewResource(R.id.widget_np_wide_shuffle, shuffleIcon)
                    tint(views, R.id.widget_np_wide_shuffle, ink)

                    val repeatIcon = when (lastRepeatMode) {
                        1 -> R.drawable.repeat_one_on
                        2 -> R.drawable.repeat_on
                        else -> R.drawable.repeat
                    }
                    views.setImageViewResource(R.id.widget_np_wide_repeat, repeatIcon)
                    tint(views, R.id.widget_np_wide_repeat, ink)
                }

                // Lossless Badge
                if (config.showLosslessBadge && !lastAudioQuality.isNullOrBlank()) {
                    views.setViewVisibility(R.id.widget_np_wide_lossless, android.view.View.VISIBLE)
                    views.setTextViewText(R.id.widget_np_wide_lossless, lastAudioQuality)
                    views.setTextColor(R.id.widget_np_wide_lossless, ink)
                } else {
                    views.setViewVisibility(R.id.widget_np_wide_lossless, android.view.View.GONE)
                }

                views.setTextViewText(
                    R.id.widget_np_wide_pill_text,
                    context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
                )
                views.setTextColor(R.id.widget_np_wide_pill_text, ink)
                views.setInt(
                    R.id.widget_np_wide_pill,
                    "setBackgroundResource",
                    if (ink == Color.WHITE) R.drawable.widget_pill_scrim_light
                    else R.drawable.widget_pill_scrim_dark,
                )
                views.setOnClickPendingIntent(R.id.widget_np_wide_pill, getPlayPauseIntent())
            }
            NowPlayingForm.SQUARE -> {
                views.setViewVisibility(R.id.widget_np_sq_prev, transportVisibility)
                views.setViewVisibility(R.id.widget_np_sq_next, transportVisibility)
                views.setOnClickPendingIntent(R.id.widget_np_sq_prev, getPreviousIntent())
                views.setOnClickPendingIntent(R.id.widget_np_sq_next, getNextIntent())
                tint(views, R.id.widget_np_sq_prev, ink)
                tint(views, R.id.widget_np_sq_next, ink)

                views.setViewVisibility(R.id.widget_np_sq_like, likeVisibility)
                views.setImageViewResource(R.id.widget_np_sq_like, likeIcon)
                views.setOnClickPendingIntent(R.id.widget_np_sq_like, getLikeIntent())
                tint(views, R.id.widget_np_sq_like, ink)

                views.setTextViewText(
                    R.id.widget_np_sq_pill_text,
                    context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
                )
                views.setTextColor(R.id.widget_np_sq_pill_text, ink)
                views.setInt(
                    R.id.widget_np_sq_pill,
                    "setBackgroundResource",
                    if (ink == Color.WHITE) R.drawable.widget_pill_scrim_light
                    else R.drawable.widget_pill_scrim_dark,
                )
                views.setOnClickPendingIntent(R.id.widget_np_sq_pill, getPlayPauseIntent())
            }
        }

        views.setOnClickPendingIntent(bgId, getOpenAppIntent())
        views.setOnClickPendingIntent(artId, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(titleId, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(artistId, getOpenAppIntent(expandPlayer = true))
        return views
    }

    private fun tint(views: RemoteViews, viewId: Int, color: Int) {
        views.setInt(viewId, "setColorFilter", color)
    }

    private fun sizeArtwork(
        views: RemoteViews,
        artId: Int,
        form: NowPlayingForm,
        card: CardSize,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val density = context.resources.displayMetrics.density
        val widthDp = card.width / density
        val heightDp = card.height / density
        val artDp = when (form) {
            NowPlayingForm.ROW -> (heightDp - 2 * ROW_PADDING_DP).coerceIn(36f, 72f)
            NowPlayingForm.SQUARE -> (minOf(widthDp, heightDp) * 0.46f).coerceIn(56f, 130f)
            NowPlayingForm.WIDE -> (heightDp - 2 * CARD_PADDING_DP).coerceIn(72f, 190f)
        }
        views.setViewLayoutWidth(artId, artDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(artId, artDp, TypedValue.COMPLEX_UNIT_DIP)
    }

    private suspend fun loadLyricLines(mediaId: String?): List<LyricsEntry> {
        if (mediaId == null) return emptyList()
        val raw = runCatching { database.lyrics(mediaId).first()?.lyrics }.getOrNull()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
    }

    private fun createLyricsRemoteViews(
        options: Bundle,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        lines: List<LyricsEntry>,
        currentPosition: Long,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
        val card = cardSizeFor(options)

        views.setImageViewBitmap(
            R.id.widget_lyr_bg,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        views.setTextViewText(R.id.widget_lyr_title, title)
        views.setTextViewText(R.id.widget_lyr_artist, artist)

        if (lines.isEmpty()) {
            views.setTextViewText(R.id.widget_lyr_prev, "")
            views.setTextViewText(
                R.id.widget_lyr_current,
                context.getString(R.string.widget_no_lyrics),
            )
            views.setTextViewText(R.id.widget_lyr_next, "")
        } else {
            val index = LyricsUtils.findCurrentLineIndex(lines, currentPosition)
                .coerceIn(0, lines.lastIndex)
            views.setTextViewText(R.id.widget_lyr_prev, lines.getOrNull(index - 1)?.text ?: "")
            views.setTextViewText(R.id.widget_lyr_current, lines[index].text)
            views.setTextViewText(R.id.widget_lyr_next, lines.getOrNull(index + 1)?.text ?: "")
        }

        views.setImageViewResource(
            R.id.widget_lyr_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        val transportVisibility =
            if (config.showPrevNext) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_lyr_skip_prev, transportVisibility)
        views.setViewVisibility(R.id.widget_lyr_skip_next, transportVisibility)

        views.setOnClickPendingIntent(R.id.widget_lyr_play_pause, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_lyr_skip_next, getNextIntent())
        views.setOnClickPendingIntent(R.id.widget_lyr_skip_prev, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_lyr_bg, getOpenAppIntent(openLyrics = true))
        views.setOnClickPendingIntent(R.id.widget_lyr_title, getOpenAppIntent(openLyrics = true))
        views.setOnClickPendingIntent(R.id.widget_lyr_artist, getOpenAppIntent(openLyrics = true))
        views.setOnClickPendingIntent(R.id.widget_lyr_prev, getOpenAppIntent(openLyrics = true))
        views.setOnClickPendingIntent(R.id.widget_lyr_current, getOpenAppIntent(openLyrics = true))
        views.setOnClickPendingIntent(R.id.widget_lyr_next, getOpenAppIntent(openLyrics = true))

        val ink = onCardColorFor(config, albumArt)
        listOf(
            R.id.widget_lyr_title,
            R.id.widget_lyr_artist,
            R.id.widget_lyr_prev,
            R.id.widget_lyr_current,
            R.id.widget_lyr_next,
        ).forEach { views.setTextColor(it, ink) }
        listOf(
            R.id.widget_lyr_play_pause,
            R.id.widget_lyr_skip_next,
            R.id.widget_lyr_skip_prev,
            R.id.widget_lyr_glyph,
        ).forEach { tint(views, it, ink) }
        return views
    }

    private fun createQueuePreviewRemoteViews(
        options: Bundle,
        config: WidgetConfig,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        queueItems: List<WidgetQueueItem>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_queue_preview)
        val card = cardSizeFor(options)
        val ink = onCardColorFor(config, albumArt)

        views.setImageViewBitmap(
            R.id.widget_queue_bg,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        // Header info
        views.setTextViewText(R.id.widget_queue_title, title)
        views.setTextViewText(R.id.widget_queue_artist, artist)

        if (config.showArtwork) {
            views.setViewVisibility(R.id.widget_queue_art, android.view.View.VISIBLE)
            views.setImageViewBitmap(
                R.id.widget_queue_art,
                albumArt?.let { getRoundedCornerBitmap(it) } ?: getRoundedDefaultIcon(),
            )
        } else {
            views.setViewVisibility(R.id.widget_queue_art, android.view.View.GONE)
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_queue_play_pause, playPauseIcon)
        views.setOnClickPendingIntent(R.id.widget_queue_play_pause, getPlayPauseIntent())

        val transportVisibility = if (config.showPrevNext) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_queue_prev, transportVisibility)
        views.setViewVisibility(R.id.widget_queue_next, transportVisibility)
        views.setOnClickPendingIntent(R.id.widget_queue_prev, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_queue_next, getNextIntent())

        // Quick play playlist button
        views.setOnClickPendingIntent(
            R.id.widget_queue_playlist,
            getPlayPlaylistIntent(config.playlistId ?: "favorites"),
        )

        // Header clicks deep-link to player
        views.setOnClickPendingIntent(R.id.widget_queue_bg, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_queue_art, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_queue_title, getOpenAppIntent(expandPlayer = true))
        views.setOnClickPendingIntent(R.id.widget_queue_artist, getOpenAppIntent(expandPlayer = true))

        // Colors
        listOf(
            R.id.widget_queue_title,
            R.id.widget_queue_artist,
            R.id.widget_queue_label,
            R.id.widget_queue_empty_text,
            R.id.widget_q_title_1,
            R.id.widget_q_artist_1,
            R.id.widget_q_title_2,
            R.id.widget_q_artist_2,
            R.id.widget_q_title_3,
            R.id.widget_q_artist_3,
        ).forEach { views.setTextColor(it, ink) }

        listOf(
            R.id.widget_queue_prev,
            R.id.widget_queue_play_pause,
            R.id.widget_queue_next,
            R.id.widget_queue_playlist,
        ).forEach { tint(views, it, ink) }

        // Bind up to 3 queue items
        val itemRowIds = listOf(R.id.widget_q_item_1, R.id.widget_q_item_2, R.id.widget_q_item_3)
        val itemTitleIds = listOf(R.id.widget_q_title_1, R.id.widget_q_title_2, R.id.widget_q_title_3)
        val itemArtistIds = listOf(R.id.widget_q_artist_1, R.id.widget_q_artist_2, R.id.widget_q_artist_3)

        if (queueItems.isEmpty()) {
            views.setViewVisibility(R.id.widget_queue_empty_text, android.view.View.VISIBLE)
            itemRowIds.forEach { views.setViewVisibility(it, android.view.View.GONE) }
        } else {
            views.setViewVisibility(R.id.widget_queue_empty_text, android.view.View.GONE)
            for (i in itemRowIds.indices) {
                val rowId = itemRowIds[i]
                val titleViewId = itemTitleIds[i]
                val artistViewId = itemArtistIds[i]
                val item = queueItems.getOrNull(i)
                if (item != null) {
                    views.setViewVisibility(rowId, android.view.View.VISIBLE)
                    views.setTextViewText(titleViewId, item.title)
                    views.setTextViewText(artistViewId, item.artist)
                    views.setOnClickPendingIntent(rowId, getSkipToQueueIntent(item.index))
                } else {
                    views.setViewVisibility(rowId, android.view.View.GONE)
                }
            }
        }

        return views
    }

    private fun createPlaylistRemoteViews(
        options: Bundle,
        config: WidgetConfig,
        albumArt: Bitmap?,
        playlistName: String,
        playlistId: String,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)
        val card = cardSizeFor(options)
        val ink = onCardColorFor(config, albumArt)

        views.setImageViewBitmap(
            R.id.widget_playlist_bg,
            loadConfiguredBackground(config, card)
                ?: createCardBackground(config, albumArt, card),
        )

        views.setTextViewText(R.id.widget_playlist_title, playlistName)
        views.setTextViewText(R.id.widget_playlist_subtitle, context.getString(R.string.tap_to_play))

        if (config.showArtwork) {
            views.setViewVisibility(R.id.widget_playlist_art, android.view.View.VISIBLE)
            views.setImageViewBitmap(
                R.id.widget_playlist_art,
                albumArt?.let { getRoundedCornerBitmap(it) } ?: getRoundedDefaultIcon(),
            )
        } else {
            views.setViewVisibility(R.id.widget_playlist_art, android.view.View.GONE)
        }

        // Click on play container launches playback of that playlist
        views.setOnClickPendingIntent(
            R.id.widget_playlist_play_container,
            getPlayPlaylistIntent(playlistId),
        )
        // Background click opens main app
        views.setOnClickPendingIntent(R.id.widget_playlist_bg, getOpenAppIntent())

        applyCardInk(
            views,
            ink,
            texts = listOf(
                R.id.widget_playlist_label,
                R.id.widget_playlist_title,
                R.id.widget_playlist_subtitle,
                R.id.widget_playlist_play_text,
            ),
            icons = listOf(
                R.id.widget_playlist_play_icon,
            ),
            pillId = R.id.widget_playlist_play_container,
        )

        return views
    }

    private fun getOpenAppIntent(
        expandPlayer: Boolean = false,
        openLyrics: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (openLyrics) {
                action = MainActivity.ACTION_OPEN_LYRICS
                putExtra(MainActivity.EXTRA_OPEN_LYRICS, true)
            } else if (expandPlayer) {
                action = MainActivity.ACTION_EXPAND_PLAYER
                putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
            }
        }
        val requestCode = when {
            openLyrics -> 110
            expandPlayer -> 111
            else -> 100
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getPlayPauseIntent(): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun getLikeIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_LIKE
        }
        return PendingIntent.getBroadcast(
            context,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNextIntent(): PendingIntent = broadcastToMusicWidget(
        MusicWidgetReceiver.ACTION_NEXT,
        requestCode = 103,
    )

    private fun getPreviousIntent(): PendingIntent = broadcastToMusicWidget(
        MusicWidgetReceiver.ACTION_PREVIOUS,
        requestCode = 104,
    )

    private fun getShuffleIntent(): PendingIntent = broadcastToMusicWidget(
        MusicWidgetReceiver.ACTION_SHUFFLE,
        requestCode = 112,
    )

    private fun getRepeatIntent(): PendingIntent = broadcastToMusicWidget(
        MusicWidgetReceiver.ACTION_REPEAT,
        requestCode = 113,
    )

    private fun getPlayPlaylistIntent(playlistId: String): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PLAYLIST
            putExtra(MusicWidgetReceiver.EXTRA_PLAYLIST_ID, playlistId)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                114 + (playlistId.hashCode() and 0xFFFF),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                context,
                114 + (playlistId.hashCode() and 0xFFFF),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun getSkipToQueueIntent(index: Int): PendingIntent {
        val intent = Intent(context, QueueWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_SKIP_TO_QUEUE_ITEM
            putExtra(MusicWidgetReceiver.EXTRA_QUEUE_INDEX, index)
        }
        return PendingIntent.getBroadcast(
            context,
            120 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcastToMusicWidget(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getTurntablePlayPauseIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            105,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntableNextIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            106,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePreviousIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            107,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntableLikeIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_LIKE
        }
        return PendingIntent.getBroadcast(
            context,
            108,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ART_CORNER_FRACTION = 0.13f
        const val CARD_PADDING_DP = 14f
        const val ROW_PADDING_DP = 8f
        const val CARD_CORNER_DP = 16f
    }
}
