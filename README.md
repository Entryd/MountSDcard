# Mount SDcard (Root) 🚀

A lightweight, completely offline, and powerful Android utility for managing SD cards and OTG USB drives via Root access. Built with Material 3 and designed for maximum privacy and hardware-level control.

**Zero Internet Permissions.** Your files and crypto wallets stay strictly offline.

## ✨ Features
* **Android Mount/Unmount:** Seamlessly integrate or remove drives from the Android system (`vold`).
* **Linux Hard Mount (Bypass Mode):** Directly mount corrupted, unsupported, or Ventoy partitions straight to the Linux kernel (`/data/local/tmp/vault`), bypassing Android's infinite `checking...` loop.
* **Hardware Deep Sleep (Stealth Mode):** Send ACPI/sysfs signals to completely cut off power to the USB/OTG or SD card controller, hiding the drive at the hardware level.
* **Auto-Refresh:** Pull-to-refresh UI to instantly detect hardware changes.
* **Bilingual:** Fully supports English and Russian languages.

## 🛡️ Privacy & Security
This application is designed for paranoid-level security:
- Requires **Magisk / KernelSU / APatch / Zygisk** (Root).
- Does **NOT** request the Internet permission.
- Does **NOT** collect telemetry or analytics.

## 🛠️ Build it yourself
The project uses standard Gradle build tools.
```bash
git clone [https://github.com/Entryd/MountSDcard.git](https://github.com/Entryd/MountSDcard.git)
cd MountSDcard
./gradlew assembleRelease
