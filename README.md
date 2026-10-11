# Humminbird

[English](README.en.md)

Humminbird 是基于 [Halcyon](https://github.com/Kifranei/Halcyon) 精简的本地音乐播放器，面向 Android 10 及以上。安装包名是 `com.kifranei.humminbird.player`。

项目地址：<https://github.com/Kifranei/Humminbird>

## 能做什么

- 扫描并播放本地音乐，管理歌单、文件夹和标签。
- 播放本地 MV。本地视频文件优先。
- 本地歌词、内嵌歌词，以及 Lyrico 歌词插件。支持桌面歌词和状态栏歌词。
- 均衡器和播放器里的可视化频谱。查看频谱可以交给设置中选择的外部应用。
- 连接 WebDAV、SMB 2/3、FTP / FTPS、SFTP、Navidrome 和 Emby，并支持 Last.fm。文件协议可在音乐库设置中管理，扫描共享目录后按歌曲、专辑、艺术家和文件夹浏览、搜索与连续播放。
- 局域网网页播放器。
- 悬浮导航使用高斯模糊，卡片支持流光透过的磨砂背景。
- 界面语言：简体中文、繁体中文、英文、日文。未选择字体时使用系统字体。

## 运行环境

- Android 10（API 29）及以上。
- 安装包架构：`arm64-v8a`、`armeabi-v7a`。

## 自己编译

需要 JDK 21。

```bash
./gradlew :app:assembleRelease
```

Release 包会出现在 `app/build/outputs/renamed-apk/release/`，文件名以 `Humminbird-` 开头。一次构建会生成 arm64-v8a、armeabi-v7a 和通用包。

仓库里没有 `release.jks` 时，Release 会使用本机 debug 签名。这种包不能覆盖用正式证书签名的安装。

## 文档

- [更新记录](CHANGELOG.md)
- [Apache-2.0 许可证](LICENSE)
- [第三方许可](THIRD_PARTY_LICENSES.md)
