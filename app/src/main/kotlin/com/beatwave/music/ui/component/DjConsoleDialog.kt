/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.beatwave.music.LocalDatabase
import com.beatwave.music.LocalPlayerConnection
import com.beatwave.music.R
import com.beatwave.music.extensions.metadata
import com.beatwave.music.extensions.toMediaItem
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.models.toMediaMetadata
import com.beatwave.music.playback.DjConsoleController
import com.beatwave.music.playback.dj.TransitionStyle
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Legacy alias for backward compatibility */
enum class MashupTransitionStyle(val title: String, val subtitle: String) {
    LINE_BY_LINE("Beat Swap", "Quantized phrase swap on master beat boundary"),
    VERSE_BY_VERSE("Filter Sweep", "Dramatic resonant high-pass club riser into drop"),
    ACAPELLA_SWAP("Vocal Hook Swap", "Lead vocal and melody swap over active rhythm")
}

/**
 * Professional DJ Console Dialog.
 * Replaces DJ Mashup with an interactive twin-deck DJ Console featuring:
 * - Real-time DJ transitions (Beat Swap, Vocal Swap, Filter Sweep, Bass Drop, Vinyl Brake, Echo Freeze)
 * - Interactive Deck A & Deck B with vinyl rotation
 * - Live Master BPM & Musical Key detection
 * - Tactile Crossfader & one-tap DJ Transition trigger
 * - Auto-DJ crossfade replacement mode
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DjConsoleDialog(
    initialSong: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    val allLibrarySongs by database.allSongs().collectAsState(initial = emptyList())
    val likedSongs by database.likedSongsByCreateDateAsc().collectAsState(initial = emptyList())
    val queueWindows by (playerConnection?.queueWindows ?: MutableStateFlow(emptyList())).collectAsState()
    val djState by (playerConnection?.djState ?: MutableStateFlow(com.beatwave.music.playback.dj.DjState())).collectAsState()

    val queueSongs = remember(queueWindows, allLibrarySongs) {
        val songMap = allLibrarySongs.associateBy { it.id }
        val fromWindows = queueWindows.mapNotNull { window ->
            window.mediaItem.metadata ?: songMap[window.mediaItem.mediaId]?.toMediaMetadata()
        }
        val fromPlayer = try {
            playerConnection?.player?.let { p ->
                (0 until p.mediaItemCount).mapNotNull { idx ->
                    val item = p.getMediaItemAt(idx)
                    item.metadata ?: songMap[item.mediaId]?.toMediaMetadata()
                }
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        (fromWindows + fromPlayer).distinctBy { it.id }
    }

    val selectedTracks = remember { mutableStateListOf(initialSong) }
    var selectedEffect by remember { mutableStateOf(DjConsoleController.selectedEffect) }
    var transitionDurationMs by remember { mutableStateOf(DjConsoleController.transitionDurationMs) }
    var activeSourceTab by remember { mutableStateOf(if (queueSongs.isNotEmpty()) "QUEUE" else "ALL") }
    var crossfaderPos by remember { mutableFloatStateOf(DjConsoleController.crossfaderPosition) }
    var autoDjOnTrackEnd by remember { mutableStateOf(DjConsoleController.autoDjOnTrackEnd) }
    var showAdditionalDecks by remember { mutableStateOf(false) }

    LaunchedEffect(queueSongs) {
        if (queueSongs.isNotEmpty() && activeSourceTab == "ALL") {
            activeSourceTab = "QUEUE"
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var onlineResults by remember { mutableStateOf<List<MediaMetadata>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    // Auto-populate Deck B from next queue song or liked songs so DJ can transition in 1 tap
    LaunchedEffect(queueSongs, likedSongs, allLibrarySongs) {
        if (selectedTracks.size == 1) {
            val candidate = queueSongs.firstOrNull { it.id != initialSong.id }
                ?: likedSongs.firstOrNull { it.id != initialSong.id }?.toMediaMetadata()
                ?: allLibrarySongs.firstOrNull { it.id != initialSong.id }?.toMediaMetadata()
            if (candidate != null && selectedTracks.none { it.id == candidate.id }) {
                selectedTracks.add(candidate)
            }
        }
    }

    // Debounced online search
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            delay(350L)
            isSearchingOnline = true
            withContext(Dispatchers.IO) {
                val res = YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val songItems = res?.items?.filterIsInstance<SongItem>().orEmpty()
                onlineResults = songItems.map { it.toMediaMetadata() }
            }
            isSearchingOnline = false
        } else {
            onlineResults = emptyList()
            isSearchingOnline = false
        }
    }

    val deckA = selectedTracks.getOrNull(0) ?: initialSong
    val deckB = selectedTracks.getOrNull(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .fillMaxHeight(0.96f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.equalizer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🎛️ PRO DJ CONSOLE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (DjConsoleController.isActive) Color(0xFF00E676).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (DjConsoleController.isActive) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (DjConsoleController.isActive) "● LIVE ON AIR" else "READY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (DjConsoleController.isActive) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    val bpmText = if (djState.currentBpm > 0f) "${djState.currentBpm.toInt()} BPM" else "BPM Auto-Detect"
                    val keyText = djState.currentKey ?: "Key Lock"
                    Text(
                        text = "Master: $bpmText • $keyText • Dynamic Phrase Mixing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ==================== TWIN DJ DECKS DISPLAY ====================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // DECK A (MASTER)
                    Box(modifier = Modifier.weight(1f)) {
                        DjDeckConsoleCard(
                            deckLabel = "DECK A (MASTER)",
                            badgeColor = Color(0xFF00E5FF),
                            track = deckA,
                            isPlaying = playerConnection?.player?.isPlaying == true,
                            bpm = djState.currentBpm,
                            musicalKey = djState.currentKey,
                            onRemove = null,
                            onEmptyClick = {
                                activeSourceTab = "QUEUE"
                                Toast.makeText(context, "Select track for Deck A", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // DECK B (CUE / NEXT)
                    Box(modifier = Modifier.weight(1f)) {
                        DjDeckConsoleCard(
                            deckLabel = "DECK B (NEXT CUE)",
                            badgeColor = Color(0xFFFF007F),
                            track = deckB,
                            isPlaying = false,
                            bpm = djState.nextBpm,
                            musicalKey = djState.nextKey,
                            onRemove = {
                                if (deckB != null) selectedTracks.remove(deckB)
                            },
                            onEmptyClick = {
                                activeSourceTab = "QUEUE"
                                Toast.makeText(context, "Select next song from Queue below", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // ==================== TACTILE CROSSFADER & TRANSITION TRIGGER ====================
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "DECK A",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E5FF)
                            )
                            Text(
                                text = "CROSSFADER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "DECK B",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF007F)
                            )
                        }

                        Slider(
                            value = crossfaderPos,
                            onValueChange = {
                                crossfaderPos = it
                                DjConsoleController.crossfaderPosition = it
                            },
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color(0xFFFF007F).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Big Tactile DJ Transition Button
                        Button(
                            onClick = {
                                if (playerConnection != null) {
                                    DjConsoleController.selectedEffect = selectedEffect
                                    DjConsoleController.transitionDurationMs = transitionDurationMs
                                    DjConsoleController.triggerDjTransition(
                                        playerConnection = playerConnection,
                                        targetTrack = deckB,
                                        effect = selectedEffect,
                                        durationMs = transitionDurationMs,
                                    )
                                    Toast.makeText(
                                        context,
                                        "⚡ Transitioning to ${deckB?.title ?: "next track"} via ${selectedEffect.title}!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.sparks),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TRANSITION NOW • ${selectedEffect.title.uppercase()} (${transitionDurationMs / 1000f}s)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // ==================== DJ TRANSITION EFFECT SELECTOR ====================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "DJ TRANSITION EFFECT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedEffect.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TransitionStyle.values().forEach { style ->
                        val isSel = selectedEffect == style
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedEffect = style
                                DjConsoleController.selectedEffect = style
                            },
                            label = {
                                Text(
                                    text = style.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Transition Description Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 ${selectedEffect.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // ==================== TRANSITION DURATION PRESETS ====================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Duration:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        1500L to "1.5s (Drop)",
                        3000L to "3.0s (Club)",
                        5000L to "5.0s (Build)",
                        8000L to "8.0s (Phrase)"
                    ).forEach { (ms, lbl) ->
                        val isChosen = transitionDurationMs == ms
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.clickable {
                                transitionDurationMs = ms
                                DjConsoleController.transitionDurationMs = ms
                            }
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                color = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // ==================== AUTO DJ CROSSFADE TOGGLE ====================
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto DJ Transition on Track End",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Applies ${selectedEffect.title} automatically instead of plain volume crossfade",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDjOnTrackEnd,
                            onCheckedChange = {
                                autoDjOnTrackEnd = it
                                DjConsoleController.autoDjOnTrackEnd = it
                                playerConnection?.service?.djEngine?.enabled = it
                                if (it) {
                                    playerConnection?.service?.djEngine?.creativeEnabled = true
                                }
                            }
                        )
                    }
                }

                // ==================== CANDIDATE SONG SELECTOR TABS ====================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "QUEUE" to "Queue (${queueSongs.size})",
                        "ALL" to "All",
                        "LIKED" to "Liked (${likedSongs.size})",
                        "LIBRARY" to "Library (${allLibrarySongs.size})"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = activeSourceTab == key,
                            onClick = { activeSourceTab = key },
                            leadingIcon = if (key == "QUEUE") {
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (activeSourceTab == key) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search track from Queue, Library, or YouTube...",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    )
                )

                // Candidate Songs List
                val candidateSongs = remember(allLibrarySongs, likedSongs, queueSongs, onlineResults, searchQuery, activeSourceTab) {
                    val q = searchQuery.trim()
                    val baseList: List<MediaMetadata> = when (activeSourceTab) {
                        "QUEUE" -> queueSongs
                        "LIKED" -> likedSongs.map { it.toMediaMetadata() }
                        "LIBRARY" -> allLibrarySongs.map { it.toMediaMetadata() }
                        else -> (queueSongs + likedSongs.map { it.toMediaMetadata() } + allLibrarySongs.map { it.toMediaMetadata() }).distinctBy { it.id }
                    }

                    if (q.isEmpty()) {
                        baseList.take(60)
                    } else {
                        val localMatches = baseList.filter { meta ->
                            meta.title.contains(q, ignoreCase = true) ||
                                meta.artists.any { it.name.contains(q, ignoreCase = true) }
                        }
                        (localMatches + onlineResults).distinctBy { it.id }.take(60)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (candidateSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSearchingOnline) "Searching YouTube..."
                                    else if (searchQuery.isNotEmpty()) "No matching tracks found"
                                    else if (activeSourceTab == "QUEUE") "No tracks in active queue"
                                    else "No tracks found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(candidateSongs, key = { it.id }) { meta ->
                            val isSelected = selectedTracks.any { it.id == meta.id }
                            val isDeckA = selectedTracks.firstOrNull()?.id == meta.id
                            val slotIndex = selectedTracks.indexOfFirst { it.id == meta.id }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            if (!isDeckA) selectedTracks.remove(meta)
                                        } else {
                                            if (selectedTracks.size < 4) {
                                                selectedTracks.add(meta)
                                                Toast.makeText(context, "Added to Deck ${selectedTracks.size}: ${meta.title}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                selectedTracks[1] = meta
                                                Toast.makeText(context, "Set Deck B: ${meta.title}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    AsyncImage(
                                        model = meta.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = meta.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = meta.artists.joinToString(", ") { it.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isDeckA) Color(0xFF00E5FF) else Color(0xFFFF007F),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isDeckA) "DECK A" else if (slotIndex == 1) "DECK B" else "DECK ${slotIndex + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painter = painterResource(R.drawable.add),
                                                    contentDescription = "Arm to Deck",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedTracks.size >= 2) {
                    FilledTonalButton(
                        onClick = {
                            if (playerConnection != null) {
                                DjConsoleController.startContinuousDjSet(
                                    selectedTracks = selectedTracks.toList(),
                                    effect = selectedEffect,
                                    playerConnection = playerConnection
                                )
                                Toast.makeText(
                                    context,
                                    "Continuous DJ Set started with ${selectedTracks.size} tracks (${selectedEffect.title})",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.equalizer),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Continuous DJ Set", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (DjConsoleController.isActive) {
                Button(
                    onClick = {
                        DjConsoleController.stopDjConsole()
                        Toast.makeText(context, "DJ Console stopped", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop DJ Mode", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    )
}

/**
 * High-visibility DJ Deck Card with rotating vinyl visualizer and status indicators.
 */
@Composable
private fun DjDeckConsoleCard(
    deckLabel: String,
    badgeColor: Color,
    track: MediaMetadata?,
    isPlaying: Boolean,
    bpm: Float,
    musicalKey: String?,
    onRemove: (() -> Unit)?,
    onEmptyClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = badgeColor.copy(alpha = if (track != null) 0.85f else 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clickable(enabled = track == null, onClick = onEmptyClick)
    ) {
        if (track != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text = deckLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (onRemove != null) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "Clear Deck",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Vinyl Turntable Visualizer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(1.5.dp, Color(0xFF333333), CircleShape)
                    ) {
                        // Vinyl Grooves
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF222222), CircleShape)
                        )
                        // Spinning Album Center
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .rotate(if (isPlaying) rotation else 0f)
                        )
                        // Vinyl Center Spindle hole
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // BPM & Key Readout Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (bpm > 0f) "%.1f BPM".format(bpm) else "AUTO BPM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (musicalKey != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "KEY $musicalKey",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = badgeColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = "Load Deck",
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deckLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    color = badgeColor
                )
                Text(
                    text = "Tap to load next track",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
