/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.beatwave.music.playback.MusicService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Apple Music styled "Now Playing" widget (row 4x1 / square 2x2 / wide 4x2).
 */
class NowPlayingWidgetReceiver : MusicWidgetReceiver() {

    companion object {
        /**
         * Repaint now rather than waiting for the next playback event — used after
         * the config screen changes something.
         */
        fun requestUpdate(context: Context) {
            if (MusicService.isRunning) {
                runCatching {
                    context.startService(
                        Intent(context, MusicService::class.java).apply {
                            action = ACTION_UPDATE_WIDGET
                        }
                    )
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val manager = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            WidgetEntryPoint::class.java
                        ).widgetManager()
                        manager.renderIdleWidgets(appWidgetManager)
                    }
                }
            }
        }
    }
}
