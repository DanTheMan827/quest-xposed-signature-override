package com.example.signinginfooverride;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Carries the validated real caller alongside the presented platform package. */
final class AssetStorageBridge {
    static final String AUTHORITY = "com.oculus.ocms.library";
    static final String ROUTE_EXTRA = "com.example.signinginfooverride.ASSET_STORAGE_ROUTE_V1";
    private static volatile boolean installed;
    private static volatile long capabilityChecked;
    private static volatile boolean capabilityReady;
    private static volatile String failureState = "not installed (service classes or caller hook absent)";
    private static volatile boolean callersDeoptimized;

    static String diagnosticStatus() {
        return installed ? "ready; caller deoptimization=" + callersDeoptimized : failureState;
    }

    static boolean assetOperation(String name) {
        return "install_asset".equals(name) || "uninstall_asset".equals(name)
                || "cancel_download_asset".equals(name);
    }

    static synchronized void install(ClassLoader loader) {
        if (installed || !EntitlementSwapHook.callerHookReady()) return;
        Class<?> utility = XposedHelpers.findClassIfExists("com.oculus.horizon.service.AssetBundleUtil", loader);
        if (utility == null) return;
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            Method path = utility.getDeclaredMethod("getFinalAssetPath", String.class, String.class);
            // Deoptimizing the target alone does not remove copies already inlined into its callers.
            for (Method method : utility.getDeclaredMethods()) {
                if (!Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers()))
                    callersDeoptimized |= deoptimize(method);
            }
            hooks.add(XposedBridge.hookMethod(path, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    AssetRoute route = EntitlementSwapHook.assetRoute();
                    // Local presence is always about the real caller, including assets downloaded earlier.
                    if (route != null) {
                        param.args[0] = route.storagePackage((String) param.args[0]);
                        SigningHook.log("Horizon asset path owner: " + route.destination + " -> " + route.source);
                    }
                }
            }));
            for (Method method : ContentResolver.class.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("call") || types.length != 4 || types[1] != String.class
                        || types[2] != String.class || types[3] != Bundle.class
                        || (types[0] != Uri.class && types[0] != String.class)) continue;
                hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        AssetRoute route = EntitlementSwapHook.assetRoute();
                        if (route == null || !route.destination.equals(param.args[2])
                                || !assetOperation((String) param.args[1])) return;
                        Object endpoint = param.args[0];
                        String authority = endpoint instanceof Uri ? ((Uri) endpoint).getAuthority() : (String) endpoint;
                        if (!AUTHORITY.equals(authority)) return;
                        if (!outputAdapterReady()) {
                            SigningHook.log("Asset operation blocked: source storage adapter unavailable; method=" + param.args[1]);
                            param.setThrowable(new IllegalStateException("OCMS source asset storage adapter is not ready"));
                            return;
                        }
                        Bundle extras = param.args[3] instanceof Bundle ? new Bundle((Bundle) param.args[3]) : new Bundle();
                        extras.putString(ROUTE_EXTRA, route.encode());
                        param.args[3] = extras;
                        SigningHook.log("Asset storage request: " + route.destination + " -> " + route.source);
                    }
                }));
            }
            if (hooks.size() < 2) throw new NoSuchMethodException("ContentResolver.call");
            installed = true;
        } catch (Throwable incompatible) {
            for (XC_MethodHook.Unhook hook : hooks) if (hook != null) hook.unhook();
            failureState = "failed: " + incompatible.getClass().getSimpleName();
            SigningHook.log("Asset storage bridge " + failureState);
        }
    }

    static void diagnoseContentCall(String name) {
        if (!name.startsWith("assetFile") && !name.startsWith("languagePack")) return;
        SigningHook.log("Asset bridge state: version=" + BuildConfig.VERSION_NAME + "; state=" + diagnosticStatus());
        outputAdapterReady();
    }

    private static boolean outputAdapterReady() {
        long now = SystemClock.elapsedRealtime();
        if (capabilityChecked != 0 && now - capabilityChecked < 2000) return capabilityReady;
        boolean ready = false;
        String status = "no capability response";
        long identity = android.os.Binder.clearCallingIdentity();
        try {
            Context context = SigningHook.currentContext();
            Bundle result = context == null ? null : context.getContentResolver().call(
                    Uri.parse("content://" + AUTHORITY), OcmsAssetStorageHook.CAPABILITY, null, null);
            ready = result != null && result.getInt("assetStorageVersion", 0) == 1;
            if (result != null) status = "version=" + result.getString("moduleVersion", "unknown")
                    + "; state=" + result.getString("adapterState", "unreported")
                    + "; recovery=" + result.getBoolean("recoveryReady", false);
        } catch (Throwable unavailable) { status = "probe failed: " + unavailable.getClass().getSimpleName(); }
        finally { android.os.Binder.restoreCallingIdentity(identity); }
        capabilityReady = ready;
        capabilityChecked = now;
        SigningHook.log("OCMS asset adapter probe: ready=" + ready + "; " + status);
        return ready;
    }

    static boolean deoptimize(Method method) {
        try {
            Object result = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class).invoke(null, method);
            return !Boolean.FALSE.equals(result);
        } catch (Throwable unsupported) { return false; }
    }
}
