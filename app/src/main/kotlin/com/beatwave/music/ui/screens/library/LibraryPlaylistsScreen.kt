/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.screens.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.constants.CONTENT_TYPE_HEADER
import com.beatwave.music.constants.CONTENT_TYPE_PLAYLIST
import com.beatwave.music.constants.CustomPlaylistOrderKey
import com.beatwave.music.constants.GridItemSize
import com.beatwave.music.constants.GridItemsSizeKey
import com.beatwave.music.constants.InnerTubeCookieKey
import com.beatwave.music.constants.LibraryIconsOnlyKey
import com.beatwave.music.constants.LibraryViewType
import com.beatwave.music.constants.PlaylistSortDescendingKey
import com.beatwave.music.constants.PlaylistSortType
import com.beatwave.music.constants.PlaylistSortTypeKey
import com.beatwave.music.constants.PlaylistViewTypeKey
import com.beatwave.music.constants.ShowCachedPlaylistKey
import com.beatwave.music.constants.ShowDownloadedPlaylistKey
import com.beatwave.music.constants.ShowLikedPlaylistKey
import com.beatwave.music.constants.ShowTopPlaylistKey
import com.beatwave.music.constants.ShowUploadedPlaylistKey
import com.beatwave.music.constants.YtmSyncKey
import com.beatwave.music.db.entities.Playlist
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.ui.component.CreatePlaylistDialog
import com.beatwave.music.ui.component.HideOnScrollFAB
import com.beatwave.music.ui.component.LargeScreenTitle
import com.beatwave.music.ui.component.LibraryPlaylistGridItem
import com.beatwave.music.ui.component.LibraryPlaylistListItem
import com.beatwave.music.ui.component.ListScrollRail
import com.beatwave.music.ui.component.LocalMenuState
import com.beatwave.music.ui.component.PlaylistGridItem
import com.beatwave.music.ui.component.PlaylistListItem
import com.beatwave.music.ui.component.SortHeader
import com.beatwave.music.ui.component.buildAlphabetSectionIndex
import com.beatwave.music.ui.theme.AppleTokens
import com.beatwave.music.ui.utils.combinedBounceClick
import com.beatwave.music.ui.utils.heroPullZoom
import com.beatwave.music.ui.utils.listOverscroll
import com.beatwave.music.ui.utils.rememberGridColumns
import com.beatwave.music.ui.utils.rememberHeroZoom
import com.beatwave.music.utils.rememberEnumPreference
import com.beatwave.music.utils.rememberPreference
import com.beatwave.music.viewmodels.LibraryPlaylistsViewModel
import com.music.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

sealed interface LibraryPlaylistItem {
    val id: String

    data class Auto(
        override val id: String,
        val playlist: Playlist,
        val route: String,
    ) : LibraryPlaylistItem

    data class User(
        val playlist: Playlist,
    ) : LibraryPlaylistItem {
        override val id: String get() = playlist.id
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true
    )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    
    val (libraryIconsOnly) = rememberPreference(LibraryIconsOnlyKey, defaultValue = false)

    val playlists by viewModel.allPlaylists.collectAsState()

    val topSize by viewModel.topValue.collectAsState(initial = 50)

