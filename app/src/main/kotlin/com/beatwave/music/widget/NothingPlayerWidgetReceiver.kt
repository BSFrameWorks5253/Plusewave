/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.beatwave.music.playback.MusicService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NothingPlayerWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_NOTHING_WIDGET
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
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
                } catch (_: Exception) {
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
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_NOTHING_WIDGET
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_NOTHING_PLAY_PAUSE, ACTION_NOTHING_NEXT, ACTION_NOTHING_PREVIOUS -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = when (intent.action) {
                        ACTION_NOTHING_PLAY_PAUSE -> MusicWidgetReceiver.ACTION_PLAY_PAUSE
                        ACTION_NOTHING_NEXT -> MusicWidgetReceiver.ACTION_NEXT
                        ACTION_NOTHING_PREVIOUS -> MusicWidgetReceiver.ACTION_PREVIOUS
                        else -> intent.action
                    }
                    putExtras(intent)
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: Exception) {
                    try {
                        context.startService(serviceIntent)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_NOTHING_PLAY_PAUSE = "com.pulsewave.music.widget.NOTHING_PLAY_PAUSE"
        const val ACTION_NOTHING_NEXT = "com.pulsewave.music.widget.NOTHING_NEXT"
        const val ACTION_NOTHING_PREVIOUS = "com.pulsewave.music.widget.NOTHING_PREVIOUS"
        const val ACTION_UPDATE_NOTHING_WIDGET = "com.pulsewave.music.widget.UPDATE_NOTHING_WIDGET"
    }
}
