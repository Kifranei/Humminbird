# Changelog

## 1.0.1

### 中文

- 立体歌词与边缘虚化改用单一图层的原生图像滤镜，移除递归录制歌词内容的多层渲染链，修复图层树递归导致的 RenderThread 栈溢出风险。
- 多选列表支持长按后连续拖选，覆盖歌曲、专辑、艺术家、分类、歌单、文件夹歌单、搜索和最近播放；支持边缘自动滚动与回拉收缩范围，保留此前选择与排序把手。
- 多选界面、歌词分享、备份类型和模块排序等选择面板增加反选；仅反转当前可选范围，保留其他范围的选择及数量上限。
- 绘光主歌词唱完后保持正常排版，装饰词片沿光场缓慢漂移并在有限时间内淡出，避免长期散落堆积在一侧。
- #716：恢复原有底栏交互，页面左右滑动完成后及时同步气泡位置，不再触发模拟按压放大；底栏拖动仍在松手后切换页面。
- 平板首页默认显示 8 个快捷按钮，功能块排除快捷区已有项目；保留手动配置。海报卡片修正标题分隔符，左右切歌复用已加载封面并避免旧播放状态覆盖新卡片。
- 艺术家传记增加名称匹配替换规则，可单独开关并指定数据源；按别名、原名、繁转简名称依次匹配，接入备份恢复。
- 液态底栏从 Miuix 0.9.4 的 Apache-2.0 示例重新适配，替换旧 GPL 动画与手势文件，保留外观设置与电视交互；增加来源与许可证检查。

- 海报墙点击封面只展开卡片，暂停时滑动切歌仍保持暂停；隐藏底部迷你播放条。堆叠布局缓存几何数据，弹窗与滑动使用稳定布局的图层动画，降低重复排版和解码开销。
- 凝彩、绘光、回环和商籁补齐逐字羽化扫光；凝彩黑白字按实际背景区域反转，回环保留英文词间空格，商籁保持歌词正常阅读角度，时计相邻歌词支持换行。
- 自定义分类条目使用卡片，删除按钮与箭头垂直对齐。
- SMB、WebDAV、FTP / FTPS 和 SFTP 音乐库扫描只建立目录索引，按需读取当前歌曲元数据；SMB / FTP / SFTP 播放流增加 256 KiB 有界缓冲以减少小块读取的网络往返，WebDAV 移除目录预取并沿用 HTTP 流缓冲，清理旧的大量元数据缓存。

- 歌词页补回立体歌词开关，并与播放页歌词显示菜单共用实时设置；角度也同步。补齐 AM 普通、沉浸、横屏和独立歌词页的立体效果；顶部信息区与底部控制区的边缘虚化固定在视口，立体旋转后仍有柔和过渡。

- 首页支持按任意歌曲标签配置自定义分类（如编曲家 / ARRANGER），接入显示与排序及 JSON / ZIP 备份恢复；流派、年份和自定义分类可批量写入对应标签，分隔符设置统一为可保存或取消的底部面板。

- 统一底栏返回规则：子页面逐级返回，其他标签主页回到首页，首页主页直接退出；搜索优先收起输入法，底栏搜索回到进入前的标签，其他搜索入口按页面栈返回。仅当前 Pager 页面接收返回，防止相邻隐藏页面拦截以及切换动画期间的旧页面覆盖返回目标。

由 Halcyon 同步保留功能的更新。版本号 `1.0.1`，`versionCode` 为 `2`。

- 新增 SMB 2/3、FTP / FTPS 和 SFTP 音乐库。WebDAV 和这些文件协议都可在音乐库设置中配置、扫描与刷新，支持递归扫描子目录、搜索、分类和连续播放。
- 音乐库缓存按共享地址和账号区分，支持离线读取已同步的曲库与缓存歌曲；WebDAV 目录页点歌使用同目录播放队列。
- SFTP 首次连接显示服务器密钥指纹，确认后才继续；密钥变化时停止连接。FTP 使用被动模式，支持显式 TLS 的 FTPS。
- 修复随机模式自动切歌时错误再跳过一首，以及迷你播放条封面落后一首。
- 修复 Release 混淆导致 SMB 连接与艺术家名称繁转简转换组件初始化失败；艺术家传记保留原名匹配与繁转简重试。
- 全局搜索和底栏新增文件夹歌单入口，「所有」页的文件夹歌单分类默认开启，兼容旧设置和备份。
- 同步主页面左右切换与状态保存、海报墙堆叠和遥控器焦点处理。堆叠封面错落排列并保留适当间隙；下层封面长按松手置顶，拖动支持组内插层和跨组移动，每组最多 10 张，保存分组与层序。播放保留原完整弹窗，后方叠放相邻弹窗，左右滑动切歌，移除上一曲和下一曲按钮。
- 音乐来源设置只显示当前来源的配置入口。频谱查看新增「每次询问」BottomSheet。
- 对照 Folia 重做凝彩、绘光、回环，加入商籁歌词舞台；保留全局字体和独立配色。
- 全局搜索记住进入前的底栏页面；远程曲库搜索、音质及红心筛选合并后台元数据更新，避免进度反复重置。
- 艺术家简介支持系统文字选择；标签信息选择显示对应分类封面和面板标题；歌曲输出面板与设置页对齐。
- 更新封面和本地 MV 导出目录，以及相关设置的备份与恢复。

