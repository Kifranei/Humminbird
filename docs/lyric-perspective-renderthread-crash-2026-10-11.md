# 立体歌词与边缘虚化的渲染线程崩溃

用户提供的日志中，18:27:08 和 18:27:26 出现相同的原生 SIGSEGV：

- 设备为 Xiaomi 25060RK16C / MT6991，Android 16，HyperOS OS3.0.305.0。
- 崩溃线程为 RenderThread，故障在系统 libhwui.so。
- 崩溃转储记录 512 层调用栈，重复出现 RenderNode::prepareTreeImpl 和 SkiaDisplayList::prepareListAndChildren。
- 栈指针越过可读写映射，转储提示 stack overflow。日志没有应用源码的 RenderNode 名称，无法仅凭这份栈确认具体的循环边。

用户报告触发操作是开启立体歌词。根据绘制代码，优先排查了立体变换与边缘虚化的组合链：旧 MiniLyricsEdgeFeather 手动录制 drawContent，再使用四个 GraphicsLayer 组合清晰与模糊的副本。立体图层开关改变子图层关系时，录制内容与子 RenderNode 存在交叉引用风险。这条具体路径是结合触发条件和代码作出的判断，而不是日志直接列出的图层关系。

改为一个普通的离屏图层，使用 Android 原生 RenderEffect 的输入图像、渐变蒙版和模糊滤镜组合，不再把歌词的子 RenderNode 录制到自建 GraphicsLayer。立体效果仍位于内部；上下蒙版固定在外部视口。滤镜仅作用于边缘，清晰权重为 fade²，模糊权重为 fade × (1 − fade)，两者合计为 fade；中央区域保留原图。Android 10/11 和软件截图使用透明羽化回退。

原生绘制回归覆盖 Android 10、12、15，反复切换立体效果、左右及居中对齐、改变角度并捕获内容；原有顶部边界回归继续确认中央和底部像素不变。此类桌面回归不运行该 HyperOS 的硬件 HWUI 驱动，尚需在日志对应的 Android 16 设备安装新包确认。未将构建或模拟绘制结果表述为该设备已验证。
