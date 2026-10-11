# Third-Party Licenses

Humminbird 主工程以 Apache-2.0 发布。下面列出的第三方组件各自保留原许可。本文件按当前安装包里仍在使用或仍被改编的部分整理，方便随后再改。

The Humminbird application is licensed under Apache-2.0. Third-party components keep their own licenses. This list matches what the current package still links or adapts.

| Project | Purpose | License | Link | Notes |
| :-- | :-- | :-- | :-- | :-- |
| CatClawMusic.Plugins.Netease | Catalog client, EAPI encryption, and related metadata used by the remaining adapted code | MIT | https://github.com/kankejiang/CatClawMusic.Plugins.Netease | Copyright (c) 2026 kankejiang. Kotlin adaptation of NeteaseEapi.cs and NetEaseOpenApiClient.cs at dbb4db84904cd339fbebaf5a43f6b87e5818d17d. Full notice: `app/src/main/assets/licenses/CatClawMusic-Netease-MIT.txt`. No MAUI/.ccp runtime is bundled. |
| CatClawMusic | Host and browser-session integration reference | MIT | https://github.com/kankejiang/CatClawMusic | Copyright (c) 2024–2026 kankejiang. Reference revision 9dc3e08d9699a69dd20a806d7c3edc8ab6b10da5. Full notice: `app/src/main/assets/licenses/CatClawMusic-MIT.txt`. |
| Miuix | Compose UI, icons, blur, and preferences | Apache-2.0 | https://github.com/compose-miuix-ui/miuix | Artifacts aligned at 0.9.4. |
| AndroidX / Jetpack Compose | Android libraries, Compose, lifecycle, navigation, and DataStore | Apache-2.0 | https://developer.android.com/jetpack/androidx | UI and platform integration. |
| AndroidX Media3 | Playback, media session, and ExoPlayer | Apache-2.0 | https://github.com/androidx/media | `androidx.media3:*` aligned at 1.11.1. |
| Kotlin / Kotlinx Coroutines | Language runtime and coroutines | Apache-2.0 | https://github.com/JetBrains/kotlin | Kotlin 2.4.20, kotlinx-coroutines Android 1.11.0. |
| Coil | Image loading | Apache-2.0 | https://github.com/coil-kt/coil | `coil-compose` and `coil-network-okhttp` 3.6.3. |
| OkHttp | HTTP client | Apache-2.0 | https://github.com/lysine-dev/okhttp | 5.5.0. Maven coordinate remains `com.squareup.okhttp3:okhttp`. |
| Lyricon | Status-bar lyric provider API | Apache-2.0 | https://github.com/proify/lyricon | `io.github.proify.lyricon:provider`. |
| LyricGetter-API | Lyric text handoff | LGPL-2.1 | https://github.com/xiaowine/Lyric-Getter-Api | `com.github.HChenX:Lyric-Getter-Api`. |
| SuperLyricApi | Lyric publishing API | LGPL-2.1 | https://github.com/HChenX/SuperLyricApi | `com.github.HChenX:SuperLyricApi`. |
| lyrico-audiotag / Lyrico | Local audio tag reading and writing | Apache-2.0 | https://github.com/Replica0110/Lyrico | Local module. JNI fixes selectively follow v1.6.0, commit `ad9c83f5e78561edf60cfae762556583f359a395`. |
| TagLib | Native tag read/write used by lyrico-audiotag | LGPL-2.1-or-later / MPL-1.1 | https://taglib.org/ | Vendored 2.3.2, commit `deadc2990767dfbda0701e0ab35fdeea653db08f`, shipped in `liblyrico_taglib.so`. License texts stay in the vendor tree. |
| utfcpp | UTF-8 conversion for TagLib | BSL-1.0 | https://github.com/nemtrif/utfcpp | Vendored 4.1.1. License: `lyrico-audiotag/src/main/cpp/taglib/3rdparty/utfcpp/LICENSE`. |
| FFmpeg | Software decoding for local playback | LGPL-2.1-or-later | https://ffmpeg.org | Decoder sources are pinned to 9.0.2. GPL, nonfree, and version3 stay disabled. FFmpegKit is not bundled. |
| quickjs-wrapper Android | JavaScript runtime for lyric plugins | Apache-2.0 | https://github.com/HarlonWang/quickjs-wrapper | `wang.harlon.quickjs:wrapper-android:3.2.3`. |
| LX Music Mobile | Lyric-source API compatibility reference | Apache-2.0 | https://github.com/lyswhut/lx-music-mobile | Protocol reference follows v1.9.1, commit `fb8480728d875fa5e0da25eebd3a26bb71723aae`. Application binaries are not bundled. |
| MusicFree | Plugin-protocol reference for lyric plugins | AGPL-3.0 (upstream application) | https://github.com/maotoumao/MusicFree | Reference v0.6.2, commit `b3cb5e5829dc600af86731c582429f5eb99cad27`. Humminbird ships its own adapter. MusicFree binaries and user plugins are not bundled. Imported plugins keep their own licenses. |
| Oboe | Native audio output | Apache-2.0 | https://github.com/google/oboe | `com.google.oboe:oboe:1.11.0`. |
| Ktor | LAN web player HTTP server | Apache-2.0 | https://github.com/ktorio/ktor | CIO, content negotiation, and serialization aligned at 3.6.0. |
| kotlinx-serialization | JSON serialization | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization | `kotlinx-serialization-json:1.11.0`. |
| Shizuku API | Optional privileged user-service bridge | MIT | https://github.com/RikkaApps/Shizuku-API | Client API 13.1.5. This is not the separate Shizuku app. |
| AndroidHiddenApiBypass | Hidden API access helper | Apache-2.0 | https://github.com/LSPosed/AndroidHiddenApiBypass | `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`. |
| HONOR media-audio SDK | Optional high-sample-rate playback | Proprietary vendor SDK | https://developer.honor.com/repo/com/hihonor/mcs/media-audio/maven-metadata.xml | `com.hihonor.mcs:media-audio:1.2.0.301`. The vendor POM does not declare an open-source license. |
| ColorOS Live Lyrics Bridge | OPlus lyric-bridge payload reference | Apache-2.0 | https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge | Payload adaptation targets v4.4.0, commit `44d5d347623e93a008bf188ae96c060ee4791e98`. Module binaries are not bundled. |
| HyperNotification | HyperOS Focus notification API | Apache-2.0 | https://github.com/xzakota/HyperNotification | `com.xzakota.hyper.notification:focus-api:1.4`. |
| LySy | Lyric timing reference | MIT | https://github.com/pxeemo/LySy | Timing-editor reference. LySy Web is not bundled. |
| Beautiful Lyrics | Lyric visual reference | Not stated | https://github.com/surfbryce/beautiful-lyrics | No standalone license was found upstream. |
| BetterLyrics | Centered spectrum visual reference | GPL-3.0 | https://github.com/jayfunc/BetterLyrics | Consulted for layout only. `CenteredSpectrum.kt` is an independent implementation. BetterLyrics binaries are not bundled. |
| 163KeyDecrypter | 163 key decoding reference | MIT | https://github.com/lycode404/163KeyDecrypter | Decoding reference. The upstream application is not bundled. |
| Reorderable | Drag-to-reorder lists | Apache-2.0 | https://github.com/Calvin-LL/Reorderable | `sh.calvin.reorderable:reorderable`. |
| RawS Music | DSP, waveform, and mirrored-spectrum references | Apache-2.0 | https://github.com/QFDY-GZC/RawS-Music | Kotlin ports in `com.ella.music.dsp`, plus waveform and mirrored-spectrum presentation. Copyright 2024–2026 RawSMusic Contributors. The USB-exclusive native core, BRIR renderer, and FFT convolver are not included. |
| ConePlayer | Visualizer motion reference | Not stated | https://coneplayer.trantor.ink/ | Motion models were reconstructed for Compose. No ConePlayer binaries are bundled. |
| Material Symbols | A few UI vectors, including shuffle and speed | Apache-2.0 | https://fonts.google.com/icons | Outlined 24dp vectors. |

