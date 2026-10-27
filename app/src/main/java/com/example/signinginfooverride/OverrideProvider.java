package com.example.signinginfooverride;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Read-only access to public certificates when a scoped client cannot read shared storage. */
public final class OverrideProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String filename, Bundle extras) {

        long identity = Binder.clearCallingIdentity();
        try {
            if ("settings".equals(method)) {
                Bundle settings = new Bundle();
                settings.putBoolean(ModuleSettings.VERBOSE_LOGGING, ModuleSettings.verboseLogging(getContext()));
                return settings;
            }
            if ("packageSwap".equals(method)) {
                String source = PackageSwaps.packageName(filename);
                Bundle result = new Bundle();
                result.putString("presentedPackage", PackageSwaps.presented(SwapStore.read(getContext()), source));
                result.putBoolean(ModuleSettings.VERBOSE_LOGGING, ModuleSettings.verboseLogging(getContext()));
                return result;
            }
            if ("swap".equals(method)) {
                String source = PackageSwaps.packageName(filename);
                Bundle result = new Bundle();
                String target = PackageSwaps.presented(SwapStore.read(getContext()), source);
                result.putBoolean(ModuleSettings.VERBOSE_LOGGING, ModuleSettings.verboseLogging(getContext()));
                if (!source.equals(target)) {
                    try {
                        if (extras == null || !extras.containsKey("requestVersion")) return null;
                        CertificateLookup.Result certificate = TokenIdentity.resolve(getContext(), source, target, extras.getLong("requestVersion"));
                        result.putString("signer", CertificateLookup.tokenSigner(certificate));
                        describe(result, certificate);
                        result.putString("presentedPackage", target);
                    } catch (Exception unavailable) {
                        result.putString("error", "Swap signer unavailable (" + unavailable.getClass().getSimpleName() + ")");
                    }
                } else result.putString("presentedPackage", source);
                return result;
            }
            if (!"certificate".equals(method) || filename == null) return null;
            OverrideFiles.Key key = OverrideFiles.parse(filename);
            String target = PackageSwaps.presented(SwapStore.read(getContext()), key.packageName);
            CertificateLookup.Result certificate = CertificateResolver.resolve(getContext(), key.packageName, key.version, target);
            if (certificate == null) return null;
            Bundle result = new Bundle(); result.putByteArray("pem", certificate.pem); describe(result, certificate); return result;
        } catch (Exception e) { return null; }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    private static void describe(Bundle result, CertificateLookup.Result certificate) {
        result.putString("certificatePackage", certificate.packageName);
        result.putLong("certificateVersion", certificate.version);
        result.putString("certificateOrigin", certificate.origin);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
}
