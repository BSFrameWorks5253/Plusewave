/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.beatwave.music.LocalPlayerAwareWindowInsets
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.runtime.toMutableStateList
import androidx.datastore.preferences.core.edit
import com.beatwave.music.LocalPlayerConnection
import com.beatwave.music.R
import com.beatwave.music.ui.utils.rememberGridColumns
import com.beatwave.music.constants.AlbumViewTypeKey
import com.beatwave.music.constants.CONTENT_TYPE_HEADER
import com.beatwave.music.constants.CONTENT_TYPE_PLAYLIST
import com.beatwave.music.constants.GridItemSize
import com.beatwave.music.constants.GridItemsSizeKey
import com.beatwave.music.constants.GridThumbnailHeight
import com.beatwave.music.constants.LibraryBackgroundMode
import com.beatwave.music.constants.LibraryBackgroundModeKey
import com.beatwave.music.constants.LibraryIconsOnlyKey
import com.beatwave.music.constants.LibraryViewType
import com.beatwave.music.constants.MixSortDescendingKey
import com.beatwave.music.constants.MixSortType
import com.beatwave.music.constants.MixSortTypeKey
import com.beatwave.music.constants.ShowCachedPlaylistKey
import com.beatwave.music.constants.ShowDownloadedPlaylistKey
import com.beatwave.music.constants.ShowLikedPlaylistKey
import com.beatwave.music.constants.ShowLocalPlaylistKey
import com.beatwave.music.constants.ShowTopPlaylistKey
import com.beatwave.music.constants.ShowUploadedPlaylistKey
import com.beatwave.music.db.entities.Album
import com.beatwave.music.db.entities.Artist
import com.beatwave.music.db.entities.Playlist
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.extensions.reversed
import com.beatwave.music.ui.component.AlbumGridItem
import com.beatwave.music.ui.component.AlbumListItem
import com.beatwave.music.ui.component.ArtistGridItem
import com.beatwave.music.ui.component.ArtistListItem
import com.beatwave.music.ui.component.HeroBackground
import com.beatwave.music.ui.component.LocalMenuState
import com.beatwave.music.ui.component.PlaylistGridItem
import com.beatwave.music.ui.component.CreatePlaylistDialog
import com.beatwave.music.ui.component.HideOnScrollFAB
import com.beatwave.music.ui.component.hasCustomHomeBackground
import com.beatwave.music.ui.component.rememberAppBackgroundColor
import com.beatwave.music.ui.component.PlaylistListItem
import com.beatwave.music.ui.component.SortHeader
import com.beatwave.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.beatwave.music.ui.component.rememberHeroSource
import com.beatwave.music.ui.component.rememberHeroTint
import com.beatwave.music.ui.menu.AlbumMenu
import com.beatwave.music.ui.menu.ArtistMenu
import com.beatwave.music.ui.menu.PlaylistMenu
import com.beatwave.music.ui.theme.AppleTokens
import com.beatwave.music.ui.theme.HeroTintedContent
import com.beatwave.music.ui.utils.bounceClick
import com.beatwave.music.ui.utils.combinedBounceClick
import androidx.datastore.preferences.core.stringPreferencesKey
import com.beatwave.music.utils.rememberEnumPreference
import com.beatwave.music.constants.LocalOnlyModeKey
import com.beatwave.music.constants.CustomPlaylistOrderKey
import com.beatwave.music.utils.rememberPreference
import com.beatwave.music.viewmodels.LibraryMixViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.platform.LocalLocale

sealed interface LibraryMixItem {
    val id: String
    val aliasId: String get() = ""
    data class Auto(
        override val id: String,
        override val aliasId: String,
        val playlist: Playlist,
        val route: String,
        val thumbnailOverrideUrl: String?
    ) : LibraryMixItem
    data class UserPlaylist(val playlist: Playlist) : LibraryMixItem {
        override val id: String = playlist.id
    }
    data class UserAlbum(val album: Album) : LibraryMixItem {
        override val id: String = album.id
    }
    data class UserArtist(val artist: Artist) : LibraryMixItem {
        override val id: String = artist.id
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        MixSortTypeKey,
        MixSortType.CUSTOM
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    
    val (libraryIconsOnly) = rememberPreference(LibraryIconsOnlyKey, defaultValue = true)

