const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('pulseApi', {
    getSyncInfo: () => ipcRenderer.invoke('get-sync-info'),
    getPlaylists: () => ipcRenderer.invoke('get-playlists'),
    savePlaylist: (playlist) => ipcRenderer.invoke('save-playlist', playlist),
    deletePlaylist: (playlistId) => ipcRenderer.invoke('delete-playlist', playlistId),
    triggerManualSyncWithPhone: (phoneIp) => ipcRenderer.invoke('sync-with-phone', phoneIp),
    onPlaylistsUpdated: (callback) => {
        ipcRenderer.on('playlists-updated', (event, playlists) => callback(playlists));
    },
    minimizeWindow: () => ipcRenderer.send('minimize-window'),
    maximizeWindow: () => ipcRenderer.send('maximize-window'),
    closeWindow: () => ipcRenderer.send('close-window')
});
