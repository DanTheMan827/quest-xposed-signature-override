package com.example.signinginfooverride;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import java.io.IOException;

/** Permission checks shared by the manager and its certificate reader. */
final class StorageAccess {
    static boolean isGranted(Context context) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static void require(Context context) throws IOException {
        if (!isGranted(context)) throw new IOException("Storage permission is required to read and save signature PEM files");
    }
}
