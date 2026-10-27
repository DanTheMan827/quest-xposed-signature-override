package com.example.signinginfooverride;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Routes local OCMS asset paths by installer request, leaving backend identity intact. */
final class OcmsAssetStorageHook {
    static final String CAPABILITY = "com.example.signinginfooverride.asset_storage_v1";
    private static final String PREFIX = "com.oculus.ocms.installer2.";
    private static final String REQUEST = PREFIX + "request.InstallerRequest";
    private static final String TASK = PREFIX + "task.AbstractInstallTask";
    private static final String JOB = PREFIX + "task.InstallJobControl";
    private static final AssetRoute.Scope scope = new AssetRoute.Scope();
    private static volatile boolean installed;
    private static boolean attempted;
    private static volatile boolean recoveryReady;
    private static volatile String adapterState = "not initialized";

    static String diagnosticStatus() { return adapterState; }

    static synchronized void install(ClassLoader loader) {
        if (installed || attempted) return;
        Class<?> provider = XposedHelpers.findClassIfExists("com.oculus.ocms.library.provider.LibraryProvider", loader);
        if (provider == null) return;
        attempted = true;
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        String stage = "provider bridge";
        try {
            // Keep this read-only status endpoint alive even if a later reflective hook fails.
            installProvider(provider);
            stage = "installer classes";
            Class<?> request = XposedHelpers.findClass(REQUEST, loader);
            Class<?> installer = XposedHelpers.findClass(PREFIX + "Installer", loader);
            Class<?> task = XposedHelpers.findClass(PREFIX + "task.AssetInstallTask", loader);
            Class<?> job = XposedHelpers.findClass(JOB, loader);
            Class<?> completion = XposedHelpers.findClass(PREFIX + "Installer$packageInstallCompletionHandler$1", loader);
            Class<?> uninstall = XposedHelpers.findClass(PREFIX + "UninstallAssetTask", loader);
            Class<?> environment = XposedHelpers.findClass(PREFIX + "configuration.InstallEnvironment", loader);
            stage = "request and task fields";
            // Validate the named data used across worker/coroutine boundaries before activating.
            for (String field : new String[]{"packageName", "requestId", "installOption", "action"})
                request.getDeclaredField(field);
            job.getDeclaredField("data");
            uninstall.getDeclaredField("installRequestId");
            uninstall.getDeclaredField("packageName");
            Class<?> jobData = XposedHelpers.findClass(PREFIX + "data.InstallJobData", loader);
            for (String field : new String[]{"packageName", "installRequestId", "installOption"}) jobData.getDeclaredField(field);
            Class<?> abstractTask = XposedHelpers.findClass(TASK, loader);
            Method taskRecord = abstractTask.getDeclaredMethod("A0S");
            for (String field : new String[]{"package_name", "install_uuid", "install_option"})
                taskRecord.getReturnType().getDeclaredField(field);

            Method path = environment.getDeclaredMethod("A05", String.class, String.class);
            stage = "output paths";
            AssetStorageBridge.deoptimize(path);
            Method staging = environment.getDeclaredMethod("A06", String.class, String.class);
            AssetStorageBridge.deoptimize(staging);
            // Metadata/verification methods may have inlined these path helpers before the hooks existed.
            for (Class<?> caller : new Class<?>[]{environment,
                    XposedHelpers.findClassIfExists(PREFIX + "entitlement_ext.DLCInstallData", loader),
                    XposedHelpers.findClassIfExists(PREFIX + "InstallerUtil", loader),
                    XposedHelpers.findClassIfExists("com.oculus.ocms.am.assets.AssetManager", loader)}) {
                if (caller == null) continue;
                for (Method method : caller.getDeclaredMethods()) {
                    if (!Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers()))
                        AssetStorageBridge.deoptimize(method);
                }
            }
            hooks.add(XposedBridge.hookMethod(path, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    AssetRoute route = scope.current();
                    if (route != null && route.destination.equals(param.args[0])) {
                        param.args[0] = route.source;
                        SigningHook.log("OCMS asset output package: " + route.destination + " -> " + route.source);
                    }
                }
            }));
            // This helper carries its own request ID, even when called outside an installer worker.
            hooks.add(XposedBridge.hookMethod(staging, scoped(param -> {
                String id = (String) param.args[1];
                AssetRoute route = id == null || id.isEmpty() ? scope.current() : saved(id, (String) param.args[0]);
                if (route != null && route.destination.equals(param.args[0])) {
                    param.args[0] = route.source;
                    SigningHook.log("OCMS asset staging package: " + route.destination + " -> " + route.source);
                }
                return route;
            })));
            stage = "request construction";
            hooks.addAll(XposedBridge.hookAllConstructors(request, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    AssetRoute route = scope.current();
                    if (route == null || !assetRequest(param.thisObject)
                            || !route.destination.equals(field(param.thisObject, "packageName"))) return;
                    try {
                        String id = text(param.thisObject, "requestId");
                        if (validRequestId(id) && preferences().edit().putString(id, route.encode()).commit())
                            SigningHook.log("OCMS asset request bound to storage owner: " + route.source);
                        else throw new IllegalStateException("Asset storage route could not be persisted");
                    } catch (Throwable unavailable) {
                        SigningHook.log("OCMS asset route persistence unavailable");
                        // Abort before AppManager posts the request. Never silently download into the wrong OBB.
                        param.setThrowable(new IllegalStateException("Asset storage route unavailable", unavailable));
                    }
                }
            }));
            stage = "worker boundaries";
            int requestHooks = hookWorkers(installer, request, abstractTask, job, false, hooks);
            int taskHooks = hookWorkers(task, request, abstractTask, job, true, hooks);
            int jobHooks = hookWorkers(job, request, abstractTask, job, true, hooks);
            stage = "worker boundaries (request=" + requestHooks + ", task=" + taskHooks + ", job=" + jobHooks + ")";
            if (requestHooks == 0 || taskHooks == 0 || jobHooks == 0)
                throw new NoSuchMethodException("Asset request/worker boundary");
            // Kotlin erases this callback's parameters to Object, so the typed worker scan cannot find it.
            stage = "completion callback signature";
            Method finish = completion.getDeclaredMethod("invoke", Object.class, Object.class, Object.class);
            AssetStorageBridge.deoptimize(finish);
            hooks.add(XposedBridge.hookMethod(finish, scoped(param ->
                    workerRoute(param.args[1], request, abstractTask, job))));
            stage = "asset deletion signature";
            Method remove = uninstall.getDeclaredMethod("A00", uninstall);
            AssetStorageBridge.deoptimize(remove);
            hooks.add(XposedBridge.hookMethod(remove, scoped(param ->
                    saved(text(param.args[0], "installRequestId"), text(param.args[0], "packageName")))));
            List<XC_MethodHook.Unhook> recovery = new ArrayList<>();
            try {
                OcmsAssetRecoveryHook.install(loader, recovery);
                hooks.addAll(recovery);
                recoveryReady = true;
            } catch (Throwable unsupportedRecovery) {
                for (XC_MethodHook.Unhook hook : recovery) if (hook != null) hook.unhook();
                // A firmware-specific restart adapter must not disable correctly installed download paths.
                recoveryReady = false;
            }
            installed = true;
            adapterState = recoveryReady ? "ready" : "ready; restart grouping adapter unavailable";
        } catch (Throwable incompatible) {
            for (XC_MethodHook.Unhook hook : hooks) if (hook != null) hook.unhook();
            adapterState = "failed at " + stage + ": " + incompatible.getClass().getSimpleName();
            SigningHook.refreshLogging();
            SigningHook.log("OCMS asset output adapter " + adapterState);
        }
    }

    private static void installProvider(Class<?> provider) throws Exception {
        Method call = provider.getDeclaredMethod("doCall", String.class, String.class, Bundle.class);
        AssetStorageBridge.deoptimize(call);
        XposedBridge.hookMethod(call, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (CAPABILITY.equals(param.args[0])) {
                    SigningHook.refreshLogging();
                    Bundle result = new Bundle(); result.putInt("assetStorageVersion", installed ? 1 : 0);
                    result.putString("moduleVersion", BuildConfig.VERSION_NAME);
                    result.putString("adapterState", adapterState);
                    result.putBoolean("recoveryReady", recoveryReady);
                    param.setResult(result);
                }
                AssetRoute route = null;
                try {
                    if (AssetStorageBridge.assetOperation((String) param.args[0]) && param.args[2] instanceof Bundle) {
                        SigningHook.refreshLogging();
                        boolean marked = ((Bundle) param.args[2]).containsKey(AssetStorageBridge.ROUTE_EXTRA);
                        if (installed) route = incoming((String) param.args[1], (Bundle) param.args[2]);
                        SigningHook.log("OCMS asset envelope: method=" + param.args[0] + "; presented=" + param.args[1]
                                + "; marker=" + marked + "; storage owner=" + (route == null ? "unavailable" : route.source));
                        if (marked && route == null)
                            throw new SecurityException("Asset storage route rejected");
                    }
                } catch (Throwable invalid) {
                    SigningHook.log("OCMS asset storage marker rejected");
                    param.setThrowable(new SecurityException("Asset storage route rejected", invalid));
                }
                scope.enter(route);
            }
            @Override protected void afterHookedMethod(MethodHookParam param) { scope.exit(); }
        });
    }

    private interface RouteLookup { AssetRoute find(XC_MethodHook.MethodHookParam param); }

    private static XC_MethodHook scoped(RouteLookup lookup) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                AssetRoute route = null;
                try { route = lookup.find(param); }
                catch (Throwable unavailable) { SigningHook.log("OCMS asset worker route unavailable"); }
                scope.enter(route);
            }
            @Override protected void afterHookedMethod(MethodHookParam param) { scope.exit(); }
        };
    }

    private static int hookWorkers(Class<?> type, Class<?> request, Class<?> task, Class<?> job,
            boolean includeReceiver, List<XC_MethodHook.Unhook> hooks) {
        int count = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (!AssetWorkerMethods.boundary(method, request, task, job, includeReceiver)) continue;
            AssetStorageBridge.deoptimize(method);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    AssetRoute route = null;
                    try {
                        route = workerRoute(param.thisObject, request, task, job);
                        if (route == null) for (Object argument : param.args) {
                            route = workerRoute(argument, request, task, job);
                            if (route != null) break;
                        }
                    } catch (Throwable incompatible) { /* Unrecognized jobs retain their own paths. */ }
                    scope.enter(route);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) { scope.exit(); }
            }));
            count++;
        }
        return count;
    }

    private static AssetRoute workerRoute(Object value, Class<?> request, Class<?> task, Class<?> job) {
        if (value == null) return null;
        if (request.isInstance(value)) {
            if (!assetRequest(value)) return null;
            return saved(text(value, "requestId"), text(value, "packageName"));
        }
        if (job.isInstance(value)) {
            Object data = field(value, "data");
            if (!isAsset(data, "installOption")) return null;
            return saved(text(data, "installRequestId"), text(data, "packageName"));
        }
        if (task.isInstance(value)) {
            Object record = XposedHelpers.callMethod(value, "A0S");
            if (!isAsset(record, "install_option")) return null;
            return saved(text(record, "install_uuid"), text(record, "package_name"));
        }
        return null;
    }

    private static AssetRoute incoming(String destination, Bundle extras) throws Exception {
        String encoded = extras.getString(AssetStorageBridge.ROUTE_EXTRA);
        if (encoded == null) return null;
        Context context = SigningHook.currentContext();
        if (context == null) return null;
        // Only the platform service may carry a route from its already-validated real caller.
        String[] callers = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        boolean platformCaller = false;
        if (callers != null) for (String caller : callers) platformCaller |= "com.oculus.horizon".equals(caller);
        if (!platformCaller) return null;
        AssetRoute route = AssetRoute.decode(encoded);
        if (!route.destination.equals(destination)) return null;
        context.getPackageManager().getApplicationInfo(route.source, 0);
        Bundle mapping = SigningHook.companionCall("packageSwap", route.source, null);
        SigningHook.updateLogging(mapping);
        if (mapping == null || !route.destination.equals(mapping.getString("presentedPackage"))) return null;
        SigningHook.log("OCMS accepted asset identities: source=" + route.source + "; destination=" + route.destination);
        return route;
    }

    private static SharedPreferences preferences() {
        return SigningHook.currentContext().getSharedPreferences("signature_override_asset_requests_v1", Context.MODE_PRIVATE);
    }

    static AssetRoute saved(String id, String destination) {
        if (!validRequestId(id)) return null;
        AssetRoute route = AssetRoute.decode(preferences().getString(id, null));
        return route != null && route.destination.equals(destination) ? route : null;
    }

    private static boolean validRequestId(String id) { return id != null && !id.isEmpty() && id.length() <= 256; }
    private static Object field(Object value, String name) { return XposedHelpers.getObjectField(value, name); }
    private static String text(Object value, String name) { return (String) field(value, name); }
    private static String enumName(Object value) { return value instanceof Enum<?> ? ((Enum<?>) value).name() : null; }
    private static boolean isAsset(Object value, String name) { return "InstallAsset".equals(enumName(field(value, name))); }
    private static boolean assetRequest(Object value) {
        return AssetRoute.assetRequest(enumName(field(value, "action")), isAsset(value, "installOption"));
    }
}
