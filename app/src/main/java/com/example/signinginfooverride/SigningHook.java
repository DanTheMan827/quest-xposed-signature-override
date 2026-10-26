package com.example.signinginfooverride;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Parcel;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Changes only package-query results in explicitly scoped client processes. */
public final class SigningHook implements IXposedHookLoadPackage {
    private static boolean installed;
    private static volatile boolean verboseLogging;
    private static final ThreadLocal<Boolean> inQuery = new ThreadLocal<>();

    @Override public synchronized void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        // Never capture substituted results or modify the system package database.
        if (installed || BuildConfig.APPLICATION_ID.equals(p.packageName) || "android".equals(p.packageName)) return;
        Class<?> manager = XposedHelpers.findClassIfExists("android.app.ApplicationPackageManager", p.classLoader);
        if (manager == null) return;
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                // Provider resolution can itself query package metadata. Leave those nested calls alone.
                if (Boolean.TRUE.equals(inQuery.get())) return;
                inQuery.set(true);
                try {
                    refreshLogging();
                    if (param.hasThrowable()) {
                        log(param.method.getName() + " failed: " + param.getThrowable().getClass().getSimpleName());
                        return;
                    }
                    log("Query: " + param.method.getName());
                    Object original = param.getResult();
                    if (original instanceof PackageInfo) {
                        param.setResult(replace((PackageInfo) original));
                    } else if (original instanceof List<?>) {
                        List<Object> copy = new ArrayList<>((List<?>) original);
                        log("Package list size=" + copy.size());
                        for (int i = 0; i < copy.size(); i++) {
                            if (copy.get(i) instanceof PackageInfo) copy.set(i, replace((PackageInfo) copy.get(i)));
                        }
                        param.setResult(copy);
                    }
                } finally { inQuery.remove(); }
            }
        };
        for (String name : new String[]{"getPackageInfo", "getInstalledPackages", "getPackagesHoldingPermissions"}) {
            XposedBridge.hookAllMethods(manager, name, hook);
        }
        installed = true;
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo replace(PackageInfo original) {
        if (original.packageName == null || BuildConfig.APPLICATION_ID.equals(original.packageName)) return original;
        try {
            byte[] pem = certificate(original.packageName, original.getLongVersionCode());
            if (pem == null) {
                log("No readable override for " + original.packageName + "+" + original.getLongVersionCode());
                return original;
            }
            List<java.security.cert.X509Certificate> certificates = OverrideFiles.certificates(pem);
            Signature[] replacements = new Signature[certificates.size()];
            for (int i = 0; i < replacements.length; i++) replacements[i] = new Signature(certificates.get(i).getEncoded());
            PackageInfo copy;
            Parcel parcel = Parcel.obtain();
            try {
                original.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                copy = PackageInfo.CREATOR.createFromParcel(parcel);
            } finally { parcel.recycle(); }
            if (copy.signatures != null) copy.signatures = replacements;
            if (copy.signingInfo != null) copy.signingInfo = signingInfo(copy.signingInfo, replacements);
            log("Applied override for " + original.packageName + "+" + original.getLongVersionCode()
                    + "; current signers=" + replacements.length);
            return copy;
        } catch (Throwable error) {
            log("Lookup failed for " + original.packageName + ": " + error);
            return original;
        }
    }

    private static byte[] certificate(String packageName, long version) throws Exception {
        String filename = OverrideFiles.filename(packageName, version);
        File file = OverrideFiles.resolve(new File(BuildConfig.OVERRIDE_DIRECTORY), filename);
        try { if (file.isFile()) { log("Reading PEM directly: " + filename); return OverrideFiles.read(file); } }
        catch (java.io.IOException | SecurityException denied) { /* Try manager's read-only bridge. */ }
        Context context = currentContext();
        if (context == null) return null;
        log("Reading PEM through companion: " + filename);
        Bundle result = context.getContentResolver().call(
                Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".certificates"),
                "certificate", filename, null);
        return result == null ? null : result.getByteArray("pem");
    }

    private static Context currentContext() {
        return (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
    }

    private static void refreshLogging() {
        // Re-read for each query so the switch works in already-running scoped apps.
        // Missing settings, a missing companion or IPC failure always disables logging.
        try {
            Context context = currentContext();
            Bundle settings = context == null ? null : context.getContentResolver().call(
                    Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".certificates"), "settings", null, null);
            verboseLogging = settings != null && settings.getBoolean(ModuleSettings.VERBOSE_LOGGING, false);
        } catch (Throwable unavailable) { verboseLogging = false; }
    }

    private static void log(String message) {
        if (verboseLogging) XposedBridge.log("SigningInfoOverride: " + message);
    }

    private static SigningInfo signingInfo(SigningInfo original, Signature[] signatures) throws Exception {
        // Android 9-12: PackageParser.SigningDetails; Android 13+: SigningDetails.
        Field field = SigningInfo.class.getDeclaredField("mSigningDetails");
        field.setAccessible(true);
        Class<?> detailsClass = field.getType();
        Constructor<?> constructor = detailsClass.getDeclaredConstructor(Signature[].class, int.class);
        constructor.setAccessible(true);
        Object details = constructor.newInstance(signatures, 0 /* scheme unknown */);
        field.set(original, details);
        return original;
    }
}
