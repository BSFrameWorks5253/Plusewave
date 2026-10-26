const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const SyncServer = require('./sync-server');

let mainWindow = null;
let syncServer = null;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 820,
        minWidth: 900,
        minHeight: 600,
        title: 'PulseWave Desktop',
        backgroundColor: '#08080A',
        titleBarStyle: 'hidden',
        titleBarOverlay: {
            color: '#08080A',
            symbolColor: '#E2E8F0',
            height: 38
        },
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false
        }
    });

    mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

app.whenReady().then(async () => {
    // Start local Wi-Fi playlist sync server
    const storePath = path.join(app.getPath('userData'), 'pulsewave_playlists.json');
    syncServer = new SyncServer(5253, storePath, (updatedPlaylists) => {
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.webContents.send('playlists-updated', updatedPlaylists);
        }
    });

    syncServer.onPlaybackControlCallback = (action, value) => {
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.webContents.send('remote-playback-control', { action, value });
        }
    };

    try {
        await syncServer.start();
    } catch (e) {
        console.error('Could not start sync server:', e);
    }

    createWindow();

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        if (syncServer) syncServer.stop();
        app.quit();
    }
});

// IPC handlers
ipcMain.handle('get-sync-info', () => {
    return {
        port: syncServer ? syncServer.port : 5253,
        ipList: syncServer ? syncServer.getLocalIpAddresses() : ['127.0.0.1'],
        device: require('os').hostname()
    };
});

ipcMain.handle('get-playlists', () => {
    return syncServer ? syncServer.playlists : [];
});

ipcMain.handle('save-playlist', (event, playlist) => {
    if (!syncServer) return false;
    const index = syncServer.playlists.findIndex(p => p.id === playlist.id);
    if (index >= 0) {
        syncServer.playlists[index] = playlist;
    } else {
        syncServer.playlists.push(playlist);
    }
    syncServer.savePlaylists();
    return true;
});

ipcMain.handle('delete-playlist', (event, playlistId) => {
    if (!syncServer) return false;
    syncServer.playlists = syncServer.playlists.filter(p => p.id !== playlistId);
    syncServer.savePlaylists();
    return true;
});

ipcMain.on('minimize-window', () => {
    if (mainWindow) mainWindow.minimize();
});

ipcMain.on('maximize-window', () => {
    if (mainWindow) {
        if (mainWindow.isMaximized()) {
            mainWindow.unmaximize();
        } else {
            mainWindow.maximize();
        }
    }
});

ipcMain.on('close-window', () => {
    if (mainWindow) mainWindow.close();
});
