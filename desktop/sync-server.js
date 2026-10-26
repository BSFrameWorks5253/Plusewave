const express = require('express');
const cors = require('cors');
const os = require('os');
const fs = require('fs');
const path = require('path');

class SyncServer {
    constructor(port = 5253, dataFilePath = null, onSyncCallback = null) {
        this.port = port;
        this.dataFilePath = dataFilePath || path.join(__dirname, 'playlists_store.json');
        this.onSyncCallback = onSyncCallback;
        this.app = express();
        this.server = null;
        this.playlists = this.loadPlaylists();
        this.connectedClients = [];

        this.initServer();
    }

    getLocalIpAddresses() {
        const interfaces = os.networkInterfaces();
        const ips = [];
        for (const name of Object.keys(interfaces)) {
            for (const iface of interfaces[name]) {
                if (iface.family === 'IPv4' && !iface.internal) {
                    ips.push(iface.address);
                }
            }
        }
        return ips.length > 0 ? ips : ['127.0.0.1'];
    }

    loadPlaylists() {
        try {
            if (fs.existsSync(this.dataFilePath)) {
                const data = fs.readFileSync(this.dataFilePath, 'utf-8');
                return JSON.parse(data);
            }
        } catch (e) {
            console.error('Failed to load playlists file:', e);
        }
        // Default initial playlist if empty
        return [
            {
                id: 'pulse_favorites',
                name: 'PulseWave Favorites',
                author: 'You',
                thumbnailUrl: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500&q=80',
                songs: [
                    {
                        id: 's_1',
                        title: 'Starboy',
                        artistsText: 'The Weeknd, Daft Punk',
                        album: 'Starboy',
                        duration: 230,
                        thumbnailUrl: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=300&q=80'
                    },
                    {
                        id: 's_2',
                        title: 'Blinding Lights',
                        artistsText: 'The Weeknd',
                        album: 'After Hours',
                        duration: 200,
                        thumbnailUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&q=80'
                    },
                    {
                        id: 's_3',
                        title: 'Midnight City',
                        artistsText: 'M83',
                        album: 'Hurry Up, We\'re Dreaming',
                        duration: 243,
                        thumbnailUrl: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=300&q=80'
                    }
                ]
            }
        ];
    }

    savePlaylists() {
        try {
            fs.writeFileSync(this.dataFilePath, JSON.stringify(this.playlists, null, 2), 'utf-8');
        } catch (e) {
            console.error('Failed to save playlists:', e);
        }
    }

    initServer() {
        this.app.use(cors());
        this.app.use(express.json({ limit: '50mb' }));

        // Health & Status endpoint for local Wi-Fi discovery
        this.app.get('/api/status', (req, res) => {
            res.json({
                app: 'PulseWave Desktop',
                version: '6.1.5',
                device: os.hostname(),
                ipList: this.getLocalIpAddresses(),
                port: this.port,
                playlistCount: this.playlists.length,
                totalSongs: this.playlists.reduce((acc, p) => acc + (p.songs ? p.songs.length : 0), 0),
                syncReady: true,
                timestamp: Date.now()
            });
        });

        // Get all playlists
        this.app.get('/api/playlists', (req, res) => {
            res.json({
                success: true,
                playlists: this.playlists
            });
        });

        // Add or update single playlist
        this.app.post('/api/playlists', (req, res) => {
            const playlist = req.body;
            if (!playlist || !playlist.name) {
                return res.status(400).json({ success: false, error: 'Playlist name required' });
            }
            if (!playlist.id) {
                playlist.id = 'pl_' + Date.now();
            }
            const existingIndex = this.playlists.findIndex(p => p.id === playlist.id || p.name === playlist.name);
            if (existingIndex >= 0) {
                this.playlists[existingIndex] = playlist;
            } else {
                this.playlists.push(playlist);
            }
            this.savePlaylists();
            if (this.onSyncCallback) this.onSyncCallback(this.playlists);
            res.json({ success: true, playlist });
        });

        // Full Bidirectional Sync endpoint with Mobile App
        this.app.post('/api/sync', (req, res) => {
            const { sourceDevice, incomingPlaylists } = req.body;
            console.log(`[PulseWave Sync] Received sync request from ${sourceDevice || 'Android Phone'}`);

            if (Array.isArray(incomingPlaylists)) {
                // Merge incoming playlists with existing desktop playlists
                for (const incoming of incomingPlaylists) {
                    if (!incoming.name) continue;
                    const matchIndex = this.playlists.findIndex(
                        p => (p.id && incoming.id && p.id === incoming.id) || p.name.trim().toLowerCase() === incoming.name.trim().toLowerCase()
                    );
                    if (matchIndex >= 0) {
                        // Merge songs avoiding duplicate IDs
                        const existingSongs = this.playlists[matchIndex].songs || [];
                        const incomingSongs = incoming.songs || [];
                        const seenIds = new Set(existingSongs.map(s => s.id));
                        for (const s of incomingSongs) {
                            if (!seenIds.has(s.id)) {
                                existingSongs.push(s);
                                seenIds.add(s.id);
                            }
                        }
                        this.playlists[matchIndex].songs = existingSongs;
                        if (incoming.thumbnailUrl) {
                            this.playlists[matchIndex].thumbnailUrl = incoming.thumbnailUrl;
                        }
                    } else {
                        this.playlists.push({
                            id: incoming.id || ('phone_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6)),
                            name: incoming.name,
                            author: incoming.author || sourceDevice || 'Phone Sync',
                            thumbnailUrl: incoming.thumbnailUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
                            songs: incoming.songs || []
                        });
                    }
                }
                this.savePlaylists();
                if (this.onSyncCallback) this.onSyncCallback(this.playlists);
            }

            // Return merged state back to the mobile phone
            res.json({
                success: true,
                message: 'Playlists successfully synchronized over Wi-Fi',
                desktopDevice: os.hostname(),
                playlists: this.playlists,
                timestamp: Date.now()
            });
        });

        // Pulse Connect — Remote Desktop Playback Control
        this.app.get('/api/playback/status', (req, res) => {
            res.json({
                success: true,
                device: os.hostname(),
                playbackState: this.currentPlaybackState || { isPlaying: false, currentSong: null, volume: 1.0 }
            });
        });

        this.app.post('/api/playback/control', (req, res) => {
            const { action, value } = req.body || {};
            console.log(`[Pulse Connect] Remote playback command received: ${action}`, value || '');
            if (this.onPlaybackControlCallback) {
                this.onPlaybackControlCallback(action, value);
            }
            res.json({ success: true, action, message: `Command ${action} received` });
        });
    }

    start() {
        return new Promise((resolve, reject) => {
            this.server = this.app.listen(this.port, '0.0.0.0', () => {
                const ips = this.getLocalIpAddresses();
                console.log(`[PulseWave Sync Server] Running on port ${this.port}`);
                console.log(`[PulseWave Sync Server] Available on LAN addresses:`);
                ips.forEach(ip => console.log(`   -> http://${ip}:${this.port}`));
                resolve(ips);
            });
            this.server.on('error', (err) => {
                console.error('[PulseWave Sync Server] Error:', err);
                reject(err);
            });
        });
    }

    stop() {
        if (this.server) {
            this.server.close();
            this.server = null;
        }
    }
}

module.exports = SyncServer;

if (require.main === module) {
    const server = new SyncServer(5253);
    server.start().then(() => {
        console.log('[PulseWave Sync Server] Standalone service active.');
    });
}