    val topSize by viewModel.topValue.collectAsState(initial = 50)
    
    val likedThumbnail by rememberPreference(stringPreferencesKey("thumbnail_${PlaylistEntity.LIKED_PLAYLIST_ID}"), "")
    val likedThumbnailLegacy by rememberPreference(stringPreferencesKey("thumbnail_auto:liked"), "")
    val effectiveLikedThumbnail = likedThumbnail.takeIf { it.isNotBlank() } ?: likedThumbnailLegacy.takeIf { it.isNotBlank() }

    val downloadThumbnail by rememberPreference(stringPreferencesKey("thumbnail_${PlaylistEntity.DOWNLOADED_PLAYLIST_ID}"), "")
    val downloadThumbnailLegacy by rememberPreference(stringPreferencesKey("thumbnail_auto:downloaded"), "")
    val effectiveDownloadThumbnail = downloadThumbnail.takeIf { it.isNotBlank() } ?: downloadThumbnailLegacy.takeIf { it.isNotBlank() }

    val topThumbnail by rememberPreference(stringPreferencesKey("thumbnail_${PlaylistEntity.TOP_PLAYLIST_ID}"), "")
    val topThumbnailLegacy by rememberPreference(stringPreferencesKey("thumbnail_auto:top"), "")
    val effectiveTopThumbnail = topThumbnail.takeIf { it.isNotBlank() } ?: topThumbnailLegacy.takeIf { it.isNotBlank() }

    val cacheThumbnail by rememberPreference(stringPreferencesKey("thumbnail_${PlaylistEntity.CACHED_PLAYLIST_ID}"), "")
    val cacheThumbnailLegacy by rememberPreference(stringPreferencesKey("thumbnail_auto:cached"), "")
    val effectiveCacheThumbnail = cacheThumbnail.takeIf { it.isNotBlank() } ?: cacheThumbnailLegacy.takeIf { it.isNotBlank() }

    val uploadedThumbnail by rememberPreference(stringPreferencesKey("thumbnail_${PlaylistEntity.UPLOADED_PLAYLIST_ID}"), "")
    val uploadedThumbnailLegacy by rememberPreference(stringPreferencesKey("thumbnail_auto:uploaded"), "")
    val effectiveUploadedThumbnail = uploadedThumbnail.takeIf { it.isNotBlank() } ?: uploadedThumbnailLegacy.takeIf { it.isNotBlank() }

    val localThumbnail by rememberPreference(stringPreferencesKey("thumbnail_${PlaylistEntity.LOCAL_PLAYLIST_ID}"), "")
    val localThumbnailLegacy by rememberPreference(stringPreferencesKey("thumbnail_auto:local"), "")
    val effectiveLocalThumbnail = localThumbnail.takeIf { it.isNotBlank() } ?: localThumbnailLegacy.takeIf { it.isNotBlank() }

    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val libraryBackgroundMode by rememberEnumPreference(LibraryBackgroundModeKey, LibraryBackgroundMode.THUMBNAIL_BLUR)

    // LibraryBackgroundMode only applies when no custom image is set. Without this
    // the blurred-artwork hero and its scrim kept rendering under the user's own
    // background, so the same picture looked right on Home and wrong here.
    val customBackground = hasCustomHomeBackground()
    val heroArtworkVisible = !customBackground

