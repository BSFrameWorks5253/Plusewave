package com.beatwave.music.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.shapes.ContinuousRoundedRectangle
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain
import com.beatwave.music.viewmodels.DesktopSyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSyncScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: DesktopSyncViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080A))
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Connection Status Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(20.dp))
                .background(if (state.isConnected) Color(0x151DB954) else Color(0x158B5CF6))
                .border(
                    0.5.dp,
                    if (state.isConnected) Color(0x401DB954) else Color(0x308B5CF6),
                    ContinuousRoundedRectangle(20.dp),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (state.isConnected) Color(0xFF1DB954) else Color(0xFFF59E0B)),
                    )
                    Text(
                        text = if (state.isConnected) "Desktop Online • ${state.desktopName ?: "Connected"}" else "Looking for PulseWave Desktop",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Text(
                    text = "Sync playlists seamlessly between this phone and your PC over the same local Wi-Fi connection.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                )

                if (state.lastSyncTime != null) {
                    Text(
                        text = "Last synced: ${state.lastSyncTime}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B5CF6),
                    )
                }
            }
        }

        // Host IP Configuration Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(20.dp))
                .background(Color(0xFF111113))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), ContinuousRoundedRectangle(20.dp))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "DESKTOP LOCAL IP ADDRESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )

                OutlinedTextField(
                    value = state.host,
                    onValueChange = viewModel::updateHost,
                    label = { Text("PC Local Wi-Fi IP (e.g. 192.168.1.xxx)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedLabelColor = Color(0xFF8B5CF6),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = viewModel::testConnection,
                        enabled = !state.isChecking && !state.isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF222226),
                            contentColor = Color.White,
                        ),
                        shape = ContinuousRoundedRectangle(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test Connection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = viewModel::syncNow,
                        enabled = !state.isChecking && !state.isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White,
                        ),
                        shape = ContinuousRoundedRectangle(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(painterResource(R.drawable.sync), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Live Message or Error Feedback
        AnimatedVisibility(visible = state.message != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(14.dp))
                    .background(Color(0x201DB954))
                    .border(0.5.dp, Color(0x501DB954), ContinuousRoundedRectangle(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = state.message.orEmpty(),
                    fontSize = 13.sp,
                    color = Color(0xFF1DB954),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        AnimatedVisibility(visible = state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(14.dp))
                    .background(Color(0x20EF4444))
                    .border(0.5.dp, Color(0x50EF4444), ContinuousRoundedRectangle(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = state.error.orEmpty(),
                    fontSize = 13.sp,
                    color = Color(0xFFF87171),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Pulse Connect Remote Playback Card
        AnimatedVisibility(visible = state.isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .background(Color(0xFF141418))
                    .border(0.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f), ContinuousRoundedRectangle(20.dp))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "PULSE CONNECT REMOTE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFF8B5CF6),
                            )
                            Text(
                                text = "Control PC Playback Live",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(ContinuousRoundedRectangle(8.dp))
                                .background(Color(0x208B5CF6))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("WI-FI ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        }
                    }

                    // Transport Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.FilledIconButton(
                            onClick = { viewModel.sendRemoteCommand("previous") },
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF222228),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(painterResource(R.drawable.skip_previous), contentDescription = "Previous")
                        }

                        androidx.compose.material3.FilledIconButton(
                            onClick = {
                                viewModel.sendRemoteCommand(if (state.remotePlaying) "pause" else "play")
                            },
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF8B5CF6),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                painterResource(if (state.remotePlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = if (state.remotePlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        androidx.compose.material3.FilledIconButton(
                            onClick = { viewModel.sendRemoteCommand("next") },
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF222228),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(painterResource(R.drawable.skip_next), contentDescription = "Next")
                        }
                    }

                    // Volume Control
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Desktop Volume", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text("${(state.remoteVolume * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        }
                        androidx.compose.material3.Slider(
                            value = state.remoteVolume,
                            onValueChange = { newVol ->
                                viewModel.sendRemoteCommand("volume", newVol)
                            },
                            valueRange = 0f..1f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color(0xFF8B5CF6),
                                activeTrackColor = Color(0xFF8B5CF6),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        }

        // Instructions Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(20.dp))
                .background(Color(0xFF111113))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), ContinuousRoundedRectangle(20.dp))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "HOW WI-FI PLAYLIST SYNC WORKS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )

                Text(
                    text = "1. Connect your phone and PC to the same Wi-Fi router / internet network.\n" +
                        "2. Open PulseWave Desktop on your PC.\n" +
                        "3. Check the IP displayed on your desktop top bar, or tap 'Test Connection' above.\n" +
                        "4. Tap 'Sync Now' — your playlists, liked songs, and tracks are instantly synchronized both ways!",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 20.sp,
                )
            }
        }
    }

    // TopAppBar Header
    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF08080A),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
        ),
        title = { Text("Desktop Wi-Fi Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = "Back",
                )
            }
        },
    )
}
