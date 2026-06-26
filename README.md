# ParaLLEl Lite — Android SM64 Rom Hack Launcher
> **The first mobile-first SM64 Rom Hack manager and frontend wrapper for Android handhelds. Vibe-coded from scratch.**
<h1 align="center" style="font-size: 3.5rem; font-weight: 900; background: linear-gradient(to right, #ff0000, #ff7f00, #ffff00, #00ff00, #0000ff, #4b0082, #8b00ff); -webkit-background-clip: text; -webkit-text-fill-color: transparent; display: inline-block; padding: 10px; animation: rainbow 5s ease infinite;">
  ⚠️ FIX ME MY CODE IS SPAGHETTI SHIT. It works, it's functional, but pull requests and architecture refactors are highly welcome.
</h1>

<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/6deb0870-108f-4d80-aea2-af3ed56dd5b7" />

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
