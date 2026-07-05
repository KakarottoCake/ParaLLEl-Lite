# ParaLLEl Lite — Android SM64 Rom Hack Launcher

> **A mobile-first Super Mario 64 ROM hack manager and emulator frontend for Android handhelds.**

<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/6deb0870-108f-4d80-aea2-af3ed56dd5b7" />

Sign in with your Romhacking.com account, browse your followed hacks with cover
art, patch a clean base ROM on-device, and launch straight into the emulator of
your choice — all from one screen.

---

## Features

- **Account syncing** — pulls your followed hacks and metadata directly from your
  Romhacking.com profile.
- **Cover art** — real screenshots/thumbnails for each hack, loaded on demand.
- **On-device BPS patching** — downloads and applies BPS patches against your base
  ROM (including ZIP extraction) using a native FLIPS patcher. Patched ROMs are
  written to a folder you choose.
- **Multiple emulators** — deep-links into RetroArch (ParaLLEl-N64 or
  Mupen64Plus-Next core) or the standalone **M64Plus FZ** app, selectable per hack.
- **Recommended settings** — surfaces each hack's required graphics/save options
  (framebuffer emulation, 16 kB EEPROM, etc.) straight from the Romhacking metadata.
- **Second-screen star counter (opt-in)** — a manual star counter on a connected
  second display (e.g. handhelds like the Ayn Thor), controlled with +/- buttons in
  the notification while you play.
- **Handheld-friendly UI** — a page-based flow (grid → detail → version picker),
  filter chips, and a login flow that behaves on devices which report a hardware
  keyboard.

---

## Compatibility & the state of N64 emulation

**Not every ROM hack will run correctly, and that's a limitation of N64 emulation
on Android — not of this launcher.** Many hacks depend on specific graphics or save
behaviours (framebuffer emulation, 16 kB EEPROM, depth-compare, LLE RSP, etc.) that
the current mobile emulator cores handle inconsistently. ParaLLEl Lite does what a
frontend can: it tells you each hack's recommended settings and lets you switch
between cores and emulators to find one that works. Beyond that, the underlying
emulator is the deciding factor.

I'd genuinely encourage the N64 emulation developers to keep expanding the
per-game options available on Android. As those options mature, a launcher like
this can naturally grow from a best-effort tool into a legitimate, first-class way
to play SM64 ROM hacks on handhelds.

---

## Requirements

- A legally obtained Super Mario 64 base ROM (`.z64` or `.v64`)
- At least one supported emulator:
  - **RetroArch** with the **ParaLLEl N64** and/or **Mupen64Plus-Next** core, or
  - **M64Plus FZ** (free or Pro)
- **All-Files access** is requested when you pick an output folder or use M64Plus FZ,
  so patched ROMs can be written where the emulator can read them.

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
  colours, typography and shapes; the app is a small page stack (list → detail →
  version picker) built from stateless components.
- **`ui/launcher/LauncherViewModel`** — owns all UI state as an immutable
  `StateFlow<LauncherUiState>`; the UI is a pure function of that state.
- **`data/`** — `HackRepository` (metadata + auth), `PatchLauncher`
  (download → BPS patch → launch), and `SessionStore` (encrypted token storage).
- **`service/StarTrackerService`** — renders the manual star counter on secondary
  displays via a foreground service.
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
