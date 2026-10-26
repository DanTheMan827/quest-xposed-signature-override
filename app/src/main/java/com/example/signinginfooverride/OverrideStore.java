package com.example.signinginfooverride;

import android.content.Context;
import android.system.Os;
import java.io.*;
import java.util.*;

/** Shared-storage reads and atomic saves using the granted Android permissions. */
final class OverrideStore {
    private final Context context;
    private final File directory = new File(BuildConfig.OVERRIDE_DIRECTORY);

    OverrideStore(Context context) { this.context = context.getApplicationContext(); }

    List<String> names() throws Exception {
        StorageAccess.require(context);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot open override folder");
        String[] files = directory.list();
        if (files == null) throw new IOException("Cannot list override folder");
        if (files.length > 1024) throw new IOException("Folder has more than 1024 entries");
        List<String> result = new ArrayList<>();
        for (String file : files) if (file.endsWith(".pem")) result.add(file);
        Collections.sort(result);
        return result;
    }

    byte[] read(String name) throws Exception {
        StorageAccess.require(context);
        return OverrideFiles.read(OverrideFiles.resolve(directory, name));
    }

    synchronized void save(String name, byte[] pem, boolean replace) throws Exception {
        StorageAccess.require(context);
        OverrideFiles.certificates(pem);
        File target = OverrideFiles.resolve(directory, name);
        if (!replace && names().contains(name)) throw new IOException("Override already exists; refresh and confirm replacement");
        File temporary = null;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create override folder");
            temporary = File.createTempFile(".capture-", ".tmp", directory);
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(pem); out.getFD().sync(); }
            Os.rename(temporary.getPath(), target.getPath());
        } catch (Exception error) {
            throw new IOException("Cannot save to override folder. Check available space and storage permission.", error);
        } finally { if (temporary != null) temporary.delete(); }
    }
}
