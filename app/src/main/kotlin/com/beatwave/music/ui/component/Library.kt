/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.WatchEndpoint
import com.beatwave.music.R
import com.beatwave.music.db.entities.Album
import com.beatwave.music.db.entities.Artist
import com.beatwave.music.db.entities.Playlist
import com.beatwave.music.ui.menu.AlbumMenu
import com.beatwave.music.ui.menu.ArtistMenu
import com.beatwave.music.ui.menu.PlaylistMenu
import com.beatwave.music.ui.menu.YouTubePlaylistMenu
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.beatwave.music.LocalDatabase
import com.beatwave.music.LocalPlayerConnection
import com.beatwave.music.playback.PlaylistPlaybackTracker
import com.beatwave.music.playback.queues.ListQueue
import com.beatwave.music.playback.queues.YouTubeQueue
import com.beatwave.music.extensions.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryArtistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    modifier: Modifier = Modifier,
    showIconOnly: Boolean = true,
) = ArtistListItem(
    artist = artist,
    showIconOnly = showIconOnly,
    trailingContent = {
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null
            )
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            navController.navigate("artist/${artist.id}")
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    modifier: Modifier = Modifier,
    showIconOnly: Boolean = true,
) = ArtistGridItem(
    artist = artist,
    fillMaxWidth = true,
    showIconOnly = showIconOnly,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                navController.navigate("artist/${artist.id}")
            },
            onLongClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@Composable
fun LibraryAlbumListItem(
    modifier: Modifier = Modifier,
    navController: NavController,
    menuState: MenuState,
    album: Album,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    showIconOnly: Boolean = true,
) = AlbumListItem(
    album = album,
    isActive = isActive,
    isPlaying = isPlaying,
    showIconOnly = showIconOnly,
    trailingContent = {
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null
            )
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            navController.navigate("album/${album.id}")
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumGridItem(
    modifier: Modifier = Modifier,
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    album: Album,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    showIconOnly: Boolean = true,
) = AlbumGridItem(
    album = album,
    isActive = isActive,
    isPlaying = isPlaying,
    coroutineScope = coroutineScope,
    fillMaxWidth = true,
    showIconOnly = showIconOnly,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                navController.navigate("album/${album.id}")
            },
            onLongClick = {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@Composable
fun LibraryPlaylistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    playlist: Playlist,
    modifier: Modifier = Modifier,
    showIconOnly: Boolean = false,
    isCustomSort: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) = PlaylistListItem(
    playlist = playlist,
    showIconOnly = showIconOnly,
    trailingContent = {
        if (isCustomSort) {
            androidx.compose.material3.IconButton(
                onClick = { },
                modifier = dragHandleModifier
            ) {
                Icon(
                    painter = painterResource(R.drawable.drag_handle),
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    if (playlist.playlist.isEditable || playlist.songCount != 0) {
                        PlaylistMenu(
                            playlist = playlist,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss
                        )
                    } else {
                        playlist.playlist.browseId?.let { browseId ->
                            YouTubePlaylistMenu(
                                playlist = PlaylistItem(
                                    id = browseId,
                                    title = playlist.playlist.name,
                                    author = null,
                                    songCountText = null,
                                    thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                                    playEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.playEndpointParams
                                    ),
                                    shuffleEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.shuffleEndpointParams
                                    ),
                                    radioEndpoint = WatchEndpoint(
                                        playlistId = "RDAMPL$browseId",
                                        params = playlist.playlist.radioEndpointParams
                                    ),
                                    isEditable = false
                                ),
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                }
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null
            )
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0)
                navController.navigate("online_playlist/${playlist.playlist.browseId}")
            else
                navController.navigate("local_playlist/${playlist.id}")
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    playlist: Playlist,
    modifier: Modifier = Modifier,
    showIconOnly: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    PlaylistGridItem(
        playlist = playlist,
        fillMaxWidth = true,
        showIconOnly = showIconOnly,
        onQuickPlay = {
            if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.browseId != null) {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(
                            playlistId = playlist.playlist.browseId,
                            params = playlist.playlist.playEndpointParams
                        )
                    )
                )
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    val songs = database.playlistSongs(playlist.id).first().map { it.song }
                    if (songs.isNotEmpty()) {
                        val (startIndex, positionMs) = PlaylistPlaybackTracker.resolveStartProgress(
                            context,
                            playlist.id,
                            songs.map { it.id }
                        )
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.map { it.toMediaItem() },
                                    startIndex = startIndex,
                                    position = positionMs,
                                    playlistId = playlist.id
                                )
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0)
                        navController.navigate("online_playlist/${playlist.playlist.browseId}")
                    else
                        navController.navigate("local_playlist/${playlist.id}")
                },
                onLongClick = {
                    menuState.show {
                        if (playlist.playlist.isEditable || playlist.songCount != 0) {
                            PlaylistMenu(
                                playlist = playlist,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss
                            )
                        } else {
                            playlist.playlist.browseId?.let { browseId ->
                                YouTubePlaylistMenu(
                                    playlist = PlaylistItem(
                                        id = browseId,
                                        title = playlist.playlist.name,
                                        author = null,
                                        songCountText = null,
                                        thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                                        playEndpoint = WatchEndpoint(
                                            playlistId = browseId,
                                            params = playlist.playlist.playEndpointParams
                                        ),
                                        shuffleEndpoint = WatchEndpoint(
                                            playlistId = browseId,
                                            params = playlist.playlist.shuffleEndpointParams
                                        ),
                                        radioEndpoint = WatchEndpoint(
                                            playlistId = "RDAMPL$browseId",
                                            params = playlist.playlist.radioEndpointParams
                                        ),
                                        isEditable = false
                                    ),
                                    coroutineScope = coroutineScope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                }
            )
    )
}
