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
- Renders short text/Markdown-ish content to 384 px bitmap
- Sends Paperang-style packets with CRC and feed command

This is still a prototype and needs real-device APK testing.

Last cloud-build trigger: 2026-07-07.

## Device test order

1. Pair the printer in Android Bluetooth settings first.
2. Open the APK and grant Bluetooth permissions.
3. Try `Classic paired connect`.
4. Press `Black stripe test`.
5. If Classic fails, try `BLE scan/connect`, then `Black stripe test`.