LGPL and MPL components above stay under those licenses. They do not relicense the Apache-2.0 application code.

FFmpegKit, the bundled MiSans and Inter fonts, and the MCP Kotlin SDK are not part of this package. Redistribution of FFmpeg, TagLib, LyricGetter-API, and SuperLyricApi must follow their upstream terms. If a native build or a bundled binary changes, update this file before release.


### Apache Commons Net 3.13.0

FTP / explicit FTPS client. Apache License 2.0: https://commons.apache.org/proper/commons-net/

### JSch (mwiede) 2.28.7

SFTP client. BSD-style license: https://github.com/mwiede/jsch/blob/master/LICENSE.txt

### Bouncy Castle 1.86

Cryptographic primitives shared by SMB and SFTP, including the modern SSH key exchanges. MIT-style license: https://www.bouncycastle.org/licence.html

## 2026-10-11 navigation provenance and redistribution materials

The liquid navigation implementation was reimported from the Apache-2.0 Miuix v0.9.4 examples, commit `39c40f99844227b853f0049a0933b1f3ae6c00ba`, credited to compose-miuix-ui and Kyant0/AndroidLiquidGlass. The former GPL gesture inspector and KernelSU/InstallerX-derived animation and navigation sources are no longer compiled. See [migration and distribution evidence](docs/license-compliance-2026-10-11.md) and [source hashes](docs/licenses/miuix-example-provenance.json). Remaining font and vendor-SDK authorization gaps are recorded explicitly.
