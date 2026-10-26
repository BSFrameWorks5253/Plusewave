// PulseWave Desktop Renderer Script
let allPlaylists = [];
let currentPlaylist = null;
let currentTrack = null;
let isPlaying = false;
let currentProgress = 0;
let progressInterval = null;
let desktopIps = ['127.0.0.1'];
let syncPort = 5253;

// DOM Elements
const playlistList = document.getElementById('playlist-list');
const heroImg = document.getElementById('hero-img');
const heroTitle = document.getElementById('hero-title');
const heroDesc = document.getElementById('hero-desc');
const heroSongCount = document.getElementById('hero-song-count');
const heroDuration = document.getElementById('hero-duration');
const tracklistBody = document.getElementById('tracklist-body');
const btnPlayAll = document.getElementById('btn-play-all');
const heroPlayIcon = document.getElementById('hero-play-icon');

// Player Elements
const currentTrackImg = document.getElementById('current-track-img');
const currentTrackTitle = document.getElementById('current-track-title');
const currentTrackArtist = document.getElementById('current-track-artist');
const btnPlayPause = document.getElementById('btn-play-pause');
const ctrlPlayIcon = document.getElementById('ctrl-play-icon');
const progressFill = document.getElementById('progress-fill');
const timeCurrent = document.getElementById('time-current');
const timeTotal = document.getElementById('time-total');
const progressWrap = document.getElementById('progress-wrap');

// Modals
const syncModal = document.getElementById('sync-modal');
const createModal = document.getElementById('create-modal');
const btnOpenSync = document.getElementById('btn-open-sync');
const syncPill = document.getElementById('sync-pill');
const btnCloseSyncModal = document.getElementById('btn-close-sync-modal');
const btnCloseSyncModalBottom = document.getElementById('btn-close-sync-modal-bottom');
const modalIpAddress = document.getElementById('modal-ip-address');
const btnCopyIp = document.getElementById('btn-copy-ip');
const syncStatusText = document.getElementById('sync-status-text');

const btnCreatePlaylist = document.getElementById('btn-create-playlist');
const btnImportSpotify = document.getElementById('btn-import-spotify');
const btnCloseCreateModal = document.getElementById('btn-close-create-modal');
const btnCancelCreate = document.getElementById('btn-cancel-create');
const btnSaveNewPlaylist = document.getElementById('btn-save-new-playlist');
const inputPlaylistName = document.getElementById('input-playlist-name');
const inputSpotifyLink = document.getElementById('input-spotify-link');
const inputCoverUrl = document.getElementById('input-cover-url');

const btnSyncCurrentPlaylist = document.getElementById('btn-sync-current-playlist');
const btnDeletePlaylist = document.getElementById('btn-delete-playlist');
const searchInput = document.getElementById('search-input');

// Initialize
async function init() {
    setupEventListeners();
    await loadSyncInfo();
    await loadPlaylists();

    if (window.pulseApi) {
        window.pulseApi.onPlaylistsUpdated((updated) => {
            console.log('[Desktop] Live sync update received from phone:', updated);
            allPlaylists = updated;
            renderPlaylistSidebar();
            if (currentPlaylist) {
                const updatedCurrent = allPlaylists.find(p => p.id === currentPlaylist.id);
                if (updatedCurrent) {
                    selectPlaylist(updatedCurrent);
                } else if (allPlaylists.length > 0) {
                    selectPlaylist(allPlaylists[0]);
                }
            }
            showNotification('⚡ Playlists Synced with Android Phone!');
        });
    }
}

async function loadSyncInfo() {
    if (window.pulseApi) {
        const info = await window.pulseApi.getSyncInfo();
        desktopIps = info.ipList || ['127.0.0.1'];
        syncPort = info.port || 5253;
        const primaryIp = desktopIps[0];
        modalIpAddress.textContent = `${primaryIp}:${syncPort}`;
        syncStatusText.textContent = `Wi-Fi Sync: ${primaryIp}:${syncPort}`;
        document.getElementById('footer-wifi-port').textContent = `${primaryIp}:${syncPort}`;
    }
}

