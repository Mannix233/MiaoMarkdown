# Miao MD Print Android

Native Android prototype for Miaomiaoji/Paperang P1 Markdown printing.

This project is intended to be built in the cloud with GitHub Actions, so this PC does not need Android Studio, Android SDK, Gradle, or an emulator.

## Cloud build

Recommended repository root:

```text
D:\paperang
```

Push `D:\paperang` to GitHub, then run:

```text
Actions -> Android Debug APK -> Run workflow
```

The APK artifact will be:

```text
miao-md-print-debug-apk / app-debug.apk
```

## Current app status

- Native Android Java app
- BLE scan for Paperang/Miao devices
- Connects to service `49535343-fe7d-4ae5-8fa9-9fafd205e455`
- Writes to characteristic `49535343-8841-43f4-a8d4-ecbe34729bb3`
- Classic Bluetooth SPP fallback for paired devices
- Renders Markdown with Markwon before converting the preview to a 384 px bitmap
- Chinese Edit/Preview UI with connection, route, font size, density, post-print feed, and paper-length estimate
- Supports CommonMark plus GFM tables, task lists, strikethrough, and basic HTML through Markwon plugins
- Sends Paperang-style packets with CRC and feed command

## Raster sizing

The Android raster path follows the desktop Electron raster path:

- Paper width: `384 px`
- Default content font: `19 px`
- Paper padding: `22 px`
- Feed estimate: `height_px * 0.1217 mm`
- Minimum estimate: `20 mm`
- Print density default: `75`
- Post-print feed default: `5 mm` (`5 * 56 = 280` feed units)

The UI preview uses the same fixed-width paper view as printing, so phone screen density should not change printed font size.

This is still a prototype and needs real-device APK testing.

Last cloud-build trigger: 2026-07-07.

## Device test order

1. Pair the printer in Android Bluetooth settings first.
2. Open the APK and grant Bluetooth permissions.
3. Try `Classic paired connect`.
4. Press `Black stripe test`.
5. If Classic fails, try `BLE scan/connect`, then `Black stripe test`.
