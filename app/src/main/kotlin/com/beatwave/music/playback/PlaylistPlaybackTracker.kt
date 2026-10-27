/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback

import android.content.Context

/**
 * Tracks the last played song, queue index, and millisecond playback position for each playlist,
 * so that when a user returns to a playlist (even after closing from recents or removing from background),
 * playback seamlessly resumes right where they left off.
 */
object PlaylistPlaybackTracker {
    private const val PREFS_NAME = "playlist_playback_tracker"
    private const val KEY_PREFIX_INDEX = "last_index_"
    private const val KEY_PREFIX_SONG_ID = "last_song_id_"
    private const val KEY_PREFIX_POSITION = "last_position_ms_"
    private const val KEY_ACTIVE_PLAYLIST = "active_playlist_id"

    fun saveActivePlaylist(context: Context, playlistId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PLAYLIST, playlistId).apply()
    }

    fun getActivePlaylist(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PLAYLIST, null)
    }

    fun savePlaylistProgress(
        context: Context,
        playlistId: String,
        songId: String,
        songIndex: Int,
        positionMs: Long = 0L,
    ) {
        if (playlistId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_PREFIX_INDEX + playlistId, songIndex)
            .putString(KEY_PREFIX_SONG_ID + playlistId, songId)
            .putLong(KEY_PREFIX_POSITION + playlistId, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun getLastPlayedIndex(context: Context, playlistId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PREFIX_INDEX + playlistId, 0)
    }

    fun getLastPlayedSongId(context: Context, playlistId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFIX_SONG_ID + playlistId, null)
    }

    fun getLastPlayedPosition(context: Context, playlistId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_PREFIX_POSITION + playlistId, 0L)
    }

    /**
     * Resolves both the start index and seek position (in ms) for a playlist.
     * Checks if the last played song ID exists in the list first, then falls back
     * to the saved index. If the saved index was the very last song in a multi-song playlist,
     * wraps to (0, 0L) so the user can replay the playlist.
     */
    fun resolveStartProgress(context: Context, playlistId: String, songIds: List<String>): Pair<Int, Long> {
        if (songIds.isEmpty()) return Pair(0, 0L)

        val savedPosition = getLastPlayedPosition(context, playlistId)
        val savedSongId = getLastPlayedSongId(context, playlistId)
        if (!savedSongId.isNullOrBlank()) {
            val idx = songIds.indexOf(savedSongId)
            if (idx in 0 until songIds.size) {
                return if (idx == songIds.lastIndex && songIds.size > 1) {
                    Pair(0, 0L)
                } else {
                    Pair(idx, savedPosition)
                }
            }
        }

        val savedIndex = getLastPlayedIndex(context, playlistId)
        if (savedIndex in 0 until songIds.size) {
            return if (savedIndex == songIds.lastIndex && songIds.size > 1) {
                Pair(0, 0L)
            } else {
                Pair(savedIndex, savedPosition)
            }
        }

        return Pair(0, 0L)
    }

    fun resolveStartIndex(context: Context, playlistId: String, songIds: List<String>): Int {
        return resolveStartProgress(context, playlistId, songIds).first
    }
}
