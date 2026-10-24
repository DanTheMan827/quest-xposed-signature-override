# SigningInfo Override 2.0 — direct Horizon hook

Hooks `com.oculus.auth.coreutil.AppVerifierUtils.getSignatureFromPackageName(String)`
directly inside processes selected in LSPosed/Vector. An SD-card config maps package
names and installed numeric version codes to replacement public signing certificates
or certificate SHA-256 digests. Editing records needs no module or libmain.so rebuild.

This replaces the previous generic SigningInfo/PackageManager hooks. No token
requests/responses, installer identity, version metadata, system signing records,
or actual APK signatures are changed. No compiled APK or certificate is included.

## Configuration

The scoped Horizon process reads:

```text
/sdcard/SignatureOverrides/config.json
```

Example, also included at SignatureOverrides/config.json:

```json
{
  "overrides": [
    {
      "packageName": "com.activ8.kizunaaivr",
      "versionCode": 123456,
      "certificateFile": "certificates/kizuna-original.pem"
    }
  ]
}
```

**Replace 123456 with the installed game's numeric versionCode.** Matching uses
PackageInfo.getLongVersionCode(), not the display versionName. No wildcard package
or version is supported. Find the installed value from PowerShell:

```powershell
adb shell dumpsys package com.activ8.kizunaaivr | Select-String 'versionCode='
```

The certificateFile path is relative to the config directory and must stay inside
it. PEM and DER X.509 formats are accepted; supply exactly one PUBLIC certificate.
The module computes SHA-256 of its encoded certificate bytes and returns URL-safe
Base64 without padding or wrapping, matching the uploaded Horizon implementation.

Alternatively replace certificateFile with a sha256 field containing either a
64-character hex certificate digest (no colons) or a 43-character unpadded
Base64URL certificate digest. Specify exactly one of these two fields per record.
This is the signing-certificate digest, not the APK file hash or a token.

Add records to overrides for more packages/versions. Duplicate matches, invalid
JSON, invalid digests, or read failures leave Horizon's original method in place.
Each file is limited to 64 KiB; at most 256 records. JSON must be UTF-8 without a
BOM. An empty `{"overrides":[]}` disables overrides.

## Export the certificate and upload

From the project root with Build Tools 34.0.0 installed:

```powershell
.\tools\export-certificate.ps1 -OriginalApk 'C:\path\original.apk'
```

The helper verifies the original APK and writes its public certificate into
SignatureOverrides/certificates/kizuna-original.pem. Use the original APK, not a
re-signed copy. Pass -ApkSigner for a different full path to apksigner.bat and
-OutputFile for another local output path. Multiple certificate blocks are rejected
rather than silently selecting a signer/rotation ancestor. If script execution
is blocked, run a child PowerShell without changing the permanent policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\export-certificate.ps1 -OriginalApk 'C:\path\original.apk'
```

Edit config.json's versionCode, then upload:

```powershell
adb shell mkdir -p /sdcard/SignatureOverrides
adb push .\SignatureOverrides\. /sdcard/SignatureOverrides/
```

Confirm the resulting paths, avoiding an extra nested SignatureOverrides folder.
Config and certificate files are reread on every lookup. To update while the broker
is running, transfer config.json.new and rename it to config.json after upload,
so queries do not read a partial file. Horizon may serve a cached token without
calling this method again; editing the config does not invalidate that cache.

## Build and activate

Use JDK 17, Gradle 8.9, Android SDK Platform 35, Build Tools 34.0.0, and the pinned
Android Gradle Plugin 8.7.3. Set ANDROID_HOME or local.properties sdk.dir to the
SDK root. A wrapper is not bundled: copy gradlew, gradlew.bat and gradle/wrapper/
from your Gradle 8.9 project or generate them with installed Gradle 8.9:

```powershell
gradle wrapper --gradle-version 8.9
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

No build-time certificate asset is required. Select **com.oculus.horizon** in the
module's LSPosed/Vector scope and reboot after installation/update. Preserve the
module signing key when updating its APK. Application ID is unchanged:
com.example.signinginfooverride. Requires Android 9+ and legacy Xposed API support.
No launcher activity or settings UI is supplied.

## Implementation and verification

The hook reads the queried package argument and verifier context field, obtains
the installed version, and only overrides exact configuration matches. It resolves
the actual return type from the method and invokes its public String constructor.
In the supplied Horizon.zip this is X.0Xf, whose sha256Hash field holds a 43-character
digest. Constructor/field compatibility is checked. A fresh wrapper is returned for
each successful match, after verifying that it retained the configured hash.

Logs:

```text
SigningInfoOverride v2.0: direct verifier hook installed in ...
SigningInfoOverride: returned configured signature for com.activ8.kizunaaivr; versionCode=...; app_signer=...
```

A version mismatch logs the installed versionCode. Configuration/read/lookup failures
log a fallback error once until a successful matching or mismatched-version query
resets error reporting. No matching package returns the original result without an
override log. The original method's exceptions remain possible on all fallback paths.

**Horizon itself must be able to read the SD-card files.** Root-shell visibility or
a grant to the game does not establish Horizon's access. This module does not grant
filesystem permissions, change SELinux policy, or fake storage permission checks.
Unreadable files cause fallback and a framework-log error. Do not put private keys
or tokens in the config directory. No account credentials, tokens, Authorization
headers, or requests are logged; app_signer is a public certificate digest.

Other firmware may change the class, context field, wrapper shape, or call path.
Calls bypassing this method are unaffected. This does not guarantee server
authentication succeeds and does not generate server-issued tokens or signatures.

## Validation

The hook was matched against the uploaded Horizon.zip method signature, context
field, return-type constructor, and sha256Hash field. XML, config wiring, digest
format and archive checks were performed. The environment lacks a working Android
SDK/JDK/Gradle, so APK compilation and device tests were not performed.