    val heroUrl = if (libraryBackgroundMode == LibraryBackgroundMode.THUMBNAIL_BLUR && heroArtworkVisible) {
        (albums + artists + playlists).firstOrNull()?.let {
            when (it) {
                is Album -> it.album.thumbnailUrl
                is Artist -> it.artist.thumbnailUrl
                is Playlist -> it.thumbnails.firstOrNull()
                else -> null
            }
        }
    } else {
        null
    }
    val heroSource = rememberHeroSource(staticArt = heroUrl)
    // Shared resolver, so Library falls back the same way Home and Search do instead of
    // resolving the same preference by hand — its own fallback was colorScheme.primary,
    // a saturated accent, where every other screen falls back to a neutral surface.
    val tint = if (libraryBackgroundMode == LibraryBackgroundMode.THEME) {
        rememberAppBackgroundColor(MaterialTheme.colorScheme.primary)
    } else {
        rememberHeroTint(heroUrl)
    }
    val onTint = AppleTokens.onColor(tint)
    val heroBackdrop = rememberLayerBackdrop()

    // These six are constant for the life of the screen, but were rebuilt on every
    // recomposition and passed straight into keyed lazy items — a new instance each
    // time defeats skipping, so all six rows recomposed on any state change here.
    val likedName = stringResource(R.string.liked)
    val offlineName = stringResource(R.string.offline)
    val myTopName = stringResource(R.string.my_top)
    val cachedName = stringResource(R.string.cached_playlist)
    val uploadedName = stringResource(R.string.uploaded_playlist)
    val localName = stringResource(R.string.filter_local)

