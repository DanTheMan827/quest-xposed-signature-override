# Signature Overrides 3.1

An Android Xposed module and landscape tablet manager in **one APK**. There is no
companion APK and no JSON configuration. The manager has a launcher icon; the
module entry point is in the same application. The launcher uses an adaptive
certificate-and-signature icon, with a monochrome variant for Android themed icons.

## Override files

Place public signing certificates in:

```text
/sdcard/SignatureOverrides/[bundle id]+[bundle version identifier].pem
```

On Android, the bundle ID is the package name and the version identifier is the
**numeric `PackageInfo.getLongVersionCode()`**, not the display `versionName`.
For example, `org.example.application+4294967297.pem` matches that exact installed
package and version. The `+` is a literal separator. Versions must be nonnegative,
canonical decimal integers. There are no wildcard or cross-version matches.

Each file contains 1–16 public X.509 PEM certificate blocks, one per **current**
signer, with a total size of at most 64 KiB. Multiple current signers are kept as
a set in one file. Do not append rotation ancestors as additional current signers.
No private keys, raw hashes or JSON entries are accepted. Invalid, duplicate,
oversized or unreadable certificates leave the original package-query result intact.
Certificate expiry is shown as information; expired Android signing certificates
are not rejected solely because of their dates.

The old `config.json` is no longer read. To migrate, rename each certificate using
its old entry's package name and numeric version code, then place it directly in
`SignatureOverrides`. Hash-only entries require obtaining the original public
certificate; a certificate cannot be reconstructed from its hash.

## Companion UI

Install the module APK and launch **Signature Overrides**. The interface uses a
landscape, two-pane tablet layout with large controls suitable for a Quest 3 panel.
It supports resizing; the toolbar can scroll on narrower panels.

- **Available overrides** lists PEM files and their package/version identifiers.
  Select an entry for signer subject, issuer, validity dates, serial number,
  public-key algorithm and SHA-256 fingerprint. Malformed PEMs remain visible with
  an error so they can be identified.
- **Capture installed app** searches installed apps by label, package or version.
  Select an app to inspect its current signer certificates, then choose
  **Capture to SignatureOverrides**. The filename is generated automatically.
  Existing files require explicit replacement confirmation. An app version or
  signer change while the details are open aborts capture until it is reselected.
- **File access** opens Android's All Files Access settings on Android 11+, or
  requests both read and write storage permissions on Android 9–10. On first launch,
  the app explains why PEM file access is needed and offers to request the grant.
  Returning from settings refreshes the list. Denial or revocation blocks PEM
  reads and saves with a visible explanation; installed-app inspection still works.
  If a runtime grant was permanently denied, File access opens the app's settings.
- **Verbose module logging** is **disabled by default**. Enable the switch for
  package-query traces, selected PEM read routes, exact matches, signer counts and
  fallback errors in the Xposed framework log. Disabling it suppresses the module's
  diagnostic messages. Scoped apps re-read the setting on subsequent package queries,
  so changing it does not require a restart. Missing or unavailable settings keep
  logging disabled. The switch is saved privately by the companion app; the
  read-only bridge exposes its value without permitting external changes.

Files are staged and renamed so lookups do not see a partially written PEM.

Capture uses `GET_SIGNING_CERTIFICATES` and `SigningInfo.getApkContentsSigners()`.
It does **not** back up the app or read private app data. An app's `allowBackup=false`
setting does not prevent certificate capture. Packages, numeric versions and public
signer certificates are read through Android's package APIs; storage permission is
needed to read and save PEM files in the shared directory. Captures only expose the
currently installed APK's signers: capture the original before replacing or
re-signing an app. The manager excludes itself from its own module's hooks.
Installed-app discovery is for the current Android user/profile.

## Scope and behavior

Enable the module in LSPosed/another framework supporting legacy Xposed API 82.
Scope it to the **client apps that query package signatures**, then restart those
processes (or reboot after installation/update). The module is general purpose:
there are no hard-coded third-party package names, vendor verifier classes or
package-specific diagnostics.

The hook replaces signature information in results from Android's
`ApplicationPackageManager.getPackageInfo`, `getInstalledPackages`, and
`getPackagesHoldingPermissions` overloads. It updates both legacy `signatures`
and modern `signingInfo` when present. The installed package name and long version
code determine the exact PEM filename. Other package metadata is retained in a
parcel copy, so framework-cached objects are not mutated. Replacement signing
information describes the supplied current signers without claiming a rotation
history or signature scheme that was not captured.

