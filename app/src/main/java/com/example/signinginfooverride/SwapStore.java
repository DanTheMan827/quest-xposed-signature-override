package com.example.signinginfooverride;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/** Private app preferences: the exported provider offers read access only. */
final class SwapStore {
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("PackageSwaps", Context.MODE_PRIVATE);
    }

    static synchronized Map<String, String> read(Context context) {
        Map<String, String> swaps = new TreeMap<>();
        for (Map.Entry<String, ?> entry : preferences(context).getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) throw new IllegalArgumentException("Invalid stored swap");
            swaps.put(entry.getKey(), (String) entry.getValue());
        }
        return PackageSwaps.validate(swaps);
    }

    static synchronized void save(Context context, String previousSource, String source, String target) throws IOException {
        PackageSwaps.pair(source, target);
        Map<String, String> swaps = new TreeMap<>(read(context));
        if (previousSource != null) { PackageSwaps.packageName(previousSource); swaps.remove(previousSource); }
        swaps.put(source, target);
        PackageSwaps.validate(swaps);
        SharedPreferences.Editor edit = preferences(context).edit();
        if (previousSource != null && !previousSource.equals(source)) edit.remove(previousSource);
        if (!edit.putString(source, target).commit()) throw new IOException("Could not save the swap");
    }

    static synchronized void remove(Context context, String source) throws IOException {
        PackageSwaps.packageName(source);
        if (!preferences(context).edit().remove(source).commit()) throw new IOException("Could not remove the swap");
    }
}
