# Humminbird

[中文](README.md)

Humminbird is a lighter local music player based on [Halcyon](https://github.com/Kifranei/Halcyon), for Android 10 and later. The install package id is `com.kifranei.humminbird.player`.

Project page: <https://github.com/Kifranei/Humminbird>

## What it does

- Scan and play a local library, with playlists, folders, and tags.
- Play local music videos. A local video file is preferred.
- Local and embedded lyrics, plus Lyrico lyric plugins. Desktop lyrics and status-bar lyrics are included.
- Equalizer and the in-player visualizer. Spectrum viewing can be handed to an external app chosen in Settings.
- WebDAV, SMB 2/3, FTP / explicit FTPS, SFTP, Navidrome, and Emby, plus Last.fm. Manage file-protocol libraries from library settings, scan shared folders, and browse, search, and continuously play songs, albums, artists, and folders.
- A LAN web player.
- Gaussian-blur floating navigation, and frosted cards that let the flowing-light background show through.
- Languages: Simplified Chinese, Traditional Chinese, English, and Japanese. An empty font choice uses the system font.


## Requirements

- Android 10 (API 29) or later.
- ABIs: `arm64-v8a` and `armeabi-v7a`.

## Build

JDK 21 is required.

```bash
./gradlew :app:assembleRelease
```

Release packages are written to `app/build/outputs/renamed-apk/release/` and start with `Humminbird-`. One build produces arm64-v8a, armeabi-v7a, and a universal package.

If `release.jks` is missing, the release build is signed with the local debug key. That package cannot update an install signed with a real release certificate.

## Docs

- [Changelog](CHANGELOG.md)
- [Apache-2.0 license](LICENSE)
- [Third-party licenses](THIRD_PARTY_LICENSES.md)
