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
                    options,
                    config,
                    title,
                    artist,
                    circularAlbumArt,
                    isPlaying,
                    isLiked
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

    private fun createTurntableVinylRecord(source: Bitmap, isPlaying: Boolean): Bitmap {
        val size = minOf(source.width, source.height).coerceAtLeast(360)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val radius = size / 2f

        // 1. Sleek vinyl black base
        val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121215")
        }
        canvas.drawCircle(radius, radius, radius, vinylPaint)

        // 2. Concentric audio grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(22, 255, 255, 255)
            strokeWidth = 1.2f
        }
        val labelRadius = radius * 0.58f
        var r = labelRadius + 5f
        while (r < radius - 6f) {
            canvas.drawCircle(radius, radius, r, groovePaint)
            r += 9f
        }

        // 3. Realistic vinyl reflection sheen (anisotropic reflections)
        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = SweepGradient(
                radius, radius,
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(45, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb(45, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.20f, 0.40f, 0.50f, 0.70f, 1f)
            )
        }
        canvas.save()
        val groovePath = android.graphics.Path().apply {
            addCircle(radius, radius, radius - 4f, android.graphics.Path.Direction.CW)
            addCircle(radius, radius, labelRadius, android.graphics.Path.Direction.CCW)
        }
        canvas.clipPath(groovePath)
        canvas.drawCircle(radius, radius, radius, sweepPaint)
        canvas.restore()

        // 4. Center album art label with crisp circular shader
        val squareSource = if (source.width != source.height) {
            val minDim = minOf(source.width, source.height)
            Bitmap.createBitmap(source, (source.width - minDim) / 2, (source.height - minDim) / 2, minDim, minDim)
        } else source

        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(squareSource, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val matrix = android.graphics.Matrix().apply {
            val s = (labelRadius * 2f) / squareSource.width
            setScale(s, s)
            postTranslate(radius - labelRadius, radius - labelRadius)
        }
        artPaint.shader.setLocalMatrix(matrix)
        canvas.drawCircle(radius, radius, labelRadius, artPaint)

        // 5. Outer rim highlight on label
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(45, 255, 255, 255)
            strokeWidth = 2.5f
        }
        canvas.drawCircle(radius, radius, labelRadius, rimPaint)

        // 6. Center spindle hole & metallic bevel ring
        val spindleHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#08080A")
        }
        canvas.drawCircle(radius, radius, radius * 0.10f, spindleHolePaint)
        val spindleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(200, 230, 230, 240)
            strokeWidth = 2.5f
        }
        canvas.drawCircle(radius, radius, radius * 0.10f, spindleRingPaint)

        // 7. Sleek stylus tonearm
        drawStylusTonearm(canvas, size.toFloat(), isPlaying)

        return output
    }

    private fun drawStylusTonearm(canvas: Canvas, size: Float, isPlaying: Boolean) {
        val pivotX = size * 0.86f
        val pivotY = size * 0.14f
        val pivotRadius = size * 0.055f

        // 1. Pivot Base shadow and metallic base
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pivotX + 2f, pivotY + 3f, pivotRadius, shadowPaint)

        val pivotBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                pivotX, pivotY, pivotRadius,
                intArrayOf(Color.parseColor("#E0E0E6"), Color.parseColor("#4A4A52")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(pivotX, pivotY, pivotRadius, pivotBasePaint)

        val pivotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#222226")
            strokeWidth = 2f
        }
        canvas.drawCircle(pivotX, pivotY, pivotRadius, pivotRingPaint)

        // 2. Tonearm rod coordinates
        val targetX = if (isPlaying) size * 0.62f else size * 0.86f
        val targetY = if (isPlaying) size * 0.46f else size * 0.42f

        val elbowX = if (isPlaying) size * 0.78f else size * 0.88f
        val elbowY = if (isPlaying) size * 0.28f else size * 0.26f

        val armPath = android.graphics.Path().apply {
            moveTo(pivotX, pivotY)
            lineTo(elbowX, elbowY)
            lineTo(targetX, targetY)
        }

        // Arm shadow
        val armShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.018f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(90, 0, 0, 0)
        }
        val shadowOffsetPath = android.graphics.Path().apply {
            moveTo(pivotX + 3f, pivotY + 4f)
            lineTo(elbowX + 3f, elbowY + 4f)
            lineTo(targetX + 3f, targetY + 4f)
        }
        canvas.drawPath(shadowOffsetPath, armShadowPaint)

        // Arm rod
        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.018f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#E6E6EC")
        }
        canvas.drawPath(armPath, armPaint)

        // 3. Cartridge / Headshell at the tip
        val headAngle = if (isPlaying) 32.0 else 5.0
        val headLength = size * 0.065f
        val headWidth = size * 0.026f

        canvas.save()
        canvas.translate(targetX, targetY)
        canvas.rotate(headAngle.toFloat())

        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1C20")
            style = Paint.Style.FILL
        }
        val headRect = RectF(-headWidth / 2f, 0f, headWidth / 2f, headLength)
        canvas.drawRoundRect(headRect, 3f, 3f, headPaint)

        // Stylus needle tip highlight
        val stylusTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E82C2A")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(0f, headLength - 2f, size * 0.008f, stylusTipPaint)

        canvas.restore()
    }

    private fun createTurntableRemoteViews(
        options: Bundle,
        config: WidgetConfig,
        title: String,
        artist: String,
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        val vinylRecord = if (circularAlbumArt != null) {
            createTurntableVinylRecord(circularAlbumArt, isPlaying)
        } else {
            createTurntableVinylRecord(getCircularDefaultIcon(), isPlaying)
        }
        views.setImageViewBitmap(R.id.widget_turntable_album_art, vinylRecord)

        views.setTextViewText(R.id.widget_turntable_title, title)
        views.setTextViewText(R.id.widget_turntable_artist, artist)

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

        // Set click intents
        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_info_container, getOpenAppIntent())
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

        val (top, bottom) = gradientColorsFor(config, albumArt)
        val alphaInt = (config.opacity.coerceIn(0f, 1f) * 255).toInt()
        val topWithAlpha = ColorUtils.setAlphaComponent(top, alphaInt)
        val bottomWithAlpha = ColorUtils.setAlphaComponent(bottom, alphaInt)

        val canvas = Canvas(bitmap)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width * 0.35f, height.toFloat(),
                topWithAlpha, bottomWithAlpha, Shader.TileMode.CLAMP,
            )
        }
        drawContinuousRoundedRect(canvas, width.toFloat(), height.toFloat(), card.cornerPx, fill)

        val rimAlpha = (config.opacity.coerceIn(0f, 1f) * 120).toInt()
        if (rimAlpha > 0) {
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (height * 0.012f).coerceAtLeast(1f)
                shader = LinearGradient(
                    0f, 0f, 0f, height * 0.34f,
                    Color.argb(rimAlpha, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
            }
            drawContinuousRoundedRect(canvas, width.toFloat(), height.toFloat(), card.cornerPx, rim)
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
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        val intent = Intent(context, PlaylistWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PLAYLIST
            putExtra(MusicWidgetReceiver.EXTRA_PLAYLIST_ID, playlistId)
        }
        return PendingIntent.getBroadcast(
            context,
            114 + (playlistId.hashCode() and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