async function loadPlaylists() {
    if (window.pulseApi) {
        allPlaylists = await window.pulseApi.getPlaylists();
    } else {
        allPlaylists = [
            {
                id: 'pulse_favorites',
                name: 'PulseWave Favorites',
                author: 'You',
                thumbnailUrl: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500&q=80',
                songs: [
                    { id: '1', title: 'Starboy', artistsText: 'The Weeknd', album: 'Starboy', duration: 230, thumbnailUrl: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=100&q=80' },
                    { id: '2', title: 'Blinding Lights', artistsText: 'The Weeknd', album: 'After Hours', duration: 200, thumbnailUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=100&q=80' }
                ]
            }
        ];
    }
    renderPlaylistSidebar();
    if (allPlaylists.length > 0) {
        selectPlaylist(allPlaylists[0]);
    }
}

function renderPlaylistSidebar() {
    playlistList.innerHTML = '';
    allPlaylists.forEach(playlist => {
        const card = document.createElement('div');
        card.className = `playlist-card-row ${currentPlaylist && currentPlaylist.id === playlist.id ? 'active' : ''}`;
        card.innerHTML = `
            <img class="playlist-thumb" src="${playlist.thumbnailUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&q=80'}" alt="">
            <div class="playlist-card-text">
                <div class="playlist-card-name">${escapeHtml(playlist.name)}</div>
                <div class="playlist-card-meta">
                    <span>Playlist</span>
                    <span>•</span>
                    <span>${playlist.songs ? playlist.songs.length : 0} songs</span>
                </div>
            </div>
        `;
        card.addEventListener('click', () => selectPlaylist(playlist));
        playlistList.appendChild(card);
    });
}

function selectPlaylist(playlist) {
    currentPlaylist = playlist;
    renderPlaylistSidebar();

    heroTitle.textContent = playlist.name;
    heroDesc.textContent = playlist.author ? `Curated by ${playlist.author}. Kept in sync with PulseWave Mobile.` : 'Synced via local Wi-Fi.';
    heroImg.src = playlist.thumbnailUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80';

    const songs = playlist.songs || [];
    heroSongCount.textContent = `${songs.length} song${songs.length === 1 ? '' : 's'}`;
    const totalSec = songs.reduce((sum, s) => sum + (s.duration || 180), 0);
    heroDuration.textContent = formatDuration(totalSec);

    renderTracklist(songs);
}

function renderTracklist(songs) {
    tracklistBody.innerHTML = '';
    if (songs.length === 0) {
        tracklistBody.innerHTML = `
            <tr>
                <td colspan="6" style="text-align: center; padding: 40px; color: var(--text-muted);">
                    No songs in this playlist yet. Add songs or sync with your phone!
                </td>
            </tr>
        `;
        return;
    }

    songs.forEach((song, idx) => {
        const row = document.createElement('tr');
        row.className = 'track-row';
        row.innerHTML = `
            <td class="col-num">${idx + 1}</td>
            <td class="col-title">
                <div class="col-title-wrap">
                    <img class="track-thumb" src="${song.thumbnailUrl || 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=100&q=80'}" alt="">
                    <div>
                        <div class="track-title-text">${escapeHtml(song.title || 'Untitled Track')}</div>
                        <div class="track-artist-text">${escapeHtml(song.artistsText || 'Unknown Artist')}</div>
                    </div>
                </div>
            </td>
            <td class="col-album">${escapeHtml(song.album || 'Single')}</td>
            <td class="col-date">Recently</td>
            <td class="col-duration">${formatTime(song.duration || 210)}</td>
            <td class="col-actions">
                <button class="action-icon-btn btn-row-play" title="Play Track"><i class="fa-solid fa-play"></i></button>
            </td>
        `;
        row.addEventListener('click', () => playTrack(song));
        tracklistBody.appendChild(row);
    });
}

function playTrack(song) {
    currentTrack = song;
    currentTrackTitle.textContent = song.title;
    currentTrackArtist.textContent = song.artistsText || 'PulseWave Player';
    currentTrackImg.src = song.thumbnailUrl || 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=100&q=80';
    timeTotal.textContent = formatTime(song.duration || 210);

    isPlaying = true;
    updatePlayIcons(true);
    startProgressTimer(song.duration || 210);
}

function togglePlayPause() {
    if (!currentTrack) {
        if (currentPlaylist && currentPlaylist.songs && currentPlaylist.songs.length > 0) {
            playTrack(currentPlaylist.songs[0]);
            return;
        }
    }
    isPlaying = !isPlaying;
    updatePlayIcons(isPlaying);
    if (isPlaying) {
        startProgressTimer(currentTrack ? currentTrack.duration || 210 : 210);
    } else {
        clearInterval(progressInterval);
    }
}

function updatePlayIcons(playing) {
    ctrlPlayIcon.className = playing ? 'fa-solid fa-pause' : 'fa-solid fa-play';
    heroPlayIcon.className = playing ? 'fa-solid fa-pause' : 'fa-solid fa-play';
}

function startProgressTimer(duration) {
    clearInterval(progressInterval);
    progressInterval = setInterval(() => {
        if (!isPlaying) return;
        currentProgress += 1;
        if (currentProgress > duration) {
            currentProgress = 0;
            // Next track
            playNextTrack();
            return;
        }
        timeCurrent.textContent = formatTime(currentProgress);
        const percent = (currentProgress / duration) * 100;
        progressFill.style.width = `${percent}%`;
    }, 1000);
}

function playNextTrack() {
    if (!currentPlaylist || !currentPlaylist.songs || currentPlaylist.songs.length === 0) return;
    const currentIndex = currentPlaylist.songs.findIndex(s => s.id === (currentTrack ? currentTrack.id : null));
    const nextIndex = (currentIndex + 1) % currentPlaylist.songs.length;
    currentProgress = 0;
    playTrack(currentPlaylist.songs[nextIndex]);
}

function playPrevTrack() {
    if (!currentPlaylist || !currentPlaylist.songs || currentPlaylist.songs.length === 0) return;
    const currentIndex = currentPlaylist.songs.findIndex(s => s.id === (currentTrack ? currentTrack.id : null));
    const prevIndex = (currentIndex - 1 + currentPlaylist.songs.length) % currentPlaylist.songs.length;
    currentProgress = 0;
    playTrack(currentPlaylist.songs[prevIndex]);
}

function setupEventListeners() {
    btnPlayPause.addEventListener('click', togglePlayPause);
    btnPlayAll.addEventListener('click', togglePlayPause);
    document.getElementById('btn-next').addEventListener('click', playNextTrack);
    document.getElementById('btn-prev').addEventListener('click', playPrevTrack);

    // Sync Modal open/close
    const openSync = () => {
        loadSyncInfo();
        syncModal.classList.add('open');
    };
    btnOpenSync.addEventListener('click', openSync);
    syncPill.addEventListener('click', openSync);
    btnSyncCurrentPlaylist.addEventListener('click', openSync);
    btnCloseSyncModal.addEventListener('click', () => syncModal.classList.remove('open'));
    btnCloseSyncModalBottom.addEventListener('click', () => syncModal.classList.remove('open'));

    // Copy IP button
    btnCopyIp.addEventListener('click', () => {
        navigator.clipboard.writeText(modalIpAddress.textContent);
        btnCopyIp.textContent = 'Copied!';
        setTimeout(() => { btnCopyIp.innerHTML = '<i class="fa-regular fa-copy"></i> Copy'; }, 2000);
    });

    // Create Modal
    btnCreatePlaylist.addEventListener('click', () => {
        inputPlaylistName.value = '';
        inputSpotifyLink.value = '';
        inputCoverUrl.value = '';
        createModal.classList.add('open');
    });
    btnImportSpotify.addEventListener('click', () => {
        inputPlaylistName.value = '';
        inputSpotifyLink.value = '';
        inputCoverUrl.value = '';
        createModal.classList.add('open');
    });
    btnCloseCreateModal.addEventListener('click', () => createModal.classList.remove('open'));
    btnCancelCreate.addEventListener('click', () => createModal.classList.remove('open'));

    btnSaveNewPlaylist.addEventListener('click', async () => {
        const name = inputPlaylistName.value.trim() || 'Imported Spotify Playlist';
        const cover = inputCoverUrl.value.trim() || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80';
        
        const newPl = {
            id: 'desktop_' + Date.now(),
            name: name,
            author: 'PulseWave Desktop',
            thumbnailUrl: cover,
            songs: [
                {
                    id: 'sample_' + Date.now(),
                    title: 'Imported Track #1',
                    artistsText: 'PulseWave Artist',
                    album: name,
                    duration: 210,
                    thumbnailUrl: cover
                }
            ]
        };

        if (window.pulseApi) {
            await window.pulseApi.savePlaylist(newPl);
        }
        allPlaylists.push(newPl);
        createModal.classList.remove('open');
        selectPlaylist(newPl);
        showNotification('✨ Playlist Created & Ready for Wi-Fi Sync!');
    });

    // Delete Playlist
    btnDeletePlaylist.addEventListener('click', async () => {
        if (!currentPlaylist) return;
        if (confirm(`Are you sure you want to delete "${currentPlaylist.name}"?`)) {
            if (window.pulseApi) {
                await window.pulseApi.deletePlaylist(currentPlaylist.id);
            }
            allPlaylists = allPlaylists.filter(p => p.id !== currentPlaylist.id);
            if (allPlaylists.length > 0) {
                selectPlaylist(allPlaylists[0]);
            } else {
                renderPlaylistSidebar();
                heroTitle.textContent = 'No Playlists';
                renderTracklist([]);
            }
        }
    });

    // Scrubber click
    progressWrap.addEventListener('click', (e) => {
        const rect = progressWrap.getBoundingClientRect();
        const clickX = e.clientX - rect.left;
        const width = rect.width;
        const percent = clickX / width;
        const duration = currentTrack ? currentTrack.duration || 210 : 210;
        currentProgress = Math.floor(percent * duration);
        timeCurrent.textContent = formatTime(currentProgress);
        progressFill.style.width = `${percent * 100}%`;
    });

    // Search filter
    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase().trim();
        if (!currentPlaylist || !currentPlaylist.songs) return;
        if (!query) {
            renderTracklist(currentPlaylist.songs);
            return;
        }
        const filtered = currentPlaylist.songs.filter(s => 
            (s.title && s.title.toLowerCase().includes(query)) ||
            (s.artistsText && s.artistsText.toLowerCase().includes(query)) ||
            (s.album && s.album.toLowerCase().includes(query))
        );
        renderTracklist(filtered);
    });
}

function showNotification(msg) {
    const toast = document.createElement('div');
    toast.style.cssText = `
        position: fixed;
        bottom: 100px;
        right: 24px;
        background: rgba(139, 92, 246, 0.95);
        color: #FFF;
        padding: 12px 20px;
        border-radius: 24px;
        font-size: 13px;
        font-weight: 600;
        box-shadow: 0 10px 30px rgba(0,0,0,0.5);
        backdrop-filter: blur(10px);
        z-index: 9999;
        transition: opacity 0.3s;
    `;
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

function formatDuration(totalSeconds) {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins} min ${secs} sec`;
}

function escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

document.addEventListener('DOMContentLoaded', init);
