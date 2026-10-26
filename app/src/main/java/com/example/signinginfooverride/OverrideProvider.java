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
            if (!"certificate".equals(method) || filename == null) return null;
            OverrideFiles.parse(filename);
            OverrideStore store = new OverrideStore(getContext());
            byte[] pem = store.read(filename);
            OverrideFiles.certificates(pem);
            Bundle result = new Bundle(); result.putByteArray("pem", pem); return result;
        } catch (Exception e) { return null; }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
}
