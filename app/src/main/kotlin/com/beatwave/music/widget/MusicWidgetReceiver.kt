/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.beatwave.music.playback.MusicService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Service might be restricted in background
            }
        } else {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    ).widgetManager()
                    manager.renderIdleWidgets(appWidgetManager, appWidgetIds)
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Service might be restricted in background
            }
        } else {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    ).widgetManager()
                    manager.renderIdleWidgets(appWidgetManager, intArrayOf(appWidgetId))
                } catch (e: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetConfig.delete(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_LIKE, ACTION_NEXT, ACTION_PREVIOUS,
            ACTION_SHUFFLE, ACTION_REPEAT, ACTION_PLAY_PLAYLIST, ACTION_SKIP_TO_QUEUE_ITEM,
            ACTION_REWIND, ACTION_FORWARD -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = intent.action
                    putExtras(intent)
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    // Service might be restricted in background
                }
            }
            ACTION_UPDATE_WIDGET -> {
                if (MusicService.isRunning) {
                    try {
                        context.startService(Intent(context, MusicService::class.java).apply {
                            action = ACTION_UPDATE_WIDGET
                        })
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.pulsewave.music.widget.PLAY_PAUSE"
        const val ACTION_LIKE = "com.pulsewave.music.widget.LIKE"
        const val ACTION_NEXT = "com.pulsewave.music.widget.NEXT"
        const val ACTION_PREVIOUS = "com.pulsewave.music.widget.PREVIOUS"
        const val ACTION_SHUFFLE = "com.pulsewave.music.widget.SHUFFLE"
        const val ACTION_REPEAT = "com.pulsewave.music.widget.REPEAT"
        const val ACTION_PLAY_PLAYLIST = "com.pulsewave.music.widget.PLAY_PLAYLIST"
        const val ACTION_SKIP_TO_QUEUE_ITEM = "com.pulsewave.music.widget.SKIP_TO_QUEUE_ITEM"
        const val ACTION_REWIND = "com.pulsewave.music.widget.REWIND"
        const val ACTION_FORWARD = "com.pulsewave.music.widget.FORWARD"
        const val ACTION_UPDATE_WIDGET = "com.pulsewave.music.widget.UPDATE_WIDGET"

        const val EXTRA_PLAYLIST_ID = "playlist_id"
        const val EXTRA_QUEUE_INDEX = "queue_index"
    }
}
