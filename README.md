# Signature Overrides 3.13

An Android Xposed module and landscape tablet manager in **one APK**. There is no
companion APK and no JSON configuration. The manager has a launcher icon; the
module entry point is in the same application. The launcher uses an adaptive
certificate-and-signature icon, with a monochrome variant for Android themed icons.

The manifest declares `xposedscope` as `com.oculus.horizon;com.oculus.ocms`, using
the legacy Xposed [recommended-scope metadata](https://github.com/LSPosed/LSPosed/wiki/Module-Scope).
These are the two service packages hosting the verified hooks. Other explicitly
selected clients can still use the generic package-signature hook. No target game
or source/destination app is hardcoded in the recommended scope.

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
  The list uses the shared categorized app selector described below.
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
  package-query traces, token and entitlement ID swaps, selected PEM read routes, exact matches, signer counts and
  fallback errors in the Xposed framework log. Disabling it suppresses the module's
  diagnostic messages. Scoped apps re-read the setting on subsequent package, token or entitlement queries,
  so changing it does not require a restart. Missing or unavailable settings keep
  logging disabled. The switch is saved privately by the companion app; the
  read-only bridge exposes its value without permitting external changes.
- **Bundle ID swaps** adds an exact `source → presented ID` dictionary. Select an
  existing entry to update or remove it, or choose **Add swap** for a new entry.
  Both ID fields support manual typing and a **Pick app** button. Selecting an
  installed app fills its exact package ID. Editing the source of an existing swap
  moves that entry in the same preferences transaction; it does not leave the old
  source behind.
  Updating an existing source asks for confirmation. Entries are saved privately
  in the same app and are available without storage permission. There are no
  built-in swaps. The same dictionary also selects the package for the two supported entitlement
  checks. An empty dictionary makes no changes to token or entitlement requests.

### Shared installed-app selector

`AppSelector` is the common installed-app control used for signature capture and
both swap-field pickers. `AppCatalog` supplies the shared category rules, ordering
and search matching; there is one package-enumeration implementation.

Categories always appear in this order:

1. **User apps** — packages without a system-app flag, including disabled user apps.
2. **Updated system apps** — packages with `FLAG_UPDATED_SYSTEM_APP`.
3. **System apps** — other packages with `FLAG_SYSTEM`.
4. **Other apps** — entries without application metadata, if any.

Each nonempty category is collapsible. User apps start expanded; other categories
start collapsed. Apps sort alphabetically within each category, with package ID as
a stable tie-breaker. Disabled apps carry a Disabled label and remain in their
proper category. Search matches display name, package ID, display version or full
numeric version and initially expands matching groups. Clearing search restores
the prior ordinary expansion state. The currently selected app is highlighted;
pickers open its group when it is already selected.

Loading runs on a worker thread. Pickers are sized for a landscape tablet panel,
support cancellation, and preserve manual entry when cancelled. No storage grant
is needed to enumerate or select installed apps.

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
there are no predefined source/destination package mappings. The optional token and entitlement
swap adapters target the APIs shown in the supplied TokenProvider listing and
Horizon archive; the
signature-query hooks remain generic.

The hook replaces signature information in results from Android's
`ApplicationPackageManager.getPackageInfo`, `getInstalledPackages`, and
`getPackagesHoldingPermissions` overloads. It updates both legacy `signatures`
and modern `signingInfo` when present. The installed package name and long version
code first select the exact source PEM. If it is absent and this package is a
configured swap source, the shared certificate resolver selects the destination's
saved PEM or installed signers. This fallback applies to signature queries about
the source/copy itself; it is not limited to requests naming the destination. Other package metadata is retained in a
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
The provider also serves the read-only logging preference and resolved swap
identities independently of PEM storage permission when an installed destination
signer is available. Only the companion activity can add or remove swap entries. If both certificate read routes fail, the hook preserves the original
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

## Token bundle ID swaps

The adapter matches the supplied call:

```java
AuthMethods.authenticateApplication(String credential, String applicationId,
    String packageName, String pushId, String signerHash, String installer,
    int versionCode, boolean dsatRequired)
```

It hooks that exact method in `com.oculus.auth.api.AuthMethods` in the selected
client process and resolves the injected concrete implementation when entering
`TokenProvider.obtainAccessToken`. This supports abstract base APIs and method
overrides; nested delegation through a hooked base method is swapped only once. The supplied `TokenProvider.obtainAccessToken` passes the outgoing
package in argument 2 (`v5`) and its certificate hash in argument 4 (`v7`). The
adapter updates these **together**, after resolving signing data with the source-first
certificate priority described below. It does not rewrite the earlier installed-package queries. This
avoids trying to look up source-app metadata under an uninstalled alias.

For a source → destination entry, certificate selection uses this priority:

1. Prefer a valid, exact `source+installedSourceLongVersionCode.pem` when the
   companion has file access. This is the same exact source/version rule used by
   ordinary certificate overrides. If the source is not installed, the original
   unsigned request version is used. An exact source override is sufficient even
   when the destination is not installed and has no saved PEM.
2. Otherwise, if the destination is installed, use its exact
   `destination+installedDestinationLongVersionCode.pem` when available, then fall
   back to the destination's current installed signer.
3. If the destination is not installed, use
   `destination+requestVersionCode.pem` with storage access granted. The request
   version is interpreted as an unsigned 32-bit value. Arbitrary versions are
   never selected from the directory.

The first matching saved PEM must be valid and contain exactly one certificate.
A malformed, unreadable or multi-signer matching PEM stops resolution rather than
silently using a different signer. If no usable identity is available, **both
original token arguments remain unchanged**. Installed-app inspection and ordinary
PEM overrides continue to support multiple signers.

The resulting hash is SHA-256 of the selected DER certificate, encoded as unpadded
Base64URL. Only a dictionary's exact source ID triggers a swap; an unrelated
package is never substituted merely because its certificate is available.

Credential arguments, platform application ID, push ID, installer, version and
flags are preserved. Tokens are not read, logged, persisted or modified by this
adapter. Swaps apply once: A → B and B → C presents B for a request originally
from A. Matching is exact and case-sensitive; wildcards, self-swaps, invalid IDs
and dictionaries exceeding 256 entries are rejected. No swap changes the
installed app's actual package ID.

Scope the module to the process containing the supplied authentication API, then
restart that process after installing this module update. Adding, changing or
removing swaps is picked up on subsequent uncached token requests without a
restart. Existing token caches are not invalidated. When supported, the adapter
requests caller deoptimization through the framework extension to reduce the
risk of inlined calls bypassing the hook. Firmware that changes the method shape,
loads a different implementation, or bypasses it is not covered. Server-side
application ID, entitlement and authentication checks still apply.

## Shared certificate resolver

`CertificateLookup` and the companion's `CertificateResolver` now serve **both**
the `PackageInfo` hook and token-argument selection. Earlier builds resolved
swapped certificates only for the final token request, while the package-query
bridge looked solely for the source PEM. This let an entitlement lookup use the
destination ID while an earlier signature query still reported the copy's signer.

A query about a mapped source now uses the same source-first priority documented
above. A destination PEM is selected using the destination's installed long version,
or the source/request version if the destination is absent. If no destination PEM
exists, its real installed signing certificates are used. Unmapped packages with
no exact PEM retain their original results. A malformed matching PEM preserves the
original result instead of silently selecting another identity.

The package-query path updates both modern `SigningInfo` and legacy `signatures`
when requested; queries without signature fields are left alone. It keeps the
source's package name, UID and other metadata. Multiple current destination signers
can be presented by package queries; the token API still requires one signer.

With verbose logging enabled, provenance now identifies the selected package,
version and origin (`source override`, `destination override` or
`destination installed signer`). Successful package overrides also log the first
certificate's public SHA-256 fingerprint. Token diagnostics identify the same
certificate origin. No credential or token values are included. Restart scoped
processes after installing this module update to discard previous signing caches.

## Entitlement package selection

The **Bundle ID swaps** dictionary applies to shared package-to-app-ID library
lookups as well as `getEntitlementCheckBundle` and
`isAppEntitled` in the supplied service build. Their real library lookups use the
configured destination ID. This adapter does not synthesize an entitlement result;
the library lookup and existing exception handling still determine the outcome.

The archive routes both methods through
`com.oculus.horizon.service.result.ResultBuilder.A00(Bundle)`. The modern path
uses `EntitlementCheckResultBuilder`. The legacy path wraps an
`IsAppEntitledResultBuilder` inside `ResultBuilderNoBundle$1.this$0`. Both first
call `CallerUtils.getCallingPackage(Bundle)` to resolve the real Binder caller.

The adapter runs that caller resolver unchanged, then selects the mapped ID only
inside one of these two builder frames. Caller UID validation failures and original
exceptions are preserved. The existing `throwIfCallNotAllowed`, login and signature
checks continue running. Caller resolution for other service requests and OS permission/microphone checks
keeps using the actual caller. Shared package-to-app-ID library lookups use the
same configured mapping as described below; their real validation results are
retained. Nested non-entitlement
builder calls suppress aliasing; per-thread frames are removed even when a builder
throws. Repeated resolutions within a frame use the first alias and never chain
A → B → C.

The read-only companion bridge resolves this mapping from the existing private
swap dictionary. No additional storage grant or separate configuration file is
needed. Unavailable mappings keep the original package. Module diagnostics still
require the verbose logging switch. The token adapter continues to resolve signing data using the same source-first
certificate priority and change the outgoing token ID and signer together.
Entitlement lookup itself does not consume a signing certificate.

These adapters match the class, method and wrapper shapes found in the supplied
archive. Updated or differently obfuscated service builds may require a new adapter.
Scope the module to the service process and restart it after installing this update.
Subsequent swap edits take effect on new checks; existing library caches are retained.

## Earlier library lookups

The supplied runtime log shows a valid destination PEM being applied, while the
source package fails with `no_package_in_lib` during registration/profile/token
preflight. That failure happens before the final `authenticateApplication` hook.
An absent source PEM is not itself a failure when usable destination signing data
is available.

`LibrarySwapHook` therefore applies exact source mappings at the shared static
`ExternalPlatformLocal.getAppID(Bundle, String, OVRLibrary)` overload used by the
supplied service's package-to-app-ID resolution. All three `getAppID` overloads in
that archive delegate to this terminal overload. This makes earlier library
consumers resolve the configured destination instead of looking up the source and
failing before token acquisition.

Only its package-name argument changes. The original library lookup, numeric
`app_id` validation, developer/library checks, exceptions and return values still
run; no entitlement, purchase, app ID or token result is fabricated. Actual Binder
caller validation and OS permission checks are retained. If a request supplies a
numeric application ID that does not match the destination, the original validation
can still reject it.

The adapter avoids nested remapping and shares the current entitlement frame's
already-selected identity so A → B does not become B → C within the same check.
A separate request genuinely originating from B can still use its own configured
mapping. The new verbose diagnostic is `Library package lookup: source -> target`.
Companion lookup failure keeps the original package and default-disabled logging.
Firmware with a different method shape may require an updated adapter.

## Platform CAPI content calls and OCMS

The supplied `ovrplatformloader.c` contains forwarding thunks, not the asset or
purchase implementations. For example, `ovr_AssetFile_GetList` calls a function
pointer, as does `ovr_IAP_GetViewerPurchases`. The loader's driver-loading routine
was not decompiled in that file. This module does not patch the loader or native
function tables.

The supplied Horizon archive confirms a separate path that earlier app-ID hooks
missed: `OVRService$4.assetFileList` and related content handlers obtain a package
directly from `CallerUtils`, then query the library. Purchase queries call
`getViewerPurchasesFromOcms` / `getViewerPurchasesDurableCacheJSONFromOcms`, which
also resolve the caller directly before `getValidPackageInfoLite`. These calls
can continue carrying the source even when `ExternalPlatformLocal.getAppID`
lookups already use the destination.

`ContentSwapHook` adds a caller-resolution scope for exactly these 18 verified
handlers in the supplied service:

- Asset list, status by ID/name, download by ID/name (including callback variants),
  delete by ID/name and download cancellation by ID/name (including callback variants).
- `languagePackGetCurrent` and `languagePackSetCurrent`.
- `getViewerPurchases` and `getViewerPurchasesDurableCacheJSON`.
- `getIAPCheckoutFlowIntent`, together with its nested `IAPCheckoutFlowResultBuilder`.

The original caller resolver runs first, including UID/package validation. The
resolved package is then mapped for the selected content request. Actual asset,
library, purchase, access checks, responses and exceptions remain in place. No
receipt, entitlement, developer role or purchase is fabricated. Asset storage is
handled separately as described below. Unsupported service handlers get a
non-translating frame so a nested microphone or permission request cannot inherit
a content alias. The shared frame also prevents repeated A → B → C translations.

The supplied OCMS APK confirms the downstream receiver endpoints:

| Component | Role verified from the attached APK |
| --- | --- |
| `com.oculus.ocms.library.provider.LibraryProvider` | Exported provider with authority `com.oculus.ocms.library` |
| `com.oculus.ocms.am.service.AppManagerService` | Exported, permission-protected service for the App Manager Binder API |
| `com.oculus.ocms.am.service.AppManagerBinder` | Implementation of `IAppManager.Stub` |
| `com.oculus.ocms.am.assets.AssetManagerService` | Internal, non-exported asset-management service |

This build intercepts the verified Horizon front-end path before forwarding to
OCMS. The OCMS adapter below routes local asset storage; it does **not** change
App Manager backend package arguments or claim
coverage of every CAPI export. A driver that sends requests directly to OCMS, or
a native network path bypassing these service handlers, needs a separately traced
identity adapter. Adding OCMS to the module scope alone does not translate such arguments.
The current generic package-signature hooks can operate in scoped processes, but
that is distinct from changing an App Manager request's package field.

## Checkout caller identity

The supplied Horizon code routes checkout through
`OVRService$4.getIAPCheckoutFlowIntent(String, Bundle)` and a
`ResultBuilderNoBundle$1` wrapping `IAPCheckoutFlowResultBuilder`. The original
builder validates the real caller, resolves the app ID through
`ExternalPlatformLocal.getAppID`, and creates the Store launch intent for that
app ID and the requested SKU.

Both the service entry and that specific nested builder now allow the one-pass
source-to-destination caller swap. Previously checkout was outside the service
whitelist and its nested builder suppressed caller translation. The shared
library hook already translated its app-ID lookup, so this gap alone does not
prove the cause of a first-attempt-only failure.

The change preserves checkout arguments, SKU, app-ID validation, the returned
intent and the real source app's result/callback routing. It does not retry a
checkout, change Store initialization, skip payment confirmation or change
purchase results. A click still produces one normal checkout invocation.

With verbose logging, compare the first failing attempt and a later successful
attempt. Look for `Platform service request: getIAPCheckoutFlowIntent`,
`Checkout caller scope opened`, package/app-ID translation, and
`checkoutIntent=present` or an exception/absent intent. If both attempts return a
valid intent with the same destination selection, investigate the downstream
Store UI/service logs; this adapter does not establish that cold-start behavior
is fixed. See `docs/checkout-routing-trace.md` for the verified call sequence.

## Direct asset storage for a swapped caller

For a swap **source → destination**, the platform continues seeing the destination
for token, entitlement, library, asset and purchase queries. Local downloaded
assets belong under **`Android/obb/source`**. Neither application's installed
version, APK metadata nor UID is substituted.

The source identity is captured before presenting the destination. A forwarded
OCMS call has Horizon's Binder UID, so OCMS cannot recover the copy by asking
which apps belong to that UID, nor by reversing a many-to-one swap dictionary.
The private marker carries both identities explicitly. OCMS verifies that marker,
keeps destination for backend calls, and saves the pair for the local installer
request. A missing marker is reported as `storage owner=unavailable`; the module
does not guess a source directory.

Enable the module for **both Horizon and OCMS**, then restart both processes.
Version 3.12 corrects a confirmed OCMS setup failure: the asset task's coroutine
helper is synthetic, and the prior worker scan incorrectly excluded it. Synthetic
methods now qualify when they carry a recognized request/task/job boundary;
abstract and native methods remain excluded. In the supplied OCMS APK the worker
counts are 28 installer methods, 1 asset-task method and 39 job methods. The earlier
filter found zero asset-task methods and disabled the output adapter. Failure
diagnostics now include these counts and distinguish later signature lookups.
`AssetStorageBridge` checks a capability endpoint installed by the OCMS adapter.
Horizon list/status/path queries always use the source directory, independently
of whether the download adapter is ready. An asset present only in destination
OBB is therefore `available`, not `installed`, for the source. The original
Horizon code checks file existence in source OBB; the module does not rewrite an
installed flag after checking the destination. This also covers files already
downloaded before the module update.

If the OCMS output adapter is absent or incompatible, a swapped caller's
forwarded asset install/delete/cancel operation raises an explicit storage-adapter
error instead of falling back to destination storage. Reads continue using source
paths and source existence checks. No destination files are copied or reclassified
as present in source OBB.
The status endpoint reports the module version, initialization stage and recovery
adapter state even when a later output hook fails. The firmware-specific restart
grouping adapter is installed separately: its failure no longer disables the
download/output hooks. In that case restart grouping retains OCMS's normal behavior.

The implementation follows these boundaries in the supplied Horizon and OCMS builds:

1. After `CallerUtils` validates the real caller, retain source and presented
   destination in the existing per-request frame. For OCMS `install_asset`,
   `uninstall_asset` and `cancel_download_asset` provider calls, add a private
   storage-owner marker to a copy of the extras. Leave the package argument as
   destination and preserve all asset IDs, callbacks and request-origin data.
2. OCMS `LibraryProvider.doCall` accepts this marker only from Horizon's UID,
   verifies the source is installed and checks the exact forward swap. Its original
   destination entitlement check still runs. Invalid markers reject the operation.
3. During synchronous `InstallerRequest` construction for `StartAsset`/`InstallAsset`,
   `UninstallAsset` or `CancelAsset`,
   save the source/destination snapshot under the generated `requestId` in OCMS's
   private preferences, before AppManager posts its worker. A persistence failure
   aborts the request rather than silently writing into the wrong package.
4. Restore that snapshot at installer planning, asset-task and job boundaries.
   Worker requests use `requestId`; task records use `install_uuid`; jobs use
   `data.installRequestId`. These identities survive asynchronous execution and
   persisted job recovery. Only asset request actions and jobs marked `InstallAsset`
   qualify. The erased Kotlin completion callback restores its route from argument
   1 (`InstallJobControl`), and `UninstallAssetTask.A00` restores its own request
   snapshot. An unrecognized
   or unrelated nested job suppresses the enclosing storage route.
5. Within that scope, change only package argument 0 of OCMS
   `InstallEnvironment.A05(String package, String filename)`. Its normal staging
   helper `A06` also calls `A05` and independently restores its explicit request ID,
   including callers outside the typed worker hooks. New downloads stage and finish inside the
   source OBB tree. OCMS retains its normal temporary-file commit process inside
   that tree. No module code copies or moves downloaded files between package
   directories, rewrites filesystem APIs, or installs symlinks.
6. Horizon `AssetBundleUtil.getFinalAssetPath` uses the same source storage owner
   for returned paths and local existence/status checks. The asset's platform
   package, entitlements and other metadata remain destination data.

There is no global destination-to-source lookup: two independent requests may
retain different source owners for the same destination. The supplied OCMS startup
loop normally groups unfinished tasks only by package. `OcmsAssetRecoveryHook`
keeps routed tasks in separate local groups by request ID before jobs are rebuilt,
without modifying the records' package, version or filename fields. It activates
only after sanitation, for the exact database batch and grouping helpers in that
startup method. Ordinary and legacy tasks retain the normal package grouping.
This does not establish that OCMS's live same-package queue deduplication supports
concurrent downloads from both copies; that behavior still needs device validation. Already-running
downloads created before this update have no route snapshot and are not migrated.
Test with a new download request after restarting the services. Existing files in
the destination directory are left there.

Verbose diagnostics include `Asset storage request`, `OCMS asset request bound
to storage owner`, `OCMS asset output package` and `OCMS asset recovery group`.
They remain disabled by default.
With verbose logging enabled, diagnostics go to both the framework log (including
Vector's module log) and Android logcat under `SigningInfoOverride`. Application
attachment logs the package/process, module version and adapter states after a
context becomes available. Asset calls log the Horizon bridge state, OCMS probe
result and selected path owner. The Horizon asset utility's callers and OCMS
metadata/verification path callers are also deoptimized where the framework
supports it, to address compiled copies of the path builders in those callers.
The bridge reports whether the optional deoptimization API was available.

To diagnose a destination OBB still being returned:

1. Enable verbose module logging in the companion. Select both Horizon and
   `com.oculus.ocms` in Vector's module scope, then restart both processes.
2. Start capturing `adb logcat -v threadtime -s SigningInfoOverride:D '*:S'`, then
   reproduce an asset list/status query and a new download from the source.
3. Inspect `Module attached` for `package=com.oculus.ocms`, `Asset adapters`,
   `OCMS asset adapter probe`, `Horizon asset path owner`, `Asset storage request`
   and `OCMS asset output package`/`staging package`. `OCMS accepted asset identities`
   shows the preserved pair, and `OCMS asset envelope` shows whether it arrived.

A missing attach line can mean the process was not restarted, not scoped, or
could not read the verbose setting. A probe failure records its exception type;
an inactive output adapter records its failed initialization stage. A successful
probe followed by unchanged returned paths points to the Horizon request/path
boundary instead of proving an installer failure. Keep the full diagnostic log,
not just lines containing the source application's package name. Diagnostics never
include credentials, tokens, asset response bodies or file contents.
Storage routing requests no additional permissions and does not change storage
access controls; OCMS runs its original file-access/permission handling on the
selected paths. The companion's normal PEM storage-permission flow is unchanged.

Enable verbose logging to see `Platform service request: METHOD; content mapping=...`,
`Platform package lookup: source -> destination`, and completion/exception or
numeric error-code diagnostics. No full request, response, credential or token is
logged. No Horizon service trace for a failing call is evidence that this adapter
is not observing that request; use the actual receiver trace to select the next
hook. A `DeveloperNotEntitled` error still naming the copy can indicate an
untranslated request path; the account must still satisfy the destination's real
entitlement/developer checks.

Companion IPC now temporarily clears Binder identity solely for reading this
module's configuration, and restores it before service execution continues. This
avoids initiating module preference requests under a remote client's inherited
Binder identity. The original caller validation still runs under that caller.

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
signers, multi-signer PEMs, bounded reads, and symlink containment. Additional checks
cover exact one-pass swaps, dictionary limits and validation, paired package/signer
argument updates, unchanged credentials and flags, rejection without partial mutation,
unsigned request versions and certificate-hash encoding. Entitlement checks cover
scoped activation, nested unrelated builders, independent nested checks, one-pass
aliases, thread isolation and exception cleanup. Catalog checks cover category
classification, user-first ordering (including disabled apps), stable sorting and
search. Shared certificate lookup checks verify source precedence, destination PEM
selection with the destination's full long version, mismatched source versions,
installed destination fallback, absent-destination PEMs, unchanged unrelated
packages, invalid overrides, missing identities, and multi-signer handling. Content boundary checks verify supported asset,
language and purchase-query handlers while excluding permission and unknown APIs.
Storage-route checks cover independent owners for the same destination, unchanged
platform identity, snapshot validation, nested suppression, thread/exception
cleanup and asset-only action selection. They do not execute Android/Xposed hooks.
The compiler-generated synthetic-worker regression ensures the same method flag
combination that caused the OCMS failure is accepted, while an unrelated synthetic
helper is excluded. The transfer check passes two distinct source/destination pairs to a separate
receiver thread without a caller frame and verifies their owners remain distinct.
Recovery-group tests check per-request separation, ordinary groups, removal of
temporary empty buckets, unrelated records/maps and stale database batches.
The verified method trace is in `docs/asset-routing-trace.md`. Test fixtures
contain public certificates only.

During this change, core Java compilation and all 57 checks passed. All Java
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

8. In Bundle ID swaps, add a source/destination pair with storage denied, update it
   after confirmation, and remove it after confirmation. Verify persistence and
   filtering, empty dictionaries and exact matching.
9. In a scoped test token caller, verify only package argument 2 and signer argument
   4 change, and that the replacement hash matches the destination's certificate.
   Test a saved destination PEM, an installed destination without a PEM, an absent
   destination with a matching request-version PEM, and malformed/multi-signer or
   unavailable destination certificates. Invalid resolution must preserve both
   original arguments. Do not print credential arguments during validation.

10. Exercise both entitlement entry points with no mapping, a mapped destination
    present in the library, and a destination absent from the library. Verify that
    library lookup receives the destination and that the actual library response
    determines the result. Confirm caller-resolution errors still propagate, and
    microphone and unsupported service requests use their original caller.
    Repeat after removing the swap and after an exception to check frame cleanup.

11. In the capture list and both ID pickers, check user-first category order,
    collapsed system groups, search across hidden groups, selected-app highlighting,
    disabled-app labels and picker cancellation. Verify manual ID entry and changing
    an existing source do not create an unintended extra swap.
12. With distinct source and destination certificates, confirm an exact source PEM
    wins even if the destination is absent. Remove it or use a mismatched source
    version and confirm destination data is used. Check malformed/multi-signer exact
    source PEMs preserve both original token arguments.

13. With verbose logging enabled, reproduce the provided preflight failure and
    look for `Library package lookup: source -> destination` before token acquisition.
    Verify registration/profile preflight uses the destination's real library entry,
    unmatched sources stay unchanged, invalid numeric app IDs remain rejected, and
    entitlement plus library adapters do not apply a mapping chain twice.

14. Query the copy/source with both `GET_SIGNATURES` and `GET_SIGNING_CERTIFICATES`.
    With no source PEM, confirm its reported signer fingerprint equals the mapped
    destination PEM (or destination installed signer). Then add an exact source PEM
    with a distinct signer and confirm it takes precedence in both package queries
    and token hashes. Check verbose provenance distinguishes these cases. An
    unmapped package and source UID/package metadata must remain unchanged.

15. Scope the module to Horizon, reinstall/restart its process, and reproduce the
    asset-list, pack status/download and purchase-query calls with verbose logging.
    Verify the service-entry and package-translation traces precede forwarding to
    OCMS. Exercise genuine success and denial responses without changing their
    values. Confirm unknown and microphone methods show `content mapping=false`.
    If a failing call never reaches these handlers, capture the direct OCMS/driver
    request path before adding another identity hook.

16. Enable the module for both Horizon and OCMS and restart both. With different
    installed source/destination versions, start a new pack download from source.
    Verify request-binding and OCMS output-package diagnostics, staging files and
    final files under source OBB, source paths returned by list/status/download,
    and unchanged token/entitlement results and version arguments. Interrupt and
    resume a new routed download across an OCMS restart. Test a normal destination
    download and an unrelated APK install to confirm their own paths remain intact.
    With OCMS unscoped, verify reads still return source paths and source file
    presence while asset writes fail explicitly. Test simultaneous requests from two copies separately: OCMS's own queue
    deduplication is outside the core routing tests. No existing destination files
    should be copied, relocated or removed by the module.

17. For an entitled asset, exercise list and status-by-ID/name with these existing
    file states, without downloading or moving anything during the query:

    | File in source OBB | File in destination OBB | Expected source result |
    | --- | --- | --- |
    | Absent | Present | `available`, source path |
    | Present | Absent | `installed`, source path |
    | Present | Present | `installed`, source path |
    | Absent | Absent | `available`, source path |

    Entitlement, asset ID, filename and versions must remain unchanged. Repeat with
    the OCMS adapter unavailable: reads retain these same results and a new write
    request reports the adapter error rather than using destination OBB.