    val likedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = "auto:liked",
                name = stringResource(R.string.liked)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val downloadPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = "auto:downloaded",
                name = stringResource(R.string.offline)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val topPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = "auto:top",
                name = stringResource(R.string.my_top) + " $topSize"
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val cachePlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = "auto:cached",
                name = stringResource(R.string.cached_playlist)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )
        
    val uploadedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = "auto:uploaded",
                name = stringResource(R.string.uploaded_playlist)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    // Pull-to-refresh only: no hero artwork on this screen, so heroZoom.scale
    // goes unread and the modifier contributes just the rubber-band stretch.
    val heroZoom = rememberHeroZoom()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            }
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AppleTokens.Gutter),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CUSTOM -> R.string.sort_by_custom
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                        PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_playlist,
                    playlists.size,
                    playlists.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            FlowRow(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LibraryViewType.entries.forEachIndexed { index, type ->
                    ToggleButton(
                        checked = viewType == type,
                        onCheckedChange = { viewType = type },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            LibraryViewType.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    ) {
                        Icon(
                            painter = painterResource(
                                when (type) {
                                    LibraryViewType.LIST -> R.drawable.list
                                    LibraryViewType.GRID -> R.drawable.grid_view
                                }
                            ),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }

    // Hoisted: both view types de-duped the same list separately, and the scroll rail
    // needs the resulting count too.
    val visiblePlaylists = remember(playlists) { playlists.distinctBy { it.id } }
    var customOrder by rememberPreference(CustomPlaylistOrderKey, "")

    val autoItems = remember(
        showLiked, showDownloaded, showTop, showCached, showUploaded,
        topSize, likedPlaylist, downloadPlaylist, topPlaylist, cachePlaylist, uploadedPlaylist
    ) {
        buildList {
            if (showLiked) add(LibraryPlaylistItem.Auto("auto:liked", likedPlaylist, "auto_playlist/liked"))
            if (showDownloaded) add(LibraryPlaylistItem.Auto("auto:downloaded", downloadPlaylist, "auto_playlist/downloaded"))
            if (showTop) add(LibraryPlaylistItem.Auto("auto:top", topPlaylist, "top_playlist/$topSize"))
            if (showCached) add(LibraryPlaylistItem.Auto("auto:cached", cachePlaylist, "cache_playlist/cached"))
            if (showUploaded) add(LibraryPlaylistItem.Auto("auto:uploaded", uploadedPlaylist, "auto_playlist/uploaded"))
        }
    }

    val userItems = remember(visiblePlaylists) {
        visiblePlaylists.map { LibraryPlaylistItem.User(it) }
    }

    val localUnifiedItems = remember(autoItems, userItems, sortType, customOrder) {
        val allMap = (autoItems + userItems).associateBy { it.id }
        if (sortType == PlaylistSortType.CUSTOM && customOrder.isNotBlank()) {
            val orderIds = customOrder.split(",").filter { it.isNotBlank() }
            val orderedList = mutableListOf<LibraryPlaylistItem>()
            val seen = mutableSetOf<String>()
            for (id in orderIds) {
                allMap[id]?.let {
                    orderedList.add(it)
                    seen.add(id)
                }
            }
            for (item in autoItems) {
                if (item.id !in seen) {
                    orderedList.add(item)
                    seen.add(item.id)
                }
            }
            for (item in userItems) {
                if (item.id !in seen) {
                    orderedList.add(item)
                    seen.add(item.id)
                }
            }
            orderedList.toMutableStateList()
        } else {
            (autoItems + userItems).toMutableStateList()
        }
    }

    var hasDragged by remember { mutableStateOf(false) }

    val reorderableListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        val headerCount = 3
        if (from.index >= headerCount && to.index >= headerCount) {
            val fromIdx = from.index - headerCount
            val toIdx = to.index - headerCount
            if (fromIdx in localUnifiedItems.indices && toIdx in localUnifiedItems.indices) {
                localUnifiedItems.add(toIdx, localUnifiedItems.removeAt(fromIdx))
                hasDragged = true
            }
        }
    }

    val reorderableGridState = rememberReorderableLazyGridState(
        lazyGridState = lazyGridState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        val headerCount = 3
        if (from.index >= headerCount && to.index >= headerCount) {
            val fromIdx = from.index - headerCount
            val toIdx = to.index - headerCount
            if (fromIdx in localUnifiedItems.indices && toIdx in localUnifiedItems.indices) {
                localUnifiedItems.add(toIdx, localUnifiedItems.removeAt(fromIdx))
                hasDragged = true
            }
        }
    }

    LaunchedEffect(reorderableListState.isAnyItemDragging, reorderableGridState.isAnyItemDragging) {
        val isDragging = reorderableListState.isAnyItemDragging || reorderableGridState.isAnyItemDragging
        if (!isDragging && hasDragged) {
            val newOrder = localUnifiedItems.joinToString(",") { it.id }
            customOrder = newOrder
            viewModel.setCustomPlaylistOrder(localUnifiedItems.map { it.id })
            onSortTypeChange(PlaylistSortType.CUSTOM)
            hasDragged = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    overscrollEffect = heroZoom.listOverscroll(),
                    modifier = Modifier.heroPullZoom(heroZoom),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "title",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        LargeScreenTitle(title = stringResource(R.string.playlists))
                    }

                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    items(
                        items = localUnifiedItems,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        ReorderableItem(
                            state = reorderableListState,
                            key = item.id
                        ) { isDragging ->
                            when (item) {
                                is LibraryPlaylistItem.Auto -> {
                                    AutoPlaylistCard(
                                        playlist = item.playlist,
                                        grid = false,
                                        onClick = { navController.navigate(item.route) },
                                        showIconOnly = libraryIconsOnly,
                                        modifier = Modifier
                                            .animateItem()
                                            .longPressDraggableHandle()
                                    )
                                }
                                is LibraryPlaylistItem.User -> {
                                    LibraryPlaylistListItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        playlist = item.playlist,
                                        showIconOnly = libraryIconsOnly,
                                        isCustomSort = true,
                                        dragHandleModifier = Modifier.longPressDraggableHandle(),
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyListState,
                    icon = R.drawable.add,
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    overscrollEffect = heroZoom.listOverscroll(),
                    modifier = Modifier.heroPullZoom(heroZoom),
                    columns = rememberGridColumns(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "title",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        LargeScreenTitle(title = stringResource(R.string.playlists))
                    }

                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    items(
                        items = localUnifiedItems,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        ReorderableItem(
                            state = reorderableGridState,
                            key = item.id
                        ) { isDragging ->
                            when (item) {
                                is LibraryPlaylistItem.Auto -> {
                                    AutoPlaylistCard(
                                        playlist = item.playlist,
                                        grid = true,
                                        onClick = { navController.navigate(item.route) },
                                        showIconOnly = libraryIconsOnly,
                                        modifier = Modifier
                                            .animateItem()
                                            .longPressDraggableHandle()
                                    )
                                }
                                is LibraryPlaylistItem.User -> {
                                    LibraryPlaylistGridItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        playlist = item.playlist,
                                        showIconOnly = libraryIconsOnly,
                                        modifier = Modifier
                                            .animateItem()
                                            .longPressDraggableHandle()
                                    )
                                }
                            }
                        }
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyGridState,
                    icon = R.drawable.add,
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                )
            }
        }

        ListScrollRail(
            lazyListState = lazyListState,
            lazyGridState = lazyGridState,
            isGrid = viewType == LibraryViewType.GRID,
            itemCount = visiblePlaylists.size,
            sectionIndexMap = if (sortType == PlaylistSortType.NAME) {
                remember(visiblePlaylists) {
                    buildAlphabetSectionIndex(visiblePlaylists) { it.playlist.name }
                }
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoPlaylistCard(
    playlist: Playlist,
    grid: Boolean,
    onClick: () -> Unit,
    showIconOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var customUri by rememberPreference(stringPreferencesKey("thumbnail_${playlist.id}"), "")

    val override = customUri.takeIf { it.isNotBlank() }

    var menuOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            customUri = uri.toString()
        }
    }

    val clickMod = Modifier
        .fillMaxWidth()
        .combinedBounceClick(
            onClick = onClick,
            onLongClick = { menuOpen = true },
        )

    Box(modifier) {
        if (grid) {
            PlaylistGridItem(
                playlist = playlist,
                fillMaxWidth = true,
                autoPlaylist = true,
                showIconOnly = showIconOnly,
                thumbnailOverrideUrl = override,
                modifier = clickMod,
            )
        } else {
            PlaylistListItem(
                playlist = playlist,
                autoPlaylist = true,
                showIconOnly = showIconOnly,
                thumbnailOverrideUrl = override,
                modifier = clickMod,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.change_card_image)) },
                onClick = {
                    menuOpen = false
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            if (override != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_custom_image)) },
                    onClick = {
                        menuOpen = false
                        customUri = ""
                    },
                )
            }
        }
    }
}
