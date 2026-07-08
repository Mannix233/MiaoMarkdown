# 喵喵 Markdown / Miao Markdown

中文 | English below

## 这是干什么的

这是我给旧 Paperang P1 做的一个 Android Markdown 打印小工具。

P1 是很早的便携热敏打印机，57mm 小纸卷、203 DPI、Micro-USB、蓝牙连接，机身大概 83 x 83 x 45mm，重量大概 160g。后来 Paperang 又出了 P2、P2S、P3、C1、C1S 这些型号，P2 这一代已经是 300 DPI 了。所以这个项目没有打算覆盖所有新机器，主要就是让手里的 P1 还能在新手机上打印 Markdown 笔记。

这个项目并非官方客户端，只是个人自用、学习和维护旧设备用的东西。

## 现在能做什么

- 连接已配对的 P1，优先走经典蓝牙 SPP。
- BLE 也留着，但主要当备用路径。
- 把 Markdown 渲染成 384px 宽的黑白热敏点阵再发送给打印机。
- 支持标题、列表、任务列表、引用、代码、代码块、链接、表格、删除线、HTML 和 LaTeX 公式。
- 表格不是直接照搬手机屏幕预览，而是单独按热敏纸重新排版，尽量避免文字挤在格子里。
- 编辑页有常用 Markdown 符号按钮，不用一直在手机输入法里翻 `#`、`|`、反引号这些符号。
- 可以调字号、浓度、尾纸长度，也会估算大概会走多少纸。

## Markdown 支持到什么程度

目标是尽量接近 Typora 里常用的写法。普通笔记、标题、表格、代码、链接、HTML 小标签、数学公式这些会优先照顾。

但热敏打印最后只有黑白点阵，和电脑上的 Typora 预览不是一回事。下面这些现在不要当成已经完整支持：

- Mermaid / 流程图 / 复杂图表
- 远程图片、本地图片
- Typora 主题 CSS
- 很复杂的 HTML 排版

这些以后可以继续做，但得单独处理图片、权限、缩放和打印降级，不能只靠 Markdown 渲染器糊过去。

## 关于 P1 和尺寸

P1 常见资料里是 57mm 纸宽、203 DPI。实际打印协议里一行按 384 个点处理，也就是每行 48 字节。

这个项目里现在按这些参数来排版：

- 纸宽：`384 px`
- 默认正文字号：`19 px`
- 纸边距：`22 px`
- 纸长估算：`height_px * 0.1217 mm`
- 默认浓度：`75`
- 默认尾纸：`5 mm`

手机屏幕多大、DPI 多高，不应该改变最终打印字号。预览只是给你看版面，真正打印走的是固定 384px 点阵。

## 法律和边界

这个项目只用于个人自有旧设备维护和学习。

它不包含官方 App 代码、官方素材或品牌资源；不冒充官方客户端；不提供商业服务；不建议拿去批量卖、批量部署或做任何可能踩线的用途。品牌名和型号名只是为了说明兼容哪台设备。

如果相关权利方觉得这里有不合适的描述，可以提 issue，我会删或改。

## 构建

这个仓库用 GitHub Actions 打 APK，本机不需要 Android Studio、Android SDK 或 Gradle。

```text
Actions -> Android Debug APK -> Run workflow
```

构建出来的文件一般叫：

```text
miao-md-print-debug-apk / app-debug.apk
```

我本机最新 APK 放在：

```text
D:\paperang\latest-apk\app-debug.apk
```

## 使用顺序

1. 先在 Android 系统蓝牙里配对 P1。
2. 安装 APK，打开后给蓝牙权限。
3. 点“连接打印机”。
4. 点“编辑”写 Markdown。
5. 回到预览，点“打印 Markdown”。
6. 打印太淡就加一点浓度，糊了就降一点浓度或字号。

## English

This is a small personal Android tool for printing Markdown notes on an old Paperang P1.

The P1 is an early pocket thermal printer: 57mm paper, 203 DPI, Micro-USB, Bluetooth, roughly 83 x 83 x 45mm, and about 160g. Paperang later released newer lines such as P2, P2S, P3, C1, and C1S; P2-class devices are already 300 DPI. This app is not trying to cover every newer model. The point is simply to keep my old P1 useful with a modern Android phone.

This is not an official client and not a commercial project. It is for personal use, learning, and maintaining personally owned old hardware.

## What Works

- Connects to a paired P1, mainly through classic Bluetooth SPP.
- Keeps BLE as a fallback path.
- Renders Markdown into a 384px-wide black-and-white thermal raster.
- Supports headings, lists, task lists, quotes, inline code, code blocks, links, tables, strikethrough, HTML, and LaTeX formulas.
- Tables use a print-specific layout instead of blindly copying the phone preview.
- The editor has shortcut buttons for common Markdown symbols.
- Font size, density, post-print feed, and paper length estimate are adjustable.

## Markdown Scope

The goal is to cover the Markdown I would normally write in Typora: notes, headings, tables, code, links, small HTML tags, and formulas.

Thermal paper is still black-and-white raster output, so it will never be exactly the same as Typora on a desktop screen. These are not complete yet:

- Mermaid / flowcharts / complex diagrams
- Remote or local images
- Typora theme CSS
- Complex HTML layout

Those need separate image handling, permissions, scaling, and print fallbacks.

## P1 Layout Notes

P1 is commonly described as a 57mm, 203 DPI thermal printer. In this app the print stream is treated as 384 dots per row, or 48 bytes per row.

Current layout constants:

- Paper width: `384 px`
- Default text size: `19 px`
- Paper padding: `22 px`
- Length estimate: `height_px * 0.1217 mm`
- Default density: `75`
- Default post-print feed: `5 mm`

Phone screen DPI should not change the final print size. Preview is just a fixed-width view of the same 384px raster idea.

## Legal Boundary

This project is only for personal learning and maintenance of personally owned old hardware.

It does not contain official app code, official assets, or brand resources. It does not pretend to be an official client, does not provide a commercial service, and is not meant for bulk resale or deployment. Brand and model names are only used to describe compatible hardware.

If a rights holder thinks any wording is inappropriate, open an issue and I will remove or adjust it.

## Build

The APK is built with GitHub Actions, so this machine does not need Android Studio, Android SDK, or Gradle.

```text
Actions -> Android Debug APK -> Run workflow
```

Artifact:

```text
miao-md-print-debug-apk / app-debug.apk
```

My local latest APK path:

```text
D:\paperang\latest-apk\app-debug.apk
```

## Test Order

1. Pair the P1 in Android Bluetooth settings.
2. Install the APK and grant Bluetooth permissions.
3. Tap `连接打印机`.
4. Tap `编辑` and write Markdown.
5. Return to preview and tap `打印 Markdown`.
6. If output is too light, raise density a little. If it is blurry, lower density or font size.
