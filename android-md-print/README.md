# 喵喵 Markdown Android / Miao Markdown Android

中文 | English below

## 这是什么

这是给旧 Paperang P1 用的 Android Markdown 打印小工具。

P1 是早期 57mm 便携热敏打印机，常见资料里是 203 DPI、Micro-USB、蓝牙连接，机身约 83 x 83 x 45mm，重量约 160g。后面已经有 P2、P2S、P3、C1、C1S 这些新型号，P2 以后也有 300 DPI 机型。这个 App 主要照顾 P1，不把目标写成“全系 Paperang 客户端”。

这不是官方 App，也不是商业项目。它只是个人为了继续使用自己手里的旧设备做的实验。

## 功能

- 原生 Android Java。
- 经典蓝牙 SPP 是主要连接方式，适合已经在系统里配对过的 P1。
- BLE 保留为备用。
- Markdown 先渲染，再转成 P1 用的 384px 黑白热敏点阵。
- 支持常见 Markdown：标题、段落、粗体、斜体、删除线、列表、任务列表、引用、代码、代码块、链接、表格。
- 支持基础 HTML、裸 URL 自动识别和 LaTeX 公式。
- 表格打印单独做了热敏纸排版，不直接照搬屏幕 TextView。
- 编辑页有快捷按钮，手机上写 `#`、`|`、反引号、公式、表格会轻松一点。
- 可以调字号、浓度和尾纸，也会估算纸长。

## Markdown 目标

我希望它尽量接近 Typora 的日常写作体验。普通 Markdown 笔记应该能比较自然地从手机打出来。

但 P1 是窄纸热敏打印机，最终只有黑白点阵。下面这些还没当成完整功能做：

- Mermaid 和流程图
- 复杂图表
- 本地图片、远程图片
- Typora 主题 CSS
- 复杂 HTML/CSS 页面

以后如果继续加，应该单独处理渲染和打印降级，不能只靠 Markwon 默认渲染。

## 打印参数

P1 打印数据按一行 384 点处理，也就是 48 字节。

当前 App 用这些参数：

- 纸宽：`384 px`
- 默认正文字号：`19 px`
- 纸边距：`22 px`
- 纸长估算：`height_px * 0.1217 mm`
- 默认浓度：`75`
- 默认尾纸：`5 mm`

预览和打印都围绕这条 384px 宽的纸带来做，所以手机屏幕 DPI 不应该影响最终打印字号。

## 法律和边界

这个项目只用于个人自有旧设备维护和学习。

它不包含官方 App 代码、官方素材或品牌资源；不冒充官方客户端；不提供商业服务；不建议拿去批量卖、批量部署或做任何可能踩线的用途。品牌名和型号名只是为了说明兼容哪台设备。

如果相关权利方觉得这里有不合适的描述，可以提 issue，我会删或改。

## 构建

本机不需要 Android Studio、Android SDK、Gradle 或模拟器。GitHub Actions 会打 APK。

```text
Actions -> Android Debug APK -> Run workflow
```

构建产物一般是：

```text
miao-md-print-debug-apk / app-debug.apk
```

本机最新 APK：

```text
D:\paperang\latest-apk\app-debug.apk
```

## 测试顺序

1. 先在 Android 系统蓝牙里配对 P1。
2. 安装 APK，打开后给蓝牙权限。
3. 点“连接打印机”。
4. 点“编辑”写 Markdown。
5. 回到预览，点“打印 Markdown”。
6. 打印太淡就加一点浓度，糊了就降一点浓度或字号。

## English

This is a small Android Markdown printing app for an old Paperang P1.

The P1 is an early 57mm pocket thermal printer, commonly listed as 203 DPI, Micro-USB, Bluetooth, roughly 83 x 83 x 45mm, and about 160g. Newer lines such as P2, P2S, P3, C1, and C1S exist, and some later models are 300 DPI. This app is mainly for P1, not a universal Paperang client.

It is not an official app and not a commercial project. It is just an experiment for keeping my own old device usable.

## Features

- Native Android Java.
- Classic Bluetooth SPP is the main connection path for a paired P1.
- BLE is kept as a fallback.
- Markdown is rendered and converted into a 384px-wide black-and-white thermal raster.
- Common Markdown works: headings, paragraphs, bold, italic, strikethrough, lists, task lists, quotes, inline code, code blocks, links, and tables.
- Basic HTML, bare URL link detection, and LaTeX formulas are enabled.
- Tables use a print-specific thermal layout instead of copying the screen TextView directly.
- The editor has shortcut buttons for symbols that are annoying to type on a phone.
- Font size, density, post-print feed, and paper length estimate are adjustable.

## Markdown Goal

The goal is to get close to the everyday Typora writing experience. Normal Markdown notes should print without much fuss.

But P1 is a narrow black-and-white thermal printer. These are not complete features yet:

- Mermaid and flowcharts
- Complex diagrams
- Local or remote images
- Typora theme CSS
- Complex HTML/CSS pages

If added later, they need their own rendering and print fallback logic.

## Print Parameters

The P1 print stream is handled as 384 dots per row, or 48 bytes per row.

Current app constants:

- Paper width: `384 px`
- Default text size: `19 px`
- Paper padding: `22 px`
- Length estimate: `height_px * 0.1217 mm`
- Default density: `75`
- Default post-print feed: `5 mm`

Preview and print are both built around this 384px paper strip, so phone screen DPI should not change the final printed font size.

## Legal Boundary

This project is only for personal learning and maintenance of personally owned old hardware.

It does not contain official app code, official assets, or brand resources. It does not pretend to be an official client, does not provide a commercial service, and is not meant for bulk resale or deployment. Brand and model names are only used to describe compatible hardware.

If a rights holder thinks any wording is inappropriate, open an issue and I will remove or adjust it.

## Build

This machine does not need Android Studio, Android SDK, Gradle, or an emulator. GitHub Actions builds the APK.

```text
Actions -> Android Debug APK -> Run workflow
```

Artifact:

```text
miao-md-print-debug-apk / app-debug.apk
```

Local latest APK:

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
