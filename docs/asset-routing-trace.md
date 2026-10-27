# Asset storage trace

These findings come from the Horizon smali and OCMS APK supplied for this task.
Obfuscated method names refer to those builds; they are not stable platform APIs.
Bytecode inspection establishes the call relationships below. Device behavior
and queue interactions still require testing.

The supplied runtime probe reports `version=3.11` and
`failed at worker boundaries: NoSuchMethodException`, while Horizon reports its
bridge ready and caller deoptimization available. This confirms that OCMS loaded
the module but its output adapter did not finish initialization.

In the supplied DEX, `AssetInstallTask.A00(AssetInstallTask, continuation)` has
access flags `0x1009`: public, static and **synthetic**. It is the class's sole
eligible worker method. The previous `method.isSynthetic()` exclusion therefore
produced zero asset-task hooks and triggered the adapter's boundary guard.
Version 3.12 includes synthetic methods when their typed arguments or receiver
identify the worker boundary; it still excludes abstract and native methods.

| Boundary | Earlier filter | Corrected filter |
| --- | ---: | ---: |
| `Installer` | 28 | 28 |
| `AssetInstallTask` | 0 | 1 |
| `InstallJobControl` | 39 | 39 |

These counts were checked against the attached DEX. The regression test uses a
compiler-generated static synthetic lambda with a task parameter and rejects an
unrelated synthetic lambda. Android/Xposed execution and the resulting on-device
download paths remain to be verified.

The supplied OCMS `classes.dex` has SHA-256
`447571135b0c167efcadf487119fa301a58392edaaa024e8b7de7364b228e955`.

| Method | Observed responsibility | Module treatment |
| --- | --- | --- |
| Horizon `CallerUtils.getCallingPackage(Bundle): String` | Resolves the caller and performs UID checks | Original runs before presenting the destination; retain the real source in the request frame |
| Horizon `AssetBundleUtil.getFinalAssetPath(String, String): String` | Builds final OBB paths; called by response builders and `isAssetInstalled` | Always select source storage for the validated swapped caller, including existing-file reads |
| OCMS `LibraryProvider.doCall(String, String, Bundle): Bundle` | Dispatches provider operations after the base provider permission checks | Validate the private source marker from Horizon; establish request scope |
| OCMS `LibraryProvider.installAsset(String, Bundle): Bundle` | Reads `asset_id`/`asset_name`, checks the destination library entitlement, and calls AppManager | Retain its package argument and original entitlement checks |
| OCMS `AppManagerInternal.A0e(String, AssetPayload, InstallRequestOrigin, ResultReceiver): InstallRequestResult` | Builds `StartAsset`/`InstallAsset` request, then posts a runnable to its handler | Save the storage route at request construction, before posting |
| OCMS `Installer.A00(Context, Installer, InstallerRequest): FetchResult` | Fetches install metadata through AppManager using the request's package and version | Restore storage scope; leave request and metadata fields untouched |
| OCMS `Installer.A0V(Installer, InstallerRequest, InstallData): void` | Builds task records, checks storage, and schedules jobs | Restore the request route for path planning |
| OCMS `DLCInstallData.A1s(long, boolean): List` | For file DLC, calls `A06(asset.packageName, installRequestId)` and appends `asset.fileName`; stores this as both download and real filename | Covered by request scope and the explicit request ID hook on `A06`; no APK metadata rewriting |
| OCMS `InstallEnvironment.A05(String, String): String` | Builds external storage + `/Android/obb/` + package + optional filename | Change only its package argument within a routed asset operation |
| OCMS `InstallEnvironment.A06(String, String): String` | Builds `A05(package, null) + /update[/requestId]` | Recover the explicit request snapshot even without an enclosing worker; route staging into the source tree |
| OCMS `AssetInstallTask.A00(AssetInstallTask, continuation): Object` | Executes asset installation; uses `A05` in its in-place update branch | Recover route from task record's `install_uuid`; coroutine resumes pass the task again |
| OCMS `InstallJobControl.A0C(InstallJobControl): void` | Computes staging/final directories and calls `VrosStorageManager.ensureAppAccess(File)` | Restore job scope; original access handling receives the routed paths |
| OCMS `InstallJobControl.A0D(InstallJobControl): void` | Commits staged asset files to the final directory | Both paths resolve inside the source tree; preserve the normal installer commit |
| OCMS `Installer$packageInstallCompletionHandler$1.invoke(Object, Object, Object): Object` | Receives package, job and throwable; builds cleanup paths and calls the access/commit helpers | Explicitly recover the job from argument 1; erased Object types evade the typed worker scan |
| OCMS `UninstallAssetTask.A00(UninstallAssetTask): void` | Resolves and deletes requested asset paths asynchronously | Restore its `installRequestId`/`packageName` route; do not turn an asset delete into an APK uninstall |

