/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beatwave.music.R
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.Playlist
import com.beatwave.music.ui.component.PreferenceEntry
import com.beatwave.music.ui.component.PreferenceGroupTitle
import com.beatwave.music.ui.component.SwitchPreference
import com.beatwave.music.ui.theme.vivimusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Per-widget configuration, launched by the launcher via APPWIDGET_CONFIGURE when
 * a widget is dropped, and re-openable later from widget settings.
 *
 * Result handling matters here: the launcher only keeps the widget if we return
 * RESULT_OK with the widget id, and we must return RESULT_CANCELED on back so a
 * half-configured widget isn't left on the home screen.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var database: MusicDatabase

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelled unless the user explicitly confirms, per the configure contract.
        setResult(Activity.RESULT_CANCELED, resultIntent())

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            vivimusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val playlists by database.playlistsByNameAsc().collectAsState(initial = emptyList())
                    WidgetConfigScreen(
                        initial = WidgetConfig.load(this, widgetId),
                        playlists = playlists,
                        onConfirm = ::confirm,
                    )
                }
            }
        }
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private fun confirm(config: WidgetConfig) {
        WidgetConfig.save(this, widgetId, config)
        // Ask the service to repaint immediately so the widget doesn't sit on its
        // placeholder layout until the next playback event.
        NowPlayingWidgetReceiver.requestUpdate(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }
}

@Composable
private fun WidgetConfigScreen(
    initial: WidgetConfig,
    playlists: List<Playlist>,
    onConfirm: (WidgetConfig) -> Unit,
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(initial) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Widgets render from a different process later on, so the read grant
            // has to be persisted or the image goes blank after a reboot.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            config = config.copy(
                imageUri = uri.toString(),
                background = WidgetBackground.IMAGE,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.widget_cfg_background))

        WidgetBackground.entries.forEach { option ->
            PreferenceEntry(
                title = { Text(stringResource(option.labelRes())) },
                trailingContent = {
                    RadioButton(
                        selected = config.background == option,
                        onClick = { config = config.copy(background = option) },
                    )
                },
                onClick = {
                    if (option == WidgetBackground.IMAGE) {
                        pickImage.launch(arrayOf("image/*"))
                    } else {
                        config = config.copy(background = option)
                    }
                },
            )
        }

        if (config.background == WidgetBackground.IMAGE) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.widget_cfg_choose_image)) },
                description = config.imageUri ?: stringResource(R.string.widget_cfg_no_image),
                onClick = { pickImage.launch(arrayOf("image/*")) },
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.widget_cfg_opacity))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.widget_cfg_background_opacity),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val percent = (config.opacity * 100).toInt()
                val label = when {
                    percent >= 98 -> "100% (Solid)"
                    percent in 45..75 -> "$percent% (Frosted Glass)"
                    percent <= 5 -> "0% (Transparent)"
                    else -> "$percent%"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = config.opacity,
                onValueChange = { config = config.copy(opacity = it) },
                valueRange = 0f..1f,
                steps = 20,
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.widget_cfg_content))

        SwitchPreference(
            title = { Text(stringResource(R.string.widget_cfg_show_artwork)) },
            checked = config.showArtwork,
            onCheckedChange = { config = config.copy(showArtwork = it) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.widget_cfg_show_prev_next)) },
            checked = config.showPrevNext,
            onCheckedChange = { config = config.copy(showPrevNext = it) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.widget_cfg_show_like)) },
            checked = config.showLike,
            onCheckedChange = { config = config.copy(showLike = it) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.widget_cfg_show_shuffle_repeat)) },
            description = stringResource(R.string.widget_cfg_show_shuffle_repeat_desc),
            checked = config.showShuffleRepeat,
            onCheckedChange = { config = config.copy(showShuffleRepeat = it) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.widget_cfg_show_lossless_badge)) },
            description = stringResource(R.string.widget_cfg_show_lossless_badge_desc),
            checked = config.showLosslessBadge,
            onCheckedChange = { config = config.copy(showLosslessBadge = it) },
        )

        PreferenceGroupTitle(title = stringResource(R.string.widget_cfg_pinned_playlist))

        val currentPlaylistName = remember(config.playlistId, playlists) {
            when (config.playlistId) {
                null -> context.getString(R.string.widget_cfg_playlist_default)
                "favorites" -> context.getString(R.string.widget_cfg_playlist_favorites)
                else -> playlists.firstOrNull { it.id == config.playlistId }?.playlist?.name
                    ?: context.getString(R.string.widget_cfg_playlist_default)
            }
        }

        PreferenceEntry(
            title = { Text(stringResource(R.string.widget_cfg_pinned_playlist)) },
            description = currentPlaylistName,
            onClick = { showPlaylistDialog = true },
        )

        Button(
            onClick = { onConfirm(config) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.widget_cfg_done))
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text(stringResource(R.string.widget_cfg_pinned_playlist)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.widget_cfg_playlist_default)) },
                        trailingContent = {
                            RadioButton(
                                selected = config.playlistId == null,
                                onClick = {
                                    config = config.copy(playlistId = null)
                                    showPlaylistDialog = false
                                }
                            )
                        },
                        onClick = {
                            config = config.copy(playlistId = null)
                            showPlaylistDialog = false
                        }
                    )
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.widget_cfg_playlist_favorites)) },
                        trailingContent = {
                            RadioButton(
                                selected = config.playlistId == "favorites",
                                onClick = {
                                    config = config.copy(playlistId = "favorites")
                                    showPlaylistDialog = false
                                }
                            )
                        },
                        onClick = {
                            config = config.copy(playlistId = "favorites")
                            showPlaylistDialog = false
                        }
                    )
                    playlists.forEach { p ->
                        PreferenceEntry(
                            title = { Text(p.playlist.name) },
                            trailingContent = {
                                RadioButton(
                                    selected = config.playlistId == p.id,
                                    onClick = {
                                        config = config.copy(playlistId = p.id)
                                        showPlaylistDialog = false
                                    }
                                )
                            },
                            onClick = {
                                config = config.copy(playlistId = p.id)
                                showPlaylistDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

private fun WidgetBackground.labelRes(): Int = when (this) {
    WidgetBackground.ALBUM_TINT -> R.string.widget_cfg_bg_album
    WidgetBackground.SYSTEM_DYNAMIC -> R.string.widget_cfg_bg_system_dynamic
    WidgetBackground.DARK -> R.string.widget_cfg_bg_dark
    WidgetBackground.LIGHT -> R.string.widget_cfg_bg_light
    WidgetBackground.IMAGE -> R.string.widget_cfg_bg_image
}