Files are read on each package lookup; no module rebuild is needed. Existing app
caches may require restarting the querying app. Removing a PEM disables its
override on the next uncached lookup.

The hook first tries ordinary direct filesystem access. If the scoped client
cannot read shared storage, it tries a read-only ContentProvider in the same
module APK. Granting the manager file access therefore avoids needing to grant
storage access to every scoped client. The provider exposes only validated public
PEM certificates with valid package/version filenames; it cannot write or expose
arbitrary files. The companion checks its storage grant before each PEM operation.
The provider also serves the read-only logging preference independently of storage
permission. If both certificate read routes fail, the hook preserves the original
result. The module's diagnostic output is gated by the verbose logging setting.

This version replaces the earlier vendor-specific verifier hook with generic
Android package-query hooks. It does not intercept direct Binder queries,
`hasSigningCertificate`, `checkSignatures`, native APK parsing, or verifier code
that bypasses the listed methods. It does not alter APK files, system package
records, signature permissions, installer checks or server-side verification.
Do not scope it into Android's system-server process; that process is explicitly
excluded. Firmware with incompatible hidden `SigningInfo` internals falls back
to the original result and records a framework log entry only when verbose logging
is enabled.

## Build

Use JDK 17, Android SDK Platform 35 and the bundled Gradle 8.9 wrapper. Android
Gradle Plugin 8.7.3 is pinned. Set `ANDROID_HOME` or `local.properties` `sdk.dir`.
The Xposed API repository must be reachable for dependency resolution.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `.\gradlew.bat :app:assembleDebug`. Requires Android 9+ (API 28).
The application ID remains `com.example.signinginfooverride` to permit updates;
preserve the signing key when building an update to an existing installation.
This project builds a single application module and a single APK.

To install PEMs manually:

```sh
adb shell mkdir -p /sdcard/SignatureOverrides
adb push 'org.example.application+4294967297.pem' /sdcard/SignatureOverrides/
```

Use a temporary name and rename after uploading when updating live files. The
optional PowerShell helper verifies an APK with `apksigner` and generates the
correct filename for a single signer:

```powershell
.\tools\export-certificate.ps1 -OriginalApk 'C:\path\original.apk' -PackageName 'org.example.application' -VersionCode 4294967297
```

Use the companion UI for multiple current signers; the helper rejects ambiguous
multi-block output that might include signing history.

## Validation

Run the dependency-free core checks with JDK 17 (no Android SDK needed):

```sh
./tools/test-core.sh
```

They exercise exact long-version parsing, invalid/unsafe filenames, certificate
round trips and fingerprints, rejection of non-certificate content and duplicate
signers, multi-signer PEMs, bounded reads, and symlink containment. Test fixtures
contain public certificates only.

During this change, core Java compilation and these checks passed. All Java
sources were syntax-parsed and the manifest and Gradle wiring were inspected.
**An Android SDK was unavailable, so APK compilation, UI rendering and on-device
Xposed/Quest testing have not been performed.** Before deployment, build the APK
and validate on the target firmware:

1. Launch the single installed APK, grant ordinary storage access, and inspect
   both tabs in a landscape tablet panel. Test search, scrolling and resizing.
2. Capture a backup-disabled app after granting storage access. Compare its saved PEM
   fingerprint with `apksigner verify --print-certs` on the original APK.
3. Confirm replacement prompts and stale-selection detection. Confirm multi-signer
   capture uses current signers and excludes rotation ancestors.
4. In an explicitly scoped test client, compare legacy and modern package-query
   results with an exact PEM, a version mismatch, no PEM, and a malformed PEM.
   Check both direct reads and the read-only bridge with client storage denied.
5. Deny storage permission and verify inspection still works while PEM reads/saves
   remain blocked. Grant access and return to the app, capture and replace a PEM,
   then revoke permission and confirm access is blocked again. Test runtime denial
   and permanent denial on Android 9–10 and All Files Access on Android 11+.
6. Confirm the module emits no diagnostic messages on a fresh install. Enable
   verbose logging, perform a scoped query and check match/fallback diagnostics;
   disable it while the scoped app is running and confirm subsequent queries are
   quiet. Check that unavailable preferences keep logging disabled.
7. Verify unscoped clients and system package-signature checks remain unchanged.
