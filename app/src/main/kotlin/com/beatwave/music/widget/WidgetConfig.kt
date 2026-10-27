/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.content.Context

/** How a widget instance paints its card behind the content. */
enum class WidgetBackground {
    /** Dominant colour of the current artwork (default, Apple Music behaviour). */
    ALBUM_TINT,
    /** Material You (Monet) dynamic wallpaper colors on Android 12+. */
    SYSTEM_DYNAMIC,
    DARK,
    LIGHT,
    /** A still image the user picked in the widget's config screen. */
    IMAGE,
}

/**
 * Per-instance widget settings.
 *
 * Deliberately NOT in DataStore: these are keyed by appWidgetId rather than being
 * app-wide, and they have to be readable synchronously from AppWidgetProvider /
 * the widget manager, which is not a coroutine context. A small SharedPreferences
 * file keyed by widget id is the right size for that.
 */
data class WidgetConfig(
    val background: WidgetBackground = WidgetBackground.ALBUM_TINT,
    /** Opacity from 0.0f (fully transparent) to 1.0f (solid / full gradient). */
    val opacity: Float = 1.0f,
    /** content:// uri of the user's chosen background, when [background] is IMAGE. */
    val imageUri: String? = null,
    val showArtwork: Boolean = true,
    val showPrevNext: Boolean = true,
    val showLike: Boolean = true,
    val showShuffleRepeat: Boolean = false,
    val showLosslessBadge: Boolean = true,
    /**
     * Playlist this widget launches/plays instead of the current queue.
     * null = act on whatever is currently playing.
     */
    val playlistId: String? = null,
) {
    companion object {
        private const val FILE = "widget_config"

        private fun keyOf(widgetId: Int, name: String) = "w${widgetId}_$name"

        private fun prefs(context: Context) =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun load(context: Context, widgetId: Int): WidgetConfig {
            val p = prefs(context)
            val default = WidgetConfig()
            val backgroundName = p.getString(keyOf(widgetId, "bg"), null)
            return WidgetConfig(
                background = backgroundName
                    ?.let { name -> WidgetBackground.entries.firstOrNull { it.name == name } }
                    ?: default.background,
                opacity = p.getFloat(keyOf(widgetId, "opacity"), default.opacity),
                imageUri = p.getString(keyOf(widgetId, "image"), null),
                showArtwork = p.getBoolean(keyOf(widgetId, "art"), default.showArtwork),
                showPrevNext = p.getBoolean(keyOf(widgetId, "prevnext"), default.showPrevNext),
                showLike = p.getBoolean(keyOf(widgetId, "like"), default.showLike),
                showShuffleRepeat = p.getBoolean(keyOf(widgetId, "shufflerepeat"), default.showShuffleRepeat),
                showLosslessBadge = p.getBoolean(keyOf(widgetId, "losslessbadge"), default.showLosslessBadge),
                playlistId = p.getString(keyOf(widgetId, "playlist"), null),
            )
        }

        fun save(context: Context, widgetId: Int, config: WidgetConfig) {
            prefs(context).edit().apply {
                putString(keyOf(widgetId, "bg"), config.background.name)
                putFloat(keyOf(widgetId, "opacity"), config.opacity)
                putString(keyOf(widgetId, "image"), config.imageUri)
                putBoolean(keyOf(widgetId, "art"), config.showArtwork)
                putBoolean(keyOf(widgetId, "prevnext"), config.showPrevNext)
                putBoolean(keyOf(widgetId, "like"), config.showLike)
                putBoolean(keyOf(widgetId, "shufflerepeat"), config.showShuffleRepeat)
                putBoolean(keyOf(widgetId, "losslessbadge"), config.showLosslessBadge)
                putString(keyOf(widgetId, "playlist"), config.playlistId)
            }.apply()
        }

        /** Called from onDeleted so removed widgets don't leak their settings forever. */
        fun delete(context: Context, widgetIds: IntArray) {
            prefs(context).edit().apply {
                widgetIds.forEach { id ->
                    listOf("bg", "opacity", "image", "art", "prevnext", "like", "shufflerepeat", "losslessbadge", "playlist")
                        .forEach { remove(keyOf(id, it)) }
                }
            }.apply()
        }
    }
}
