package com.example.signinginfooverride;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Narrow adapter for the supplied OCMS startup job-reconstruction loop. */
final class OcmsAssetRecoveryHook {
    private static final String PREFIX = "com.oculus.ocms.installer2.";
    private static final ThreadLocal<Deque<Frame>> frames = new ThreadLocal<>();
    private static final class Frame {
        boolean rebuilding;
        final AssetRecoveryGroups groups = new AssetRecoveryGroups();
    }

    static void install(ClassLoader loader, List<XC_MethodHook.Unhook> hooks) throws Exception {
        Class<?> startup = XposedHelpers.findClass(PREFIX + "Installer$1", loader);
        Class<?> store = XposedHelpers.findClass(PREFIX + "InstallerRequestStore", loader);
        Class<?> dao = XposedHelpers.findClass(PREFIX + "dbschema.InstallerTaskRecordDao", loader);
        Class<?> record = XposedHelpers.findClass(PREFIX + "dbschema.InstallerTaskRecord", loader);
        Method run = startup.getDeclaredMethod("run");
        Method phase = store.getDeclaredMethod("A02");
        Method read = dao.getDeclaredMethod("A04", dao);
        Method lookup = XposedHelpers.findClass("X.000", loader).getDeclaredMethod("A0X", Map.class, Object.class);
        Method add = XposedHelpers.findClass("X.004", loader).getDeclaredMethod("A1V", Object.class, Object.class);
        for (Method method : new Method[]{run, phase, read, lookup, add}) AssetStorageBridge.deoptimize(method);
        hooks.add(XposedBridge.hookMethod(run, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                SigningHook.refreshLogging();
                Deque<Frame> stack = frames.get();
                if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
                stack.push(new Frame());
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Deque<Frame> stack = frames.get();
                if (stack == null) return;
                if (!stack.isEmpty()) stack.pop();
                if (stack.isEmpty()) frames.remove();
            }
        }));
        hooks.add(XposedBridge.hookMethod(phase, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Frame frame = current();
                // In this build A02 is called after sanitize_database_end and immediately before reconstruction.
                if (frame != null && !param.hasThrowable()) frame.rebuilding = true;
            }
        }));
        hooks.add(XposedBridge.hookMethod(read, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Frame frame = current();
                if (frame != null && frame.rebuilding && !param.hasThrowable() && param.getResult() instanceof Collection<?>)
                    frame.groups.batch((Collection<?>) param.getResult());
            }
        }));
        hooks.add(XposedBridge.hookMethod(lookup, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Frame frame = current();
                if (frame != null && frame.rebuilding && !param.hasThrowable() && directStartupCall())
                    frame.groups.lookup((Map<?, ?>) param.args[0], param.args[1], param.getResult());
            }
        }));
        hooks.add(XposedBridge.hookMethod(add, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Frame frame = current();
                if (frame == null || !frame.rebuilding || !record.isInstance(param.args[1])
                        || !frame.groups.accepts(param.args[0], param.args[1]) || !directStartupCall()) return;
                try {
                    Object item = param.args[1];
                    Object option = XposedHelpers.getObjectField(item, "install_option");
                    if (!(option instanceof Enum<?>) || !"InstallAsset".equals(((Enum<?>) option).name())) return;
                    String packageName = (String) XposedHelpers.getObjectField(item, "package_name");
                    String id = (String) XposedHelpers.getObjectField(item, "install_uuid");
                    if (OcmsAssetStorageHook.saved(id, packageName) == null) return;
                    param.args[0] = frame.groups.destination(param.args[0], item, packageName, id);
                    SigningHook.log("OCMS asset recovery group retained for routed request");
                } catch (Throwable unavailable) { SigningHook.log("OCMS asset recovery group unavailable"); }
            }
        }));
    }

    private static Frame current() {
        Deque<Frame> stack = frames.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    private static boolean directStartupCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith("com.oculus.ocms."))
                return frame.getClassName().equals(PREFIX + "Installer$1") && frame.getMethodName().equals("run");
        }
        return false;
    }
}
