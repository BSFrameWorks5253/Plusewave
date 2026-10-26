package com.beatwave.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatwave.music.sync.DesktopSyncManager
import com.beatwave.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val DesktopSyncHostKey = stringPreferencesKey("desktopSyncHost")
val DesktopSyncLastTimeKey = stringPreferencesKey("desktopSyncLastTime")

@HiltViewModel
class DesktopSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncManager: DesktopSyncManager,
) : ViewModel() {

    data class UiState(
        val host: String = "192.168.29.215",
        val port: Int = 5253,
        val isChecking: Boolean = false,
        val isSyncing: Boolean = false,
        val isConnected: Boolean = false,
        val desktopName: String? = null,
        val lastSyncTime: String? = null,
        val message: String? = null,
        val error: String? = null,
        val syncedPlaylistsCount: Int = 0,
        val remotePlaying: Boolean = false,
        val remoteVolume: Float = 0.8f,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedHost = context.dataStore.data.first()[DesktopSyncHostKey] ?: "192.168.29.215"
            val lastTime = context.dataStore.data.first()[DesktopSyncLastTimeKey]
            _uiState.update { it.copy(host = savedHost, lastSyncTime = lastTime) }
            testConnection()
        }
    }

    fun updateHost(newHost: String) {
        _uiState.update { it.copy(host = newHost, isConnected = false, error = null) }
        viewModelScope.launch {
            context.dataStore.edit { it[DesktopSyncHostKey] = newHost.trim() }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null, message = null) }
            val result = syncManager.checkDesktopStatus(_uiState.value.host, _uiState.value.port)
            result.onSuccess { status ->
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        isConnected = true,
                        desktopName = status.device,
                        message = "Connected to ${status.device} (${status.playlistCount} playlists ready)",
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        isConnected = false,
                        desktopName = null,
                        error = "Could not reach Desktop app at ${_uiState.value.host}:${_uiState.value.port}. Check if both devices are on the same Wi-Fi and PulseWave Desktop is open.",
                    )
                }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null, message = null) }
            val result = syncManager.syncWithDesktop(_uiState.value.host, _uiState.value.port)
            result.onSuccess { res ->
                val timeNow = java.text.SimpleDateFormat("hh:mm a, MMM dd", java.util.Locale.getDefault()).format(java.util.Date())
                context.dataStore.edit { it[DesktopSyncLastTimeKey] = timeNow }

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        isConnected = true,
                        desktopName = res.desktopDevice,
                        lastSyncTime = timeNow,
                        message = "Sync Complete! ${res.syncedPlaylistsCount} playlists & ${res.syncedSongsCount} songs synchronized.",
                        syncedPlaylistsCount = res.syncedPlaylistsCount,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        error = "Sync Failed: ${err.localizedMessage ?: "Unknown error"}. Ensure both devices are on the same Wi-Fi network.",
                    )
                }
            }
        }
    }

    fun sendRemoteCommand(action: String, value: Any? = null) {
        viewModelScope.launch {
            val res = syncManager.sendRemotePlaybackCommand(_uiState.value.host, action, value, _uiState.value.port)
            res.onSuccess {
                when (action) {
                    "play" -> _uiState.update { it.copy(remotePlaying = true) }
                    "pause" -> _uiState.update { it.copy(remotePlaying = false) }
                    "volume" -> if (value is Number) {
                        _uiState.update { it.copy(remoteVolume = value.toFloat()) }
                    }
                }
            }
        }
    }
}
