package com.example.signinginfooverride;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Shared caller selection for entitlement/checkout builders and scoped content service calls. */
final class EntitlementSwapHook {
    private static boolean installed;
    private static final EntitlementScope scope = new EntitlementScope();

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        Class<?> builder = XposedHelpers.findClassIfExists("com.oculus.horizon.service.result.ResultBuilder", loader);
        Class<?> caller = XposedHelpers.findClassIfExists("com.oculus.horizon.service.CallerUtils", loader);
        Class<?> bundled = XposedHelpers.findClassIfExists("com.oculus.horizon.service.result.EntitlementCheckResultBuilder", loader);
        Class<?> legacy = XposedHelpers.findClassIfExists("com.oculus.horizon.service.result.IsAppEntitledResultBuilder", loader);
        Class<?> checkout = XposedHelpers.findClassIfExists("com.oculus.horizon.service.result.IAPCheckoutFlowResultBuilder", loader);
        Class<?> wrapper = XposedHelpers.findClassIfExists("com.oculus.horizon.service.result.ResultBuilderNoBundle$1", loader);
        if (builder == null || caller == null || bundled == null || legacy == null || wrapper == null) return;
        XC_MethodHook.Unhook frameHook = null;
        try {
            Method run = builder.getDeclaredMethod("A00", Bundle.class);
            Method resolve = caller.getDeclaredMethod("getCallingPackage", Bundle.class);
            if (run.getReturnType() != Bundle.class || resolve.getReturnType() != String.class) return;
            deoptimize(run);
            frameHook = XposedBridge.hookMethod(run, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    boolean eligible = bundled.isInstance(param.thisObject);
                    if (!eligible && wrapper.isInstance(param.thisObject)) {
                        try {
                            Object wrapped = XposedHelpers.getObjectField(param.thisObject, "this$0");
                            boolean checkoutCall = checkout != null && checkout.isInstance(wrapped);
                            eligible = legacy.isInstance(wrapped) || checkoutCall;
                            if (checkoutCall) SigningHook.log("Checkout caller scope opened");
                        }
                        catch (Throwable incompatible) { eligible = false; }
                    }
                    // Every builder gets a frame: unrelated builders suppress aliases.
                    scope.enter(eligible);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) { scope.exit(); }
            });
            if (frameHook == null) return;
            XC_MethodHook.Unhook callerHook = XposedBridge.hookMethod(resolve, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    EntitlementScope.Frame frame = scope.current();
                    // Run CallerUtils first; preserve UID validation, exceptions and null results.
                    if (frame == null || param.hasThrowable() || !(param.getResult() instanceof String)) return;
                    String source = (String) param.getResult();
                    long identity = Binder.clearCallingIdentity();
                    try {
                        PackageSwaps.packageName(source);
                        Context context = SigningHook.currentContext();
                        if (context == null) return;
                        Bundle result = SigningHook.companionCall("packageSwap", source, null);
                        SigningHook.updateLogging(result);
                        if (result == null) return;
                        String target = result.getString("presentedPackage");
                        String presented = frame.resolve(source, target);
                        if (!source.equals(presented)) {
                            param.setResult(presented);
                            SigningHook.log("Platform package lookup: " + source + " -> " + presented);
                        }
                    } catch (Throwable unavailable) { SigningHook.updateLogging(null); }
                    finally { Binder.restoreCallingIdentity(identity); }
                }
            });
            if (callerHook == null) { frameHook.unhook(); return; }
            installed = true;
        } catch (Throwable incompatible) {
            if (frameHook != null) frameHook.unhook();
            SigningHook.refreshLogging();
            SigningHook.log("Entitlement swap adapter unavailable: " + incompatible.getClass().getSimpleName());
        }
    }

    static synchronized boolean callerHookReady() { return installed; }
    static void enterContentCall(boolean eligible) { scope.enter(eligible); }
    static void exitContentCall() { scope.exit(); }

    static AssetRoute assetRoute() {
        EntitlementScope.Frame frame = scope.current();
        return frame == null ? null : frame.assetRoute();
    }

    static String libraryPackage(String original, String presented) {
        return scope.libraryPackage(original, presented);
    }

    private static void deoptimize(Method method) {
        try { XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class).invoke(null, method); }
        catch (Throwable unsupported) { /* Optional framework extension for inlined caller resolution. */ }
    }
}