保留四种界面语言、原包名、系统字体回落与现有精简范围。

### English

Sync retained-feature updates from Halcyon. Version `1.0.1`, `versionCode` `2`.

- Add SMB 2/3, FTP / explicit FTPS and SFTP music libraries. Manage, scan and refresh these protocols and WebDAV from library settings, with recursive folders, search, categories and continuous playback.
- Cache library snapshots separately by address and account; reuse synchronized libraries and downloaded songs offline. WebDAV folder playback uses the whole folder queue.
- Confirm SFTP server-key fingerprints before connecting and stop when a key changes. FTP uses passive mode and supports explicit TLS.
- Fix double automatic advancement in shuffle mode and delayed mini-player artwork.
- Preserve the reflective constructors used by SMB and artist-name script conversion in Release. Keep original-name matching and Traditional-to-Simplified retries for artist biographies.
- Add folder playlists to global search and bottom navigation. Enable their results by default in All search, including upgrades and older backups.
- Update main-page swiping, state restoration and remote-control focus. Poster covers overlap within groups with spacing between stacks; hold an exposed lower cover and release to bring it forward, or drag to insert beside any layer or move between groups. Save groups and layer order, with up to 10 covers per group. Keep the original complete playback popup with adjacent popups layered behind it; swipe between songs without previous/next buttons.
- Show only the selected music library’s configuration. Add an Ask every time bottom sheet for spectrum viewers.
- Rebuild Tempera, Lumiere and Claddagh compositions using the Folia reference and add Sonnet; retain global fonts and independent colors.
- Return global search to its opening tab. Conflate remote metadata updates during search, audio-quality and favorite filtering, preserving completed results without repeatedly resetting progress.
- Support native text selection for artist biographies; show category artwork and titles in tag pickers; align the audio-output panel with settings.
- Update cover and local-MV export folders and related preference backup/restoration.

Keep the four languages, package ID, system-font fallback and reduced feature scope.

## 1.0.0

### 中文

Humminbird 的第一个版本，由 Halcyon 1.3.0 精简而来。版本号 `1.0.0`，`versionCode` 为 `1`。

保留本地曲库、标签、歌单、歌词、Lyrico 歌词插件、均衡器、播放器可视化频谱、桌面歌词、状态栏歌词和本地 MV。WebDAV、Navidrome、Emby、Last.fm 和局域网网页播放器仍在。悬浮导航保留高斯模糊，卡片保留流光透过的磨砂效果。界面提供简体中文、繁体中文、英文和日文。安装包只包含 `arm64-v8a` 和 `armeabi-v7a`。

去掉在线源，评论查看。同时去掉 AI 功能、液态玻璃、USB 独占、MCP 服务、M3U8 下载器、内置 MiSans 与 Inter 字体、OriginOS 原子随身听白名单、内置频谱查看、格式转换、多音频流导出、CUE 整轨分轨，以及 FFmpegKit。本地播放仍使用单独的 FFmpeg 解码库。未选择字体时回落到系统字体。查看频谱可以交给外部应用。

### English

First Humminbird release, trimmed from Halcyon 1.3.0. Version `1.0.0`, `versionCode` `1`.

Local library, tags, playlists, lyrics, Lyrico lyric plugins, the equalizer, the in-player visualizer, desktop lyrics, status-bar lyrics, and local music videos stay. WebDAV, Navidrome, Emby, Last.fm, and the LAN web player stay. The floating navigation keeps Gaussian blur, and cards keep the frosted flowing-light background. The app ships Simplified Chinese, Traditional Chinese, English, and Japanese. Packages include only `arm64-v8a` and `armeabi-v7a`.

Removed: online sources, and comment viewing. Also removed: AI features, liquid glass, USB exclusive mode, the MCP server, the M3U8 downloader, the bundled MiSans and Inter fonts, the OriginOS Atom Walkman allowlist, the built-in spectrum viewer, format conversion, multi-stream export, CUE splitting, and FFmpegKit. Local playback still uses a separate FFmpeg decoder. An empty font choice falls back to the system font. Spectrum viewing can still be handed to an external app.
