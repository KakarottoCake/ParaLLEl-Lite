# ParaLLEl Lite — Android SM64 Rom Hack Launcher

> **A mobile-first Super Mario 64 ROM hack manager and RetroArch frontend for Android handhelds.**

<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/6deb0870-108f-4d80-aea2-af3ed56dd5b7" />

Sign in with your Romhacking.com account, browse your followed hacks with cover
art, patch a clean base ROM on-device, and launch straight into the ParaLLEl-N64
core in RetroArch — all from one screen.

---

## Features

- **Account syncing** — pulls your followed hacks and metadata directly from your
  Romhacking.com profile.
- **Cover art** — real screenshots/thumbnails for each hack, loaded on demand.
- **On-device BPS patching** — downloads and applies BPS patches against your base
  ROM (including ZIP extraction) using a native FLIPS patcher.
- **One-tap launch** — resolves the installed RetroArch package
  (`com.retroarch.aarch64` or `com.retroarch`), stages the patched ROM to shared
  storage, and deep-links into the ParaLLEl-N64 core.
- **Live star tracking (opt-in)** — shows a live star counter on a connected second
  screen (e.g. handhelds like the Ayn Thor) by decoding the RetroArch save with the
  hack's RHDC star layout.
- **Handheld-friendly UI** — filter chips, a controller-focusable grid, and a login
  flow that behaves on devices which report a hardware keyboard.

---

## Requirements

- Android device with **RetroArch** installed (Play Store or standalone `aarch64` build)
- The **ParaLLEl N64** core installed inside RetroArch
- A legally obtained Super Mario 64 base ROM (`.z64` or `.v64`)

> **Live star tracking** additionally needs All-Files access, since it reads
> RetroArch's save file from shared storage. It's off by default — enable it in
> Settings.

---

## Building

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Requires the Android SDK (compileSdk 34) and a JDK 17+.

---

## Architecture

Single-source-of-truth MVVM:

- **`ui/`** — Jetpack Compose. A Material3 design system (`ui/theme/`) drives all
  colours, typography and shapes; screens are split into small stateless components
  (`HeaderBar`, `HackGrid`, `HackDetailPanel`, dialogs, `LoginScreen`).
- **`ui/launcher/LauncherViewModel`** — owns all UI state as an immutable
  `StateFlow<LauncherUiState>`; the UI is a pure function of that state.
- **`data/`** — `HackRepository` (metadata + auth), `PatchLauncher`
  (download → BPS patch → stage → deep-link), `SessionStore` (encrypted token
  storage), and `tracking/` (star-layout decoding + RetroArch config).
- **`service/StarTrackerService`** — renders the live counter on secondary displays.
- **`patching-engine/`** — native FLIPS BPS patcher over JNI.

Networking is Retrofit/OkHttp + kotlinx-serialization; the auth token is only
attached to the Romhacking host, so it never leaks to third-party patch CDNs.
Dependencies are wired manually in `di/AppContainer`.

---

## Credits

Full credit and inspiration goes to the original
**[Parallel Launcher](https://parallel-launcher.ca/)** desktop ecosystem for the
project inspiration, account structure, star-layout format, and baseline hack
metadata API concepts that made this project possible.

---

## License

Open source. Pull requests welcome. No warranties, no guarantees.
