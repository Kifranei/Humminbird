# Changelog

## 1.0.0

### 中文

Humminbird 的第一个版本，由 Halcyon 1.3.0 精简而来。版本号 `1.0.0`，`versionCode` 为 `1`。

保留本地曲库、标签、歌单、歌词、Lyrico 歌词插件、均衡器、播放器可视化频谱、桌面歌词、状态栏歌词和本地 MV。WebDAV、Navidrome、Emby、Last.fm 和局域网网页播放器仍在。悬浮导航保留高斯模糊，卡片保留流光透过的磨砂效果。界面提供简体中文、繁体中文、英文和日文。安装包只包含 `arm64-v8a` 和 `armeabi-v7a`。

去掉在线源，评论查看。同时去掉 AI 功能、液态玻璃、USB 独占、MCP 服务、M3U8 下载器、内置 MiSans 与 Inter 字体、OriginOS 原子随身听白名单、内置频谱查看、格式转换、多音频流导出、CUE 整轨分轨，以及 FFmpegKit。本地播放仍使用单独的 FFmpeg 解码库。未选择字体时回落到系统字体。查看频谱可以交给外部应用。

### English

First Humminbird release, trimmed from Halcyon 1.3.0. Version `1.0.0`, `versionCode` `1`.

Local library, tags, playlists, lyrics, Lyrico lyric plugins, the equalizer, the in-player visualizer, desktop lyrics, status-bar lyrics, and local music videos stay. WebDAV, Navidrome, Emby, Last.fm, and the LAN web player stay. The floating navigation keeps Gaussian blur, and cards keep the frosted flowing-light background. The app ships Simplified Chinese, Traditional Chinese, English, and Japanese. Packages include only `arm64-v8a` and `armeabi-v7a`.

Removed: online sources, and comment viewing. Also removed: AI features, liquid glass, USB exclusive mode, the MCP server, the M3U8 downloader, the bundled MiSans and Inter fonts, the OriginOS Atom Walkman allowlist, the built-in spectrum viewer, format conversion, multi-stream export, CUE splitting, and FFmpegKit. Local playback still uses a separate FFmpeg decoder. An empty font choice falls back to the system font. Spectrum viewing can still be handed to an external app.
