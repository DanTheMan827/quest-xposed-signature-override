package com.example.signinginfooverride;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Resolve package aliases at the shared package-to-app-ID lookup, before token acquisition. */
final class LibrarySwapHook {
    private static boolean installed;
    private static final ThreadLocal<Integer> depth = new ThreadLocal<>();

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        Class<?> external = XposedHelpers.findClassIfExists("com.oculus.horizon.service.ExternalPlatformLocal", loader);
        Class<?> library = XposedHelpers.findClassIfExists("com.oculus.libraryapi.OVRLibrary", loader);
        if (external == null || library == null) return;
        try {
            // All getAppID overloads in the supplied build delegate to this terminal overload.
            Method method = external.getDeclaredMethod("getAppID", Bundle.class, String.class, library);
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != String.class) return;
            deoptimizeDelegates(external);
            XC_MethodHook.Unhook hook = XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Integer previous = depth.get(); int nesting = previous == null ? 0 : previous;
                    depth.set(nesting + 1);
                    if (nesting > 0 || !(param.args[1] instanceof String)) return;
                    String source = (String) param.args[1];
                    long identity = Binder.clearCallingIdentity();
                    try {
                        PackageSwaps.packageName(source);
                        Context context = SigningHook.currentContext();
                        if (context == null) return;
                        Bundle result = SigningHook.companionCall("packageSwap", source, null);
                        SigningHook.updateLogging(result);
                        if (result == null) return;
                        String target = result.getString("presentedPackage");
                        PackageSwaps.packageName(target);
                        String presented = EntitlementSwapHook.libraryPackage(source, target);
                        if (!source.equals(presented)) {
                            param.args[1] = presented;
                            SigningHook.log("Library package lookup: " + source + " -> " + presented);
                        }
                    } catch (Throwable unavailable) { SigningHook.updateLogging(null); }
                    finally { Binder.restoreCallingIdentity(identity); }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Integer nesting = depth.get();
                    if (nesting == null || nesting <= 1) depth.remove(); else depth.set(nesting - 1);
                }
            });
            installed = hook != null;
        } catch (Throwable incompatible) {
            SigningHook.refreshLogging();
            SigningHook.log("Library swap adapter unavailable: " + incompatible.getClass().getSimpleName());
        }
    }

    private static void deoptimizeDelegates(Class<?> external) {
        try {
            Method deoptimize = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            for (Method method : external.getDeclaredMethods()) if ("getAppID".equals(method.getName())) {
                try { deoptimize.invoke(null, method); } catch (Throwable unsupported) { /* Optional. */ }
            }
        } catch (Throwable unsupported) { /* Optional framework extension. */ }
    }
}
