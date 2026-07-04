# ParaLLEl Lite — Android SM64 Rom Hack Launcher
> **The first mobile-first SM64 Rom Hack manager and frontend wrapper for Android handhelds. Vibe-coded from scratch.**
<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/6deb0870-108f-4d80-aea2-af3ed56dd5b7" />

## Architecture

The app follows a single-source-of-truth MVVM structure:

- **`ui/`** — Jetpack Compose. A Material3 design system (`ui/theme/`) drives all
  colours, typography and shapes; screens are split into small stateless
  components (`HeaderBar`, `HackGrid`, `HackDetailPanel`, dialogs, `LoginScreen`).
- **`ui/launcher/LauncherViewModel`** — owns all UI state as an immutable
  `StateFlow<LauncherUiState>`; the UI is a pure function of that state.
- **`data/`** — `HackRepository` (metadata + auth), `PatchLauncher` (download →
  BPS patch → stage → deep-link), `SessionStore` (encrypted token storage).
  Networking is Retrofit/OkHttp + kotlinx-serialization; the auth token is only
  attached to Romhacking hosts so it never leaks to third-party patch CDNs.
- **`patching-engine/`** — native FLIPS BPS patcher over JNI.

Dependencies are wired manually in `di/AppContainer`.

### 🛠️ Setup Prerequisites
To run games successfully via this launcher, ensure you have the official **RetroArch** (or RetroArch Aarch64) client installed on your device with the **paraLLEl-N64** core downloaded.

## Features

- **Direct account data syncing** — Pulls hack metadata directly from your account profile
- **Dynamic BPS patching layer** — Downloads and applies BPS patch files on-device against your base ROM, including ZIP archive extraction
- **Automated high-performance RetroArch execution routing** — Dynamically resolves the installed RetroArch package (`com.retroarch.aarch64` or `com.retroarch`), stages the patched ROM to public storage, and deep-links into the ParaLLEl N64 core with a single tap

---

## Requirements

- Android device with RetroArch installed (Play Store or standalone `aarch64` build)
- ParaLLEl N64 core installed inside RetroArch
- A legally obtained Super Mario 64 base ROM (`.z64` or `.v64`)

---

## Credits

Full credit and inspiration goes to the original **[Parallel Launcher](https://parallel-launcher.ca/)** desktop ecosystem for the project inspiration, account structure, and baseline hack metadata API concepts that made this project possible.

---

## License

This project is open source. Pull requests welcome. No warranties. No guarantees. It's spaghetti. You've been warned.
