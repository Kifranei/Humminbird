# 许可证迁移与发布材料记录

此记录描述代码和打包证据，不宣称完成全部版权、传递依赖或厂商合同的法律审计。

## 液态底栏

三个原 GPL 源文件已替换，独立的 `DragGestureInspector.kt` 已删除。
`DampedDragAnimation` 和 `InteractiveHighlight` 从 Miuix v0.9.4 重新引入，保留来源记录；手势检测使用其内置实现，并在 Initial 阶段接管超过阈值的横向移动，避免子按钮吞掉拖拽。
`FloatingBottomBar` 从同一版本的 `LiquidGlassNavigationBar.kt` 重新适配，保留公开内容接口、三种外观模式、圆角和光学参数、RTL、电视模糊回退及焦点反馈；短按与拖拽提交分开，取消拖拽恢复原选择；页面滑动进度单向驱动气泡，底栏拖拽松手后才切换页面。

来源固定为 `39c40f99844227b853f0049a0933b1f3ae6c00ba`，并保留 compose-miuix-ui 与 Kyant0/AndroidLiquidGlass 的 Apache-2.0 来源说明。
原始和适配文件散列见 [provenance](licenses/miuix-example-provenance.json)，许可证见 [Apache-2.0](licenses/miuix-Apache-2.0.txt)。旧 GPL 实现不参与构建。

CI 运行 `python tools/check_licenses.py`。修改这些适配文件需要重新核对并更新来源记录；应用代码重新引入 GPL/AGPL SPDX 文件头会失败。此检查不能识别没有声明来源的复制，也不能替代人工相似度审查。

## LGPL 与 MPL 发布材料

- 解码器 FFmpeg 为 9.0.2；源归档 SHA256 为 `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e`。9.0.2 的源码、许可证、JNI 源码、编译脚本、对应静态库和头文件随本次本地材料包提供。工作区被忽略的旧 6.0.2 目录不是对应源码，禁止用它制作发布材料。
- FFmpeg 的当前配置禁用 GPL、nonfree 和 version3；每个发布 APK 仍需核对实际 `.so`，不能只根据脚本确定成品许可证。
- TagLib 2.3.2 源码同时提供 LGPL-2.1-or-later / MPL-1.1；保留两份许可证和完整本地源码。本次提供的源码与重编译材料不擅自改变其许可声明。
- Halcyon 的 FFmpegKit `full` 6.0-2.LTS 与 `full-gpl` 不同，记录其 LGPL-3.0 来源和对应源码。Humminbird 不打包 FFmpegKit，因此不将其列为 Humminbird 成品依赖。
- Lyric-Getter-Api 7.0.0 和 SuperLyricApi 3.6 提供固定 revision 的完整上游源码与 LGPL-2.1 文本，并保留当前应用源码，供重新编译包含修改后的 Java/Kotlin API。

发布 APK 时应将材料包一并提供；本地生成材料不等于已经在远端发布。重编译和替换说明见材料包 README。最终核验记录绑定 APK SHA256、原生库散列与源码归档。

## 尚需权利人或合同证据的部分

- MiSans：嵌入应用与单独公开字体文件是不同分发行为。保留功能，尚未确认公开 Git 中原始 TTF 的再分发许可；材料包不再单独复制该字体。
- HONOR media-audio SDK：公开 Maven POM 没有声明开源许可证；需要适用的 SDK 再分发条款，不能标为 Apache-2.0。
- Beautiful Lyrics、BetterLyrics、MusicFree、Folia：目前的引用说明不替代对具体代码来源的审查。动画思想、协议兼容与视觉参考不自动等于获得源代码复制授权；本次没有完成所有参考实现的逐行相似度审查。

这些未确认事项保留为发布前的授权核验项，未通过删除声明或改写许可标签掩盖。
