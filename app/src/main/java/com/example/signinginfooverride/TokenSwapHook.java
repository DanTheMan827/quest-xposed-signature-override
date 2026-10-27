package com.example.signinginfooverride;

import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Adapter for the token-request call shown in the supplied TokenProvider listing. */
final class TokenSwapHook {
    private static boolean installed;
    private static final Set<Method> methods = new HashSet<>();
    private static final ThreadLocal<Integer> depth = new ThreadLocal<>();
    private static final Class<?>[] PARAMETERS = {String.class, String.class, String.class,
            String.class, String.class, String.class, int.class, boolean.class};

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        Class<?> auth = XposedHelpers.findClassIfExists("com.oculus.auth.api.AuthMethods", loader);
        Class<?> provider = XposedHelpers.findClassIfExists("com.oculus.auth.components.TokenProvider", loader);
        if (auth == null && provider == null) return;
        try {
            if (auth != null) hookAuthentication(auth.getMethod("authenticateApplication", PARAMETERS));
        } catch (Throwable incompatible) { report(incompatible); }
        if (provider != null) {
            for (Method caller : provider.getDeclaredMethods()) {
                Class<?>[] args = caller.getParameterTypes();
                if (!"obtainAccessToken".equals(caller.getName()) || args.length != 6
                        || args[0] != boolean.class || args[1] != String.class || args[2] != String.class
                        || args[4] != String.class || args[5] != boolean.class) continue;
                try {
                    deoptimizeCaller(caller);
                    // Resolve the injected API's concrete method as well as its declared base API.
                    // A firmware may use an abstract API or override authenticateApplication.
                    XC_MethodHook.Unhook observer = XposedBridge.hookMethod(caller, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object factory = XposedHelpers.getObjectField(param.thisObject, "mAuthMethods");
                                Object implementation = XposedHelpers.callMethod(factory, "get");
                                if (implementation != null) hookAuthentication(implementation.getClass()
                                        .getMethod("authenticateApplication", PARAMETERS));
                            } catch (Throwable incompatible) { report(incompatible); }
                        }
                    });
                    if (observer != null) installed = true;
                } catch (Throwable incompatible) { report(incompatible); }
            }
        }
        installed |= !methods.isEmpty();
    }

    private static synchronized void hookAuthentication(Method method) {
        if (methods.contains(method) || Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) return;
        XC_MethodHook.Unhook hook = XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Integer previous = depth.get();
                int nesting = previous == null ? 0 : previous;
                depth.set(nesting + 1);
                // An overriding implementation can delegate to a hooked base method. Apply once.
                if (nesting > 0 || param.args.length != 8 || !(param.args[2] instanceof String)) return;
                String source = (String) param.args[2];
                try {
                    PackageSwaps.packageName(source);
                    Context context = SigningHook.currentContext();
                    if (context == null) return;
                    Bundle extras = new Bundle();
                    extras.putLong("requestVersion", TokenRequestSwap.version(param.args));
                    Bundle result = SigningHook.companionCall("swap", source, extras);
                    SigningHook.updateLogging(result);
                    if (result == null) return;
                    if (result.containsKey("error")) {
                        SigningHook.log("Bundle swap skipped for " + source + ": " + result.getString("error"));
                        return;
                    }
                    if (TokenRequestSwap.apply(param.args, source, result.getString("presentedPackage"), result.getString("signer"))) {
                        SigningHook.log("Token request bundle ID and signer: " + source + " -> " + param.args[2]);
                        SigningHook.log("Token certificate from " + result.getString("certificatePackage") + "+"
                                + result.getLong("certificateVersion") + " (" + result.getString("certificateOrigin") + ")");
                    }
                } catch (Throwable unavailable) {
                    SigningHook.updateLogging(null);
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                Integer nesting = depth.get();
                if (nesting == null || nesting <= 1) depth.remove();
                else depth.set(nesting - 1);
            }
        });
        if (hook != null) methods.add(method);
    }

    private static void deoptimizeCaller(Method caller) {
        try {
            Method deoptimize = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            deoptimize.invoke(null, caller);
        } catch (Throwable unsupported) { /* Optional framework extension; API 82 remains supported. */ }
    }

    private static void report(Throwable error) {
        SigningHook.refreshLogging();
        SigningHook.log("Token swap adapter unavailable: " + error.getClass().getSimpleName());
    }
}