The task's `install_uuid` and job's `data.installRequestId` come from the
`InstallerRequest.requestId`. The route is a snapshot keyed by that ID, not an
inverse destination-to-source dictionary. Snapshot storage uses OCMS's private
preferences and contains package identifiers only.

## Existing assets and download status

`AssetBundleUtil.getAssetsListBundle` calls `getFinalAssetPath` twice for each
asset: once for the returned filepath and once for the file-existence test that
chooses `DownloadStatus.INSTALLED` or `AVAILABLE`. `getBundleForStatus` similarly
checks a path built from `AssetInfo.packageName` and `AssetInfo.name` before
building its response. It does not simply copy a destination database status.

Both checks must therefore receive the source path even when OCMS is unavailable.
Version 3.12 removes the earlier capability-dependent read fallback, which caused
destination-only downloads to appear installed in the copy when the output
adapter failed. The write bridge still checks OCMS readiness and now rejects a
forwarded write if the adapter is unavailable. This preserves the source storage
boundary without changing entitlements or pretending a destination file exists
in source OBB.

## Restart grouping

The unfinished-job reconstruction in `Installer$1.run()` groups task records by
`package_name` at DEX instruction offsets `0x063b`–`0x0641`. It then builds one
`InstallJobData` using a representative record's package, version and
`install_uuid`. Without another boundary, records from distinct routed requests
can be merged into one job with one selected commit directory.

`OcmsAssetRecoveryHook` preserves separate local groups for routed request IDs:

- Enter a thread-local frame only during this exact startup `run()` method.
- After `InstallerRequestStore.A02()` (the call at `0x05f1`, after sanitation),
  capture the records returned by `InstallerTaskRecordDao.A04(...)` at `0x05ff`.
- Observe `X.000.A0X(Map, Object)`, the verified get-or-create-list helper.
  At `X.004.A1V(Object, Object)`, the verified list-add helper, select a separate
  bucket only for an object from that exact batch whose `InstallAsset` request
  has a saved route. Match the original map, package key and list by identity.
- Require the nearest OCMS stack frame to be this startup `run()`, excluding
  grouping in nested workers. Suppress the adapter outside that frame.
- Use private map key objects containing package plus request ID. These keys are
  local grouping tokens. The original subsequent loop uses `X.001.A0n(Map)` and
  `X.000.A0W(Iterator)` to consume each entry's **value**, not its key.
- Remove an empty default bucket created by the original helper, while preserving
  ordinary destination records already in that bucket. Pass the selected list
  to the original add helper so each task record is still added once.

No task record is cloned or edited. Each recovered routed job therefore obtains
its own original request ID, metadata and stored file paths. The earlier sanitation
grouping (`0x0195`–`0x01b4`) remains unchanged. All helper names and the phase
boundary must be retraced for different OCMS builds.

`A05` is also used by APK installation, automatic updates, diagnostics and other
operations. A global argument substitution there would redirect unrelated work.
The module instead restores a route at a verified asset boundary and suppresses
it for an unrelated nested worker. All backend package fields, binary IDs,
version arguments, download URLs, checksums and entitlement results remain intact.

Additional callers inspected include `Installer$1.run()` (startup sanitation),
`InstallerUtil.A05(List)` (in-place storage estimates), and `AssetManager.A00(...)`
(verification). Startup sanitation handles records across packages and is not
given one blanket route. Stored task filenames already contain the selected
source staging path. Legacy downloads without a snapshot remain legacy downloads.

The current source does not patch the native CAPI loader, copy or move files from
one package's OBB to another, or change Android's package database. Concurrent
same-destination live scheduling/deduplication and restart sanitation are still device
validation items; bytecode inspection alone does not prove those scenarios.
