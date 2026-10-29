# Quest Xposed Signature Override

An Xposed module designed for Meta Quest devices to override and spoof package signatures at the system level.

---

## 🛠️ Overview

Package signature verification ensures that installed applications match their original cryptographic certificates. On Meta Quest (Android-based) environments, this restriction can make side-loading modded applications, replacing system-signed packages, or managing modified environments difficult.

**Quest Xposed Signature Override** hooks into the Android package manager service (`system_server`) to intercept signature checks, allowing custom applications or modifications to run seamlessly without signature mismatch errors.

---

## ✨ Features

- **System-Level Hooking:** Directly intercepts `PackageManagerService` signature verification methods.
- **Selective Override:** Target specific package names without disabling global system security checks.
- **Lightweight & Efficient:** Built cleanly on modern Xposed APIs with minimal performance overhead.
- **Quest-Optimized:** Tailored to run smoothly within Meta Quest Horizon OS environments equipped with an Xposed framework (e.g., LSPosed).

---

## 📋 Prerequisites

Before installing, ensure your Meta Quest device meets the following requirements:
1. **Root Access:** Rooted Meta Quest environment (Magisk / KernelSU / APatch).
2. **Xposed Framework:** An active, compatible Xposed framework installed (such as **LSPosed**).
3. **ADB / Side-loading Setup:** Basic familiarity with `adb` or a file manager on the headset for installation.

---

## 📥 Installation

1. Download the latest release APK from the [Releases](../../releases) page.
2. Install the APK onto your Meta Quest device using ADB:
```bash
adb install quest-xposed-signature-override.apk
```
3. Open your Xposed Manager app (e.g., **LSPosed**).
4. Navigate to the **Modules** tab and enable **Quest Xposed Signature Override**.
5. Set the module scope to **System Framework** (`android`) and any target packages if applicable.
6. **Reboot** your headset for the hooks to take effect.

---

## 🏗️ Building From Source

If you want to build the module yourself, follow these steps:

1. Clone the repository:
```bash
git clone https://github.com/DanTheMan827/quest-xposed-signature-override.git
cd quest-xposed-signature-override
```
2. Build the project using Gradle:
```bash
./gradlew assembleRelease
```
3. Find the compiled APK in `app/build/outputs/apk/release/`.

---

## ⚠️ Disclaimer

This tool is intended for developer testing, personal customization, and research purposes. Modifying system-level package management can carry security implications—use responsibly and at your own risk.