    fun fixedPlaylist(id: String, name: String) =
        Playlist(
            playlist = PlaylistEntity(id = id, name = name),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val likedPlaylist = remember(likedName) {
        fixedPlaylist(PlaylistEntity.LIKED_PLAYLIST_ID, likedName)
    }
    val downloadPlaylist = remember(offlineName) {
        fixedPlaylist(PlaylistEntity.DOWNLOADED_PLAYLIST_ID, offlineName)
    }
    val topPlaylist = remember(myTopName, topSize) {
        fixedPlaylist(PlaylistEntity.TOP_PLAYLIST_ID, "$myTopName $topSize")
    }
    val cachePlaylist = remember(cachedName) {
        fixedPlaylist(PlaylistEntity.CACHED_PLAYLIST_ID, cachedName)
    }
    val uploadedPlaylist = remember(uploadedName) {
        fixedPlaylist(PlaylistEntity.UPLOADED_PLAYLIST_ID, uploadedName)
    }
    val localPlaylist = remember(localName) {
        fixedPlaylist(PlaylistEntity.LOCAL_PLAYLIST_ID, localName)
    }

    // Every auto playlist except Local is built from YouTube state (likes, YT
    // downloads, listening history, uploads), so local-only mode leaves just the one.
    val (localOnly) = rememberPreference(LocalOnlyModeKey, false)
    val (showLikedPref) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloadedPref) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTopPref) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCachedPref) = rememberPreference(ShowCachedPlaylistKey, true)
    val (showUploadedPref) = rememberPreference(ShowUploadedPlaylistKey, true)
    val (showLocal) = rememberPreference(ShowLocalPlaylistKey, true)
    val showLiked = showLikedPref && !localOnly
    val showDownloaded = showDownloadedPref && !localOnly
    val showTop = showTopPref && !localOnly
    val showCached = showCachedPref && !localOnly
    val showUploaded = showUploadedPref && !localOnly

    var customOrder by rememberPreference(CustomPlaylistOrderKey, "")

    val autoItems = remember(
        showLiked, showDownloaded, showTop, showCached, showUploaded, showLocal,
        likedPlaylist, downloadPlaylist, topPlaylist, cachePlaylist, uploadedPlaylist, localPlaylist,
        effectiveLikedThumbnail, effectiveDownloadThumbnail, effectiveTopThumbnail, effectiveCacheThumbnail, effectiveUploadedThumbnail, effectiveLocalThumbnail
    ) {
        buildList {
            if (showLiked) add(LibraryMixItem.Auto(PlaylistEntity.LIKED_PLAYLIST_ID, "auto:liked", likedPlaylist, "auto_playlist/liked", effectiveLikedThumbnail))
            if (showDownloaded) add(LibraryMixItem.Auto(PlaylistEntity.DOWNLOADED_PLAYLIST_ID, "auto:downloaded", downloadPlaylist, "auto_playlist/downloaded", effectiveDownloadThumbnail))
            if (showTop) add(LibraryMixItem.Auto(PlaylistEntity.TOP_PLAYLIST_ID, "auto:top", topPlaylist, "top_playlist/$topSize", effectiveTopThumbnail))
            if (showCached) add(LibraryMixItem.Auto(PlaylistEntity.CACHED_PLAYLIST_ID, "auto:cached", cachePlaylist, "cache_playlist/cached", effectiveCacheThumbnail))
            if (showUploaded) add(LibraryMixItem.Auto(PlaylistEntity.UPLOADED_PLAYLIST_ID, "auto:uploaded", uploadedPlaylist, "auto_playlist/uploaded", effectiveUploadedThumbnail))
            if (showLocal) add(LibraryMixItem.Auto(PlaylistEntity.LOCAL_PLAYLIST_ID, "auto:local", localPlaylist, "auto_playlist/local", effectiveLocalThumbnail))
        }
    }

    val platformLocale = LocalLocale.current.platformLocale
    val userItems = remember(albums, playlists, artists, sortType, sortDescending, platformLocale) {
        if (sortType == MixSortType.CUSTOM) {
            (playlists.map { LibraryMixItem.UserPlaylist(it) } +
             albums.map { LibraryMixItem.UserAlbum(it) } +
             artists.map { LibraryMixItem.UserArtist(it) }).distinctBy { it.id }
        } else {
            val collator = Collator.getInstance(platformLocale)
            collator.strength = Collator.PRIMARY
            var items = albums + playlists
            items = when (sortType) {
                MixSortType.CREATE_DATE -> items.sortedBy { item ->
                    when (item) {
                        is Album -> item.album.bookmarkedAt
                        is Artist -> item.artist.bookmarkedAt
                        is Playlist -> item.playlist.createdAt
                        else -> LocalDateTime.now()
                    }
                }
                MixSortType.NAME -> items.sortedWith(
                    compareBy(collator) { item ->
                        when (item) {
                            is Album -> item.album.title
                            is Artist -> item.artist.name
                            is Playlist -> item.playlist.name
                            else -> ""
                        }
                    }
                )
                MixSortType.LAST_UPDATED -> items.sortedBy { item ->
                    when (item) {
                        is Album -> item.album.lastUpdateTime
                        is Artist -> item.artist.lastUpdateTime
                        is Playlist -> item.playlist.lastUpdateTime
                        else -> LocalDateTime.now()
                    }
                }
                MixSortType.CUSTOM -> items
            }.reversed(sortDescending)
            items.map { item ->
                when (item) {
                    is Playlist -> LibraryMixItem.UserPlaylist(item)
                    is Album -> LibraryMixItem.UserAlbum(item)
                    is Artist -> LibraryMixItem.UserArtist(item)
                    else -> error("Unknown item")
                }
            }
        }
    }

    val localUnifiedItems = remember(autoItems, userItems, sortType, customOrder) {
        val allMap = mutableMapOf<String, LibraryMixItem>()
        for (item in autoItems) {
            allMap[item.id] = item
            allMap[item.aliasId] = item
        }
        for (item in userItems) {
            allMap[item.id] = item
        }
        if (sortType == MixSortType.CUSTOM && customOrder.isNotBlank()) {
            val orderIds = customOrder.split(",").filter { it.isNotBlank() }
            val orderedList = mutableListOf<LibraryMixItem>()
            val seen = mutableSetOf<String>()
            for (id in orderIds) {
                allMap[id]?.let {
                    if (seen.add(it.id)) {
                        orderedList.add(it)
                    }
                }
            }
            for (item in autoItems) {
                if (seen.add(item.id)) {
                    orderedList.add(item)
                }
            }
            for (item in userItems) {
                if (seen.add(item.id)) {
                    orderedList.add(item)
                }
            }
            orderedList.toMutableStateList()
        } else {
            (autoItems + userItems).toMutableStateList()
        }
    }

    var hasDragged by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val reorderableListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        val headerCount = 2
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
        val headerCount = 2
        if (from.index >= headerCount && to.index >= headerCount) {
            val fromIdx = from.index - headerCount
            val toIdx = to.index - headerCount
            if (fromIdx in localUnifiedItems.indices && toIdx in localUnifiedItems.indices) {
                localUnifiedItems.add(toIdx, localUnifiedItems.removeAt(fromIdx))
                hasDragged = true
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(reorderableListState.isAnyItemDragging, reorderableGridState.isAnyItemDragging) {
        val isDragging = reorderableListState.isAnyItemDragging || reorderableGridState.isAnyItemDragging
        if (!isDragging && hasDragged) {
            val newOrder = localUnifiedItems.joinToString(",") { it.id }
            customOrder = newOrder
            context.dataStore.edit { preferences ->
                preferences[CustomPlaylistOrderKey] = newOrder
                preferences[MixSortTypeKey] = MixSortType.CUSTOM.name
            }
            onSortTypeChange(MixSortType.CUSTOM)
            hasDragged = false
        }
    }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            }
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
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
                        MixSortType.CUSTOM -> R.string.sort_by_custom
                        MixSortType.CREATE_DATE -> R.string.sort_by_create_date
                        MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                        MixSortType.NAME -> R.string.sort_by_name
                    }
                },
            )

            Spacer(Modifier.weight(1f))

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

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    HeroBackground(
        // HeroBackground fills its whole box with this tint, opaquely. LibraryScreen draws
        // HomeImageBackground BEHIND this composable, so an opaque tint here hid the user's
        // wallpaper completely — suppressing only the hero artwork (heroArtworkVisible) was
        // never enough, the flat colour underneath still covered it.
        tint = if (customBackground) Color.Transparent else tint,
        heroSource = heroSource,
        showDefaultIcon = libraryBackgroundMode != LibraryBackgroundMode.PLAIN && heroArtworkVisible,
        blurArtwork = libraryBackgroundMode == LibraryBackgroundMode.THUMBNAIL_BLUR && heroArtworkVisible,
        fullBlur = true,
        modifier = Modifier.fillMaxSize(),
    ) {
      if (libraryBackgroundMode == LibraryBackgroundMode.THUMBNAIL_BLUR && heroArtworkVisible) {
          // Blurred thumbnail alone isn't dark/flat enough for text on top to
          // stay readable — a uniform scrim, not just the bottom gradient.
          Box(
              modifier = Modifier
                  .matchParentSize()
                  .background(Color.Black.copy(alpha = 0.35f))
          )
      }
      HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            indicator = {
                // Only while visible — see HomeScreen: the M3 LoadingIndicator
                // animates forever once composed and pins the app at full frame
                // rate at idle.
                if (isRefreshing || pullRefreshState.distanceFraction > 0f) {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        ) {
            when (viewType) {
                LibraryViewType.LIST ->
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier,
                        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    ) {
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
                                    is LibraryMixItem.Auto -> {
                                        PlaylistListItem(
                                            playlist = item.playlist,
                                            autoPlaylist = true,
                                            flat = true,
                                            showIconOnly = libraryIconsOnly,
                                            thumbnailOverrideUrl = item.thumbnailOverrideUrl,
                                            trailingContent = {
                                                if (sortType == MixSortType.CUSTOM) {
                                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                                        Icon(painterResource(R.drawable.drag_handle), contentDescription = "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedBounceClick(
                                                    onClick = { navController.navigate(item.route) },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            PlaylistMenu(
                                                                playlist = item.playlist,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                                autoPlaylist = true
                                                            )
                                                        }
                                                    }
                                                )
                                                .animateItem()
                                        )
                                    }
                                    is LibraryMixItem.UserPlaylist -> {
                                        PlaylistListItem(
                                            playlist = item.playlist,
                                            flat = true,
                                            showIconOnly = libraryIconsOnly,
                                            trailingContent = {
                                                if (sortType == MixSortType.CUSTOM) {
                                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                                        Icon(painterResource(R.drawable.drag_handle), contentDescription = "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        menuState.show {
                                                            PlaylistMenu(
                                                                playlist = item.playlist,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedBounceClick(
                                                    onClick = { navController.navigate("local_playlist/${item.playlist.id}") },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            PlaylistMenu(
                                                                playlist = item.playlist,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                )
                                                .animateItem()
                                        )
                                    }
                                    is LibraryMixItem.UserAlbum -> {
                                        AlbumListItem(
                                            album = item.album,
                                            isActive = item.album.id == mediaMetadata?.album?.id,
                                            isPlaying = isPlaying,
                                            flat = true,
                                            showIconOnly = libraryIconsOnly,
                                            trailingContent = {
                                                if (sortType == MixSortType.CUSTOM) {
                                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                                        Icon(painterResource(R.drawable.drag_handle), contentDescription = "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = item.album,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedBounceClick(
                                                    onClick = { navController.navigate("album/${item.album.id}") },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = item.album,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                )
                                                .animateItem()
                                        )
                                    }
                                    is LibraryMixItem.UserArtist -> {
                                        ArtistListItem(
                                            artist = item.artist,
                                            flat = true,
                                            showIconOnly = libraryIconsOnly,
                                            trailingContent = {
                                                if (sortType == MixSortType.CUSTOM) {
                                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                                        Icon(painterResource(R.drawable.drag_handle), contentDescription = "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        menuState.show {
                                                            ArtistMenu(
                                                                originalArtist = item.artist,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedBounceClick(
                                                    onClick = { navController.navigate("artist/${item.artist.id}") },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            ArtistMenu(
                                                                originalArtist = item.artist,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                )
                                                .animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    modifier = Modifier,
                    columns = rememberGridColumns(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
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
                                is LibraryMixItem.Auto -> {
                                    PlaylistGridItem(
                                        playlist = item.playlist,
                                        fillMaxWidth = true,
                                        autoPlaylist = true,
                                        showIconOnly = true,
                                        thumbnailOverrideUrl = item.thumbnailOverrideUrl,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedBounceClick(
                                                onClick = { navController.navigate(item.route) },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        PlaylistMenu(
                                                            playlist = item.playlist,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                            autoPlaylist = true
                                                        )
                                                    }
                                                }
                                            )
                                            .longPressDraggableHandle()
                                            .animateItem()
                                    )
                                }
                                is LibraryMixItem.UserPlaylist -> {
                                    PlaylistGridItem(
                                        playlist = item.playlist,
                                        fillMaxWidth = true,
                                        autoPlaylist = false,
                                        showIconOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedBounceClick(
                                                onClick = { navController.navigate("local_playlist/${item.playlist.id}") },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        PlaylistMenu(
                                                            playlist = item.playlist,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                            autoPlaylist = false
                                                        )
                                                    }
                                                }
                                            )
                                            .longPressDraggableHandle()
                                            .animateItem()
                                    )
                                }
                                is LibraryMixItem.UserAlbum -> {
                                    AlbumGridItem(
                                        album = item.album,
                                        isActive = item.album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedBounceClick(
                                                onClick = { navController.navigate("album/${item.album.id}") },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = item.album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            )
                                            .longPressDraggableHandle()
                                            .animateItem()
                                    )
                                }
                                is LibraryMixItem.UserArtist -> {
                                    ArtistGridItem(
                                        artist = item.artist,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedBounceClick(
                                                onClick = { navController.navigate("artist/${item.artist.id}") },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        ArtistMenu(
                                                            originalArtist = item.artist,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            )
                                            .longPressDraggableHandle()
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }

            when (viewType) {
                LibraryViewType.LIST -> HideOnScrollFAB(
                    lazyListState = lazyListState,
                    icon = R.drawable.add,
                    onClick = { showCreatePlaylistDialog = true },
                )
                LibraryViewType.GRID -> HideOnScrollFAB(
                    lazyListState = lazyGridState,
                    icon = R.drawable.add,
                    onClick = { showCreatePlaylistDialog = true },
                )
            }
        }
      }
    }
}
