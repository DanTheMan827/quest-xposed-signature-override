package com.example.signinginfooverride;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Process;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Direct hook for the AppVerifierUtils implementation inspected in Horizon.zip. */
public final class SigningHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    static {
        Log.i("SigningInfoOverride", "SigningHook class initialized; pid=" + Process.myPid());
    }
    private static boolean installed;

    public SigningHook() {
        log("SigningHook constructor; pid=" + Process.myPid());
    }

    @Override public void initZygote(StartupParam param) {
        log("SigningHook initZygote callback; pid=" + Process.myPid());
    }

    private static void log(String message) {
        Log.i("SigningInfoOverride", message);
        XposedBridge.log("SigningInfoOverride: " + message);
    }

    @Override public synchronized void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        log("v2.2 load-package callback; package=" + p.packageName + "; process=" + p.processName +
                "; pid=" + Process.myPid());
        if (installed) {
            log("Hook already installed in this process");
            return;
        }
        try {
            Class<?> verifier = XposedHelpers.findClassIfExists(
                    "com.oculus.auth.coreutil.AppVerifierUtils", p.classLoader);
            if (verifier == null) {
                log("Verifier class unavailable in process loader: " + p.processName);
                return;
            }
            Method method = verifier.getDeclaredMethod("getSignatureFromPackageName", String.class);
            Class<?> resultType = method.getReturnType();
            // In the uploaded build this is X.0Xf(String). Resolve the actual type.
            Constructor<?> constructor = resultType.getDeclaredConstructor(String.class);
            if (resultType.getDeclaredField("sha256Hash").getType() != String.class ||
                    !Modifier.isPublic(constructor.getModifiers())) {
                throw new IllegalStateException("Unsupported signature-wrapper shape");
            }
            OverrideConfig config = new OverrideConfig(BuildConfig.CONFIG_PATH);
            AtomicBoolean reportedError = new AtomicBoolean();
            XC_MethodHook.Unhook unhook = XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof String)) return;
                    String packageName = (String) param.args[0];
                    log("getSignatureFromPackageName entered; queriedPackage=" + packageName +
                            "; pid=" + Process.myPid());
                    try {
                        // Re-read each query; config updates need no module rebuild.
                        OverrideConfig.Document document = config.read();
                        if (!document.hasPackage(packageName)) {
                            log("No configured package match for " + packageName + "; using original method");
                            return;
                        }
                        Object value = XposedHelpers.getObjectField(param.thisObject, "context");
                        if (!(value instanceof Context)) throw new IllegalStateException("Missing verifier context");
                        Context context = (Context) value;
                        PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
                        long version = info.getLongVersionCode();
                        String hash = document.hashFor(packageName, version);
                        if (hash == null) {
                            log("No version match for " + packageName +
                                    "; installed versionCode=" + version);
                            reportedError.set(false);
                            return;
                        }
                        Object result = constructor.newInstance(hash);
                        if (!hash.equals(XposedHelpers.getObjectField(result, "sha256Hash"))) {
                            throw new IllegalStateException("Signature wrapper did not retain configured hash");
                        }
                        param.setResult(result);
                        reportedError.set(false);
                        log("Returned configured signature for " + packageName +
                                "; versionCode=" + version + "; app_signer=" + hash);
                    } catch (Throwable error) {
                        // Leave the original result/exception behavior intact on error.
                        if (reportedError.compareAndSet(false, true)) {
                            log("Configuration/lookup failed; using original method; " +
                                    error.getClass().getSimpleName() + ": " + error.getMessage());
                        }
                    }
                }
            });
            if (unhook == null) {
                throw new IllegalStateException("Framework hookMethod returned null; hook was not installed");
            }
            installed = true;
            log("v2.2 direct verifier hook installed in " + p.processName +
                    "; config=" + BuildConfig.CONFIG_PATH + "; resultType=" + resultType.getName());
            installTokenDiagnostics(p.classLoader);
        } catch (Throwable error) {
            Log.e("SigningInfoOverride", "Hook installation failed; overrides inactive", error);
            log("Hook installation failed; overrides inactive");
            XposedBridge.log(error);
        }
    }

    private static void installTokenDiagnostics(ClassLoader loader) {
        try {
            Class<?> provider = XposedHelpers.findClassIfExists(
                    "com.oculus.auth.components.TokenProvider", loader);
            if (provider == null) {
                log("TokenProvider unavailable in this loader; caller diagnostics inactive");
                return;
            }
            // Vector/LSPosed extension: use reflection to retain compileOnly API 82.
            Method deoptimize = null;
            try {
                deoptimize = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            } catch (NoSuchMethodException unavailable) {
                log("Framework deoptimizeMethod(Member) unavailable; inlining remains possible");
            }
            int callers = 0;
            int traces = 0;
            for (Method caller : provider.getDeclaredMethods()) {
                String name = caller.getName();
                if (!"obtainAccessToken".equals(name) && !"obtainToken".equals(name)) continue;
                if (Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers())) continue;
                if (deoptimize != null) {
                    try {
                        // Deoptimize before adding the diagnostic hook, so its backup
                        // also uses the deoptimized body containing the signature call.
                        deoptimize.invoke(null, caller);
                        callers++;
                        log("Deoptimization requested for TokenProvider." + name);
                    } catch (Throwable error) {
                        log("Caller deoptimization failed for " + name + "; exception=" +
                                error.getClass().getSimpleName());
                        XposedBridge.log(error);
                    }
                }
                Class<?>[] args = caller.getParameterTypes();
                if (!"obtainAccessToken".equals(name) || args.length != 6 ||
                        args[0] != boolean.class || args[1] != String.class ||
                        args[4] != String.class || args[5] != boolean.class) continue;
                XC_MethodHook.Unhook trace = XposedBridge.hookMethod(caller, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        // These are package arguments in the supplied Horizon build.
                        // Do not log credentials, token objects, or all arguments.
                        log("TokenProvider.obtainAccessToken entered; callingPackage=" + param.args[1] +
                                "; metadataPackage=" + param.args[4] + "; pid=" + Process.myPid());
                    }

                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            log("TokenProvider.obtainAccessToken exited with exception=" +
                                    param.getThrowable().getClass().getName());
                        } else {
                            log("TokenProvider.obtainAccessToken exited; resultPresent=" +
                                    (param.getResult() != null));
                        }
                    }
                });
                if (trace == null) {
                    log("Framework rejected TokenProvider diagnostic hook");
                } else {
                    traces++;
                    log("Read-only TokenProvider.obtainAccessToken trace installed");
                }
            }
            log("TokenProvider diagnostics ready; deoptimizationRequests=" + callers + "; traces=" + traces);
        } catch (Throwable error) {
            log("Optional caller diagnostics failed; signature hook remains installed");
            XposedBridge.log(error);
        }
    }
}
