# PulseWave Desktop 🎵

**PulseWave Desktop** is a desktop music client matching the Spotify interface with a local Wi-Fi synchronization engine to sync playlists between your PC and the PulseWave Android app over the same internet/Wi-Fi network.

---

## ✨ Features
- **Spotify-Grade Interface**: Left sidebar (Library & Playlists), center tracklist with hero header, and bottom playback bar with scrubber, play/pause/skip controls, and volume.
- **Local Wi-Fi Playlist Sync**: Automatic peer discovery on local network port `5253`.
- **Bidirectional Sync**:
  - Pulls playlists from Android into Desktop.
  - Pushes Desktop playlists and Spotify imports into Android Room DB.
  - No cloud servers needed — direct LAN transfer.
- **Spotify Playlist Import**: Paste any Spotify playlist URL directly into Desktop to import and sync to your phone.

---

## 🚀 How to Run PulseWave Desktop

### 1. Start the App
```bash
cd desktop
npm start
```

### 2. Connect from Android Phone
1. Connect your Android phone to the **same Wi-Fi network** as this PC.
2. Open **PulseWave** on Android.
3. Open **Settings** &rarr; **Desktop Wi-Fi Sync**.
4. The Desktop IP (e.g. `192.168.29.215:5253`) is shown at the top of the Desktop app.
5. Tap **"Sync Now"** on your phone.
6. All playlists, liked songs, and tracks are instantly synchronized!
