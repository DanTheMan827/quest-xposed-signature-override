package com.example.signinginfooverride;

import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import android.os.Bundle;
import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Intercepts service requests emitted by the platform driver; no loader/binary patching. */
final class ContentSwapHook {
    private static boolean installed;
    private static final Set<Method> hookedMethods = new HashSet<>();

    static synchronized void install(ClassLoader loader) {
        if (installed || !EntitlementSwapHook.callerHookReady()) return;
        Class<?> service = XposedHelpers.findClassIfExists("com.oculus.horizon.service.OVRService$4", loader);
        if (service == null) return;
        try {
            Method deoptimize = null;
            try { deoptimize = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class); }
            catch (NoSuchMethodException unsupported) { /* Optional framework extension. */ }
            int hooked = 0;
            for (Method method : service.getDeclaredMethods()) {
                int flags = method.getModifiers();
                if (!Modifier.isPublic(flags) || Modifier.isStatic(flags) || Modifier.isAbstract(flags)
                        || Modifier.isNative(flags) || method.isSynthetic()) continue;
                if (hookedMethods.contains(method)) continue;
                String name = method.getName(); boolean translate = ContentCallPolicy.translate(name);
                if (translate && deoptimize != null) {
                    try { deoptimize.invoke(null, method); } catch (Throwable unsupported) { /* Optional. */ }
                }
                XC_MethodHook.Unhook hook = XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        // Unsupported nested requests get a false frame so identities cannot leak into permission checks.
                        EntitlementSwapHook.enterContentCall(translate);
                        SigningHook.refreshLogging();
                        SigningHook.log("Platform service request: " + name + "; content mapping=" + translate);
                        if (translate) AssetStorageBridge.diagnoseContentCall(name);
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            String outcome = param.hasThrowable() ? "; exception=" + param.getThrowable().getClass().getSimpleName() : "";
                            if (translate && !param.hasThrowable() && param.getResult() instanceof Bundle) {
                                try {
                                    Object errorCode = ((Bundle) param.getResult()).get("error_code");
                                    if (errorCode instanceof Number) outcome += "; error_code=" + errorCode;
                                } catch (RuntimeException unavailable) { /* Never change a response to inspect diagnostics. */ }
                            }
                            if ("getIAPCheckoutFlowIntent".equals(name) && !param.hasThrowable())
                                outcome += "; checkoutIntent=" + (param.getResult() instanceof Intent ? "present" : "absent");
                            SigningHook.log("Platform service completed: " + name + outcome);
                        } finally { EntitlementSwapHook.exitContentCall(); }
                    }
                });
                if (hook != null) { hooked++; hookedMethods.add(method); }
            }
            installed = hooked != 0 || !hookedMethods.isEmpty();
        } catch (Throwable incompatible) {
            installed = !hookedMethods.isEmpty();
            SigningHook.refreshLogging();
            SigningHook.log("Platform content adapter unavailable: " + incompatible.getClass().getSimpleName());
        }
    }
}
