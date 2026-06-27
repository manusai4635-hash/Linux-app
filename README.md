# Linuxify

Linuxify is a performance-first Android app shell for launching a real Linux userspace on rooted and non-rooted phones. It detects device capability, chooses a safe default mode, installs packaged runtime assets, starts a foreground Linux container process, and exposes terminal/noVNC display flows.

## What is implemented

- Android 7.0+ project scaffold using Kotlin and minimal AndroidX/Material dependencies.
- Device detection for total/free RAM, CPU cores, Android SDK, low-RAM flag, supported ABIs, and root availability.
- Automatic mode selection:
  - `<1GB` or Android low-RAM device: terminal mode
  - `1–2GB`: LXDE mode
  - `2–4GB`: XFCE mode
  - `4GB+`: GNOME mode
- Runtime selection:
  - Rooted high-memory devices: Droidspaces when the native binary is packaged
  - All other devices: PRoot when the ABI binary is packaged
- Performance preparation: cache trimming, 256MB swap placeholder for low-RAM devices, CPU cap heuristics, graphics mode selection, low-battery/storage warnings, dashboard/audio feature gating.
- Foreground service lifecycle so the Linux process is not tied to the Activity.
- Floating red disconnect overlay when overlay permission is granted.
- Full-screen container Activity that opens noVNC when a GUI session is active and gracefully falls back to terminal/status guidance when assets are missing.

## Production runtime asset layout

This repository does not commit third-party native binaries or huge rootfs payloads. If you want an 800MB APK/AAB, place trusted, signed payloads under these asset paths before building:

```text
app/src/main/assets/runtime/common/*.sh
app/src/main/assets/runtime/arm64-v8a/proot
app/src/main/assets/runtime/arm64-v8a/busybox
app/src/main/assets/runtime/arm64-v8a/websockify
app/src/main/assets/runtime/arm64-v8a/x11vnc
app/src/main/assets/runtime/arm64-v8a/pulseaudio
app/src/main/assets/runtime/arm64-v8a/droidspaces
app/src/main/assets/runtime/armeabi-v7a/...
app/src/main/assets/runtime/x86_64/...
app/src/main/assets/linux_images/terminal.tar.gz
app/src/main/assets/linux_images/lxde.tar.gz
app/src/main/assets/linux_images/xfce.tar.gz
app/src/main/assets/linux_images/gnome.tar.gz
```

For a smaller APK, keep these assets out of the APK and download them on first run after SHA-256 verification.

## Build

```bash
gradle assembleDebug
```

If you add a Gradle wrapper, use:

```bash
./gradlew assembleDebug
```

## Low-end troubleshooting

- Under 1GB RAM: terminal mode is selected automatically.
- Under 2GB RAM: software rendering, audio/dashboard gating, and a 256MB swap placeholder are prepared.
- If graphics are slow: use terminal/LXDE mode, close background apps, and prefer `armeabi-v7a` builds on old 32-bit devices.
- If storage is full: remove old rootfs files from app external files or use a smaller terminal/LXDE image.
- If the container does not boot: confirm the packaged runtime has a matching ABI directory and executable `proot` or `droidspaces` binary.

## Security notes

- Linux runs as an app-owned userspace process unless a rooted Droidspaces runtime is explicitly packaged and selected.
- No analytics or data collection code is included.
- Runtime payloads should be built from source, signed, hash-verified, and updated independently of the UI shell.
